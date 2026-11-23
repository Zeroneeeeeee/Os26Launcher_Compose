package gambi.global.os26launcher.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.media.ExifInterface
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.core.bitmap.BitmapScaler
import gambi.global.os26launcher.core.bitmap.ExifReader
import gambi.global.os26launcher.domain.model.photo.PhotoImportResult
import gambi.global.os26launcher.domain.photo.PhotoSource
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * G3b (D1/D2) — file ảnh của widget Ảnh trong `noBackupFilesDir/photo_widgets/<itemId>/`:
 *  - `<photoId>.jpg`: ảnh đã thuộc widget; `staging/<photoId>.jpg`: ảnh vừa nhập, chờ cam kết vùng cắt.
 *  - Bản sao thu nhỏ: cạnh dài tối đa [MAX_SIDE_PX], đã xoay đúng chiều, nén JPEG lại nên KHÔNG mang EXIF (kể cả vị trí GPS). Khác ảnh nền (D9):
 *    không chép nguyên byte.
 *  - Tên file là mã hex ngẫu nhiên, không lấy tên gốc. Không bao giờ log đường dẫn, URI, kích thước.
 * Mọi hàm chặn I/O: gọi từ luồng nền. Người gọi tuần tự hóa (Mutex ở repository).
 */
@Singleton
class PhotoFileStore @Inject constructor(@ApplicationContext context: Context) {
    private val root = File(context.noBackupFilesDir, DIR_NAME)

    private fun widgetDir(itemId: Long): File = File(root, itemId.toString())

    /** File ảnh nếu id hợp lệ và file tồn tại. */
    fun photoFile(itemId: Long, photoId: String, staged: Boolean): File? {
        if (itemId <= 0L || !isValidId(photoId)) return null
        val dir = if (staged) File(widgetDir(itemId), STAGING) else widgetDir(itemId)
        return File(dir, "$photoId.jpg").takeIf { it.isFile }
    }

    /**
     * Nhập ảnh từ [source] vào nháp của [itemId]. Đọc EXIF, đọc cỡ, giải mã có chặn pixel (`BitmapScaler.MAX_DECODE_PIXELS`), thu về <= [MAX_SIDE_PX],
     * xoay, nền trắng cho ảnh có alpha (JPEG không có alpha), nén, ghi `.tmp` rồi đổi tên.
     */
    fun importToStaging(itemId: Long, source: PhotoSource): PhotoImportResult {
        if (itemId <= 0L) return PhotoImportResult.Failed
        val photoId = newId()
        val staging = File(widgetDir(itemId), STAGING)
        val tmp = File(staging, "$photoId.tmp")
        return try {
            if (!staging.isDirectory && !staging.mkdirs()) return PhotoImportResult.Failed
            val orientation = try {
                source.open()?.use { ExifReader.readOrientation(it) } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // Với inJustDecodeBounds, decodeStream LUÔN trả null (chỉ điền bounds): KHÔNG dùng kết quả làm tín hiệu lỗi (lỗi G3b Task 3 đo trên Live 4:
            // mọi ảnh hợp lệ bị loại ở đây). Chỉ kiểm có mở được luồng và bounds hợp lệ.
            val opened = source.open()?.use { BitmapFactory.decodeStream(it, null, bounds) ; true } ?: false
            if (!opened) return PhotoImportResult.Failed
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return PhotoImportResult.Failed

            val swap = ExifReader.swapsAxes(orientation)
            val orientedW = if (swap) bounds.outHeight else bounds.outWidth
            val orientedH = if (swap) bounds.outWidth else bounds.outHeight
            val scale = min(1.0, MAX_SIDE_PX.toDouble() / max(orientedW, orientedH))
            val targetW = (orientedW * scale).roundToInt().coerceAtLeast(1)
            val targetH = (orientedH * scale).roundToInt().coerceAtLeast(1)
            // Thu TRƯỚC (chưa xoay nên đổi chỗ chiều), xoay SAU trên bản nhỏ (cùng thứ tự `PickedImageSource.import`).
            val decodeW = if (swap) targetH else targetW
            val decodeH = if (swap) targetW else targetH
            val sample = BitmapScaler.sampleSizeFor(
                bounds.outWidth, bounds.outHeight, decodeW, decodeH, BitmapScaler.MAX_DECODE_PIXELS
            )
            val raw = source.open()?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return PhotoImportResult.Failed
            var bmp = BitmapScaler.scaleTo(raw, decodeW, decodeH)
            bmp = ExifReader.applyOrientation(bmp, orientation)
            if (bmp.hasAlpha()) bmp = flattenOnWhite(bmp)
            val width = bmp.width
            val height = bmp.height
            try {
                FileOutputStream(tmp).use { out ->
                    if (!bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw IOException("compress")
                }
            } finally {
                bmp.recycle()
            }
            val dest = File(staging, "$photoId.jpg")
            if (!tmp.renameTo(dest)) throw IOException("rename")
            PhotoImportResult.Staged(photoId, width, height)
        } catch (e: OutOfMemoryError) {
            tmp.delete()
            Log.w(TAG, "import failed: OutOfMemoryError")
            PhotoImportResult.Failed
        } catch (e: Exception) {
            tmp.delete()
            Log.w(TAG, "import failed: ${e.javaClass.simpleName}")
            PhotoImportResult.Failed
        }
    }


    /** Chuyển ảnh nháp vào thư mục widget. false nếu không có file nháp hoặc đổi tên lỗi. */
    fun commitStaged(itemId: Long, photoId: String): Boolean {
        val src = photoFile(itemId, photoId, staged = true) ?: return false
        return src.renameTo(File(widgetDir(itemId), "$photoId.jpg"))
    }

    fun deletePhoto(itemId: Long, photoId: String, staged: Boolean) {
        photoFile(itemId, photoId, staged)?.delete()
    }

    fun clearStaging(itemId: Long) {
        if (itemId <= 0L) return
        File(widgetDir(itemId), STAGING).deleteRecursively()
    }

    fun deleteWidget(itemId: Long) {
        if (itemId <= 0L) return
        widgetDir(itemId).deleteRecursively()
    }

    /** `itemId` của mọi thư mục widget hiện có (tên không phải số dương bị bỏ qua, không đụng tới). */
    fun listWidgetIds(): List<Long> =
        root.listFiles()?.mapNotNull { f ->
            f.takeIf { it.isDirectory }?.name?.toLongOrNull()?.takeIf { it > 0L }
        }.orEmpty()

    private fun flattenOnWhite(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, 0f, 0f, null)
        src.recycle()
        return out
    }

    companion object {
        /** CHỈNH TAY (plan D2): cạnh dài tối đa của bản sao nguồn: >= 1,9 lần cỡ widget 4x2 trên màn 1080 px, đủ nét khi phóng trong màn cắt. */
        const val MAX_SIDE_PX = 2048

        /** CHỈNH TAY: chất lượng JPEG của bản sao nguồn. */
        const val JPEG_QUALITY = 90

        private const val DIR_NAME = "photo_widgets"
        private const val STAGING = "staging"
        private const val TAG = "PhotoFileStore"
        private val ID_REGEX = Regex("[0-9a-f]{12}")

        /** Mã ảnh hợp lệ: 12 ký tự hex (chặn ký tự đường dẫn). */
        fun isValidId(id: String): Boolean = ID_REGEX.matches(id)

        private fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)
    }
}
