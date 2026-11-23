package gambi.global.os26launcher.data.backdrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import gambi.global.os26launcher.core.bitmap.BitmapScaler
import gambi.global.os26launcher.core.bitmap.ExifReader
import gambi.global.os26launcher.core.bitmap.TransparentTrimmer
import java.io.File
import java.io.IOException

/**
 * Phương án A: ảnh nền do người dùng chọn bằng Photo Picker.
 *
 * Uri mà Photo Picker trả về chỉ có hiệu lực tạm thời, nên [import] lưu một bản sao vào `filesDir`:
 *  - ảnh JPEG có EXIF bình thường (không xoay) thì CHÉP NGUYÊN BYTE (D9, ảnh toàn cảnh nhờ vậy vẫn nét), giới hạn [MAX_COPY_BYTES];
 *  - còn lại giải mã (bị chặn bởi [BitmapScaler.MAX_DECODE_PIXELS]), xoay đúng chiều theo EXIF, thu về [BitmapScaler.MAX_SAVED_PIXELS],
 *    cắt bỏ viền trong suốt (nếu có) rồi nén lại.
 * File lưu luôn là toàn ảnh (không cắt theo màn). Lần mở app sau, [load] chỉ giải mã VÙNG GIỮA có tỉ lệ bằng màn bằng
 * [BitmapRegionDecoder] (không bao giờ cấp phát cả ảnh gốc), nên bitmap của Home nhỏ và cùng tỉ lệ với `ImageView`.
 *
 * Bản sao do [import] ghi không có EXIF xoay (đã xoay sẵn, hoặc EXIF bình thường), nên [load] không cần xử lý EXIF.
 */
class PickedImageSource(context: Context) : BackdropSource {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)

    override val name = "ảnh đã chọn"

    /**
     * Mô tả EXIF của lần nhập gần nhất (ví dụ "EXIF: xoay 90°"), rỗng nếu phiên này chưa nhập ảnh nào.
     * Dùng để kiểm tra code EXIF có thực sự chạy hay không.
     */
    var lastImportInfo: String = ""
        private set

    /** Đã có bản sao ảnh được lưu từ lần chọn trước chưa. */
    fun hasSaved(): Boolean = file.exists()

    /** `lastModified` của bản sao ảnh, 0 nếu chưa có. */
    fun version(): Long = if (file.exists()) file.lastModified() else 0L

    /**
     * Vùng giữa của ảnh đã lưu có tỉ lệ [reqW]:[reqH], giải mã bằng [BitmapRegionDecoder] + `inSampleSize` rồi thu về không quá
     * [reqW] x [reqH] (không phóng to). Ảnh đã lưu quá cỡ (bản cũ) cũng được chữa ở đây vì không cấp phát cả ảnh.
     */
    override fun load(reqW: Int, reqH: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null

        val region = BitmapScaler.cropRegion(w, h, reqW, reqH)
        val out = BitmapScaler.outputSize(region.width(), region.height(), reqW, reqH)
        val sample = BitmapScaler.sampleSizeFor(
            region.width(), region.height(), out.width, out.height, BitmapScaler.MAX_DECODE_PIXELS
        )
        val raw = decodeRegion(region, sample) ?: decodeWholeThenCrop(w, h, region, out.width, out.height)
        ?: return null
        return BitmapScaler.scaleTo(raw, out.width, out.height)
    }

    @Suppress("DEPRECATION") // newInstance(String) chỉ có từ API 31, minSdk 26
    private fun decodeRegion(region: Rect, sample: Int): Bitmap? = try {
        val decoder = BitmapRegionDecoder.newInstance(file.path, false)
        if (decoder == null) {
            null
        } else {
            try {
                decoder.decodeRegion(region, BitmapFactory.Options().apply { inSampleSize = sample })
            } finally {
                decoder.recycle()
            }
        }
    } catch (e: IOException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /** Đường lui khi [BitmapRegionDecoder] không dùng được: giải mã cả ảnh (đã chặn pixel) rồi cắt vùng giữa trong bộ nhớ. */
    private fun decodeWholeThenCrop(w: Int, h: Int, region: Rect, outW: Int, outH: Int): Bitmap? {
        val quality = BitmapScaler.sampleSizeFor(region.width(), region.height(), outW, outH, Long.MAX_VALUE)
        val sample = BitmapScaler.boundSample(w, h, quality, BitmapScaler.MAX_DECODE_PIXELS)
        val whole = BitmapFactory.decodeFile(
            file.path, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        val left = (region.left / sample).coerceIn(0, whole.width - 1)
        val top = (region.top / sample).coerceIn(0, whole.height - 1)
        val cropW = (region.width() / sample).coerceIn(1, whole.width - left)
        val cropH = (region.height() / sample).coerceIn(1, whole.height - top)
        val cropped = Bitmap.createBitmap(whole, left, top, cropW, cropH)
        if (cropped !== whole) whole.recycle()
        return cropped
    }

    /**
     * Nhập ảnh từ [uri] và lưu bản sao. Trả về một bitmap (ảnh đã xử lý, hoặc bản xem trước nhỏ khi chép nguyên byte JPEG; người
     * gọi chỉ kiểm null), hoặc null nếu lỗi. Chạy ở luồng nền. [reqW]/[reqH] không còn dùng: file lưu không cắt theo màn.
     */
    @Suppress("UNUSED_PARAMETER")
    fun import(uri: Uri, reqW: Int, reqH: Int): Bitmap? {
        val resolver = appContext.contentResolver
        val orientation = try {
            resolver.openInputStream(uri)?.use { ExifReader.readOrientation(it) }
                ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        lastImportInfo = "EXIF: ${describe(orientation)}"

        // D9: JPEG không cần xoay (và JPEG không có alpha nên không có viền trong suốt để cắt) thì chép nguyên byte
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            copyOriginalJpeg(uri)?.let {
                lastImportInfo += " · chép nguyên JPEG"
                return it
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Ảnh xoay 90/270 độ thì chiều rộng và cao đổi chỗ khi xoay xong
        val swap = ExifReader.swapsAxes(orientation)
        val w = if (swap) bounds.outHeight else bounds.outWidth
        val h = if (swap) bounds.outWidth else bounds.outHeight
        val target = BitmapScaler.savedSize(w, h)
        val sample = BitmapScaler.sampleSizeFor(w, h, target.width, target.height, BitmapScaler.MAX_DECODE_PIXELS)
        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        // Thu TRƯỚC (chưa xoay nên đổi chỗ chiều), xoay SAU trên bản nhỏ
        val rawW = if (swap) target.height else target.width
        val rawH = if (swap) target.width else target.height
        val scaled =
            if (raw.width > rawW || raw.height > rawH) BitmapScaler.scaleTo(raw, rawW, rawH) else raw
        val decoded = ExifReader.applyOrientation(scaled, orientation)
        val bmp = TransparentTrimmer.trim(decoded)
        if (bmp !== decoded) {
            lastImportInfo += " · cắt viền trong suốt ${decoded.width}x${decoded.height} → ${bmp.width}x${bmp.height}"
        }
        save(bmp)
        return bmp
    }

    /**
     * D9: chép nguyên byte nếu [uri] là JPEG (đầu file FF D8), không quá [MAX_COPY_BYTES], và giải mã được kích thước. Trả bản xem
     * trước nhỏ, hoặc null (không đụng file cũ) để người gọi đi đường giải mã.
     */
    private fun copyOriginalJpeg(uri: Uri): Bitmap? {
        val tmp = File(appContext.filesDir, "$FILE_NAME.tmp")
        var renamed = false
        try {
            val copied = appContext.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(COPY_BUFFER)
                var n = input.read(buf)
                if (n < 2 || buf[0] != 0xFF.toByte() || buf[1] != 0xD8.toByte()) return null
                var total = 0L
                tmp.outputStream().use { out ->
                    while (n > 0) {
                        total += n
                        if (total > MAX_COPY_BYTES) return null
                        out.write(buf, 0, n)
                        n = input.read(buf)
                    }
                }
                true
            } ?: false
            if (!copied) return null
            val preview = decodePreview(tmp) ?: return null
            if (!tmp.renameTo(file)) throw IOException("không đổi tên được file ảnh nền")
            renamed = true
            return preview
        } finally {
            if (!renamed) tmp.delete()
        }
    }

    /** Bản xem trước nhỏ (cạnh ngắn ≥ ~128 px) của [src]; null nếu không giải mã được. */
    private fun decodePreview(src: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PREVIEW_MIN_SIDE) sample *= 2
        return BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun describe(orientation: Int): String = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> "xoay 90°"
        ExifInterface.ORIENTATION_ROTATE_180 -> "xoay 180°"
        ExifInterface.ORIENTATION_ROTATE_270 -> "xoay 270°"
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "lật ngang"
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> "lật dọc"
        ExifInterface.ORIENTATION_TRANSPOSE -> "transpose"
        ExifInterface.ORIENTATION_TRANSVERSE -> "transverse"
        else -> "không xoay"
    }

    /**
     * Lưu bản sao vào filesDir. Ảnh có kênh alpha thì lưu PNG để giữ vùng trong suốt (cần để điều tra lỗi ảnh
     * chỉ phủ một dải giữa màn hình), ảnh không alpha thì lưu JPEG cho nhẹ. Ghi ra file tạm rồi đổi tên,
     * tránh để lại file hỏng nếu app bị tắt giữa chừng.
     */
    private fun save(bmp: Bitmap) {
        val tmp = File(appContext.filesDir, "$FILE_NAME.tmp")
        var renamed = false
        try {
            // FX1 (C4): kiểm kết quả nén và đổi tên; lỗi thì ném IOException (importBackdrop trả null, Settings báo lỗi), file cũ giữ nguyên
            val compressed = tmp.outputStream().use { out ->
                if (bmp.hasAlpha()) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
            }
            if (!compressed) throw IOException("không nén được ảnh nền")
            if (!tmp.renameTo(file)) throw IOException("không đổi tên được file ảnh nền")
            renamed = true
        } finally {
            if (!renamed) tmp.delete()
        }
    }

    private companion object {
        // Không đặt đuôi file vì định dạng (PNG hoặc JPEG) tùy ảnh; BitmapFactory nhận dạng theo nội dung
        const val FILE_NAME = "backdrop_image"

        // D9: JPEG lớn hơn mức này thì không chép nguyên byte mà đi đường giải mã có trần
        const val MAX_COPY_BYTES = 30L * 1024 * 1024
        const val COPY_BUFFER = 64 * 1024

        // Cạnh ngắn tối thiểu của bản xem trước trả về khi chép nguyên byte
        const val PREVIEW_MIN_SIDE = 128
    }
}
