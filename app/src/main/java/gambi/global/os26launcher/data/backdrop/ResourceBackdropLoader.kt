package gambi.global.os26launcher.data.backdrop

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import gambi.global.os26launcher.core.bitmap.BitmapScaler

/**
 * WI — giải mã ảnh nền nằm trong tài nguyên app (ảnh mặc định WP, ảnh cài sẵn WI). Tách từ `DefaultBackdropSource.load` (FX1), không đổi
 * cách làm: vùng giữa có tỉ lệ bằng màn ([BitmapScaler.cropRegion], đúng phần `centerCrop` hiển thị) rồi thu về không quá [reqW] x [reqH].
 * Cắt trong bộ nhớ vì ảnh chỉ ~1,3 MP. Người gọi bắt `OutOfMemoryError`.
 */
internal object ResourceBackdropLoader {

    fun load(res: Resources, @DrawableRes id: Int, reqW: Int, reqH: Int): Bitmap? {
        if (reqW <= 0 || reqH <= 0) return null
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inScaled = false
        }
        BitmapFactory.decodeResource(res, id, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val region = BitmapScaler.cropRegion(bounds.outWidth, bounds.outHeight, reqW, reqH)
        val out = BitmapScaler.outputSize(region.width(), region.height(), reqW, reqH)
        val sample = BitmapScaler.sampleSizeFor(
            region.width(), region.height(), out.width, out.height, BitmapScaler.MAX_DECODE_PIXELS
        )
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inScaled = false
        }
        val raw = BitmapFactory.decodeResource(res, id, opts)
            ?: return null
        val left = (region.left / sample).coerceIn(0, raw.width - 1)
        val top = (region.top / sample).coerceIn(0, raw.height - 1)
        val cropW = (region.width() / sample).coerceIn(1, raw.width - left)
        val cropH = (region.height() / sample).coerceIn(1, raw.height - top)
        val cropped = Bitmap.createBitmap(raw, left, top, cropW, cropH)
        if (cropped !== raw) raw.recycle()
        return BitmapScaler.scaleTo(cropped, out.width, out.height)
    }
}
