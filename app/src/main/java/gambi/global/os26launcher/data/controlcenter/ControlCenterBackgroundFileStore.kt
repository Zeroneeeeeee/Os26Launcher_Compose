package gambi.global.os26launcher.data.controlcenter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.domain.controlcenter.ControlCenterBackgroundStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * G3d: ảnh nền riêng của Trung tâm điều khiển. Chép một bản thu nhỏ vào `filesDir` (như ảnh nền của launcher, phương án A),
 * vì quyền đọc URI của bộ chọn ảnh không bền qua khởi động lại.
 */
@Singleton
class ControlCenterBackgroundFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : ControlCenterBackgroundStore {

    private val file get() = File(context.filesDir, FILE_NAME)

    override suspend fun importImage(uri: String): Boolean = withContext(io) {
        try {
            val u = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE, MAX_SIDE)
            }
            val bmp = context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext false
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            bmp.recycle()
            tmp.renameTo(file)
        } catch (e: IOException) {
            false
        } catch (e: RuntimeException) {
            false // SecurityException khi URI hết quyền đọc
        }
    }

    override suspend fun load(reqW: Int, reqH: Int): Bitmap? = withContext(io) {
        val f = file
        if (!f.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH) }
        BitmapFactory.decodeFile(f.path, opts)
    }

    override fun version(): Long = file.takeIf { it.exists() }?.lastModified() ?: 0L

    private fun sampleSize(w: Int, h: Int, reqW: Int, reqH: Int): Int {
        var s = 1
        while (w / (s * 2) >= max(1, reqW) && h / (s * 2) >= max(1, reqH)) s *= 2
        return s
    }

    private companion object {
        const val FILE_NAME = "control_center_background.jpg"
        const val MAX_SIDE = 1440
        const val JPEG_QUALITY = 90
    }
}
