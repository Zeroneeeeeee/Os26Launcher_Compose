package gambi.global.os26launcher.core.bitmap

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Size
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * FX1: phép tính thu/cắt ảnh nền dùng chung cho `data/backdrop` (ảnh người dùng chọn và ảnh mặc định).
 *
 * Nguyên tắc: bitmap của Home có cùng TỈ LỆ với vùng Home (kính tại chỗ kéo giãn bitmap vừa view nên chỉ khớp khi hai tỉ lệ bằng nhau)
 * và không lớn hơn vùng đó ([cropRegion] + [outputSize]); file lưu giữ nguyên toàn ảnh nhưng có trần pixel ([savedSize]).
 * Mọi lần giải mã bị chặn bởi [MAX_DECODE_PIXELS] ([sampleSizeFor]) vì `inSampleSize` chỉ có lũy thừa 2.
 */
object BitmapScaler {

    /** Trần pixel của FILE LƯU khi nhập (16 MP, giải mã ≈ 64 MB, dưới ngưỡng 100 MB của canvas). File lưu không bao giờ lên GPU. */
    const val MAX_SAVED_PIXELS = 16_000_000L

    /** Trần pixel của MỘT lần giải mã (25 MP ≈ 100 MB): chặn đỉnh tạm khi nhập và ở đường lui. */
    const val MAX_DECODE_PIXELS = 25_000_000L

    /**
     * Vùng giữa lớn nhất của ảnh [srcW] x [srcH] có tỉ lệ [reqW]:[reqH] (đúng phần `centerCrop` sẽ hiển thị), làm tròn, căn giữa.
     * [reqW] hoặc [reqH] không dương thì trả cả ảnh.
     */
    fun cropRegion(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Rect {
        if (reqW <= 0 || reqH <= 0 || srcW <= 0 || srcH <= 0) return Rect(0, 0, srcW, srcH)
        val regionW: Int
        val regionH: Int
        if (srcW.toLong() * reqH > srcH.toLong() * reqW) {
            // Ảnh rộng hơn màn: giữ chiều cao, cắt bớt chiều rộng
            regionH = srcH
            regionW = (srcH.toDouble() * reqW / reqH).roundToInt().coerceIn(1, srcW)
        } else {
            regionW = srcW
            regionH = (srcW.toDouble() * reqH / reqW).roundToInt().coerceIn(1, srcH)
        }
        val left = (srcW - regionW) / 2
        val top = (srcH - regionH) / 2
        return Rect(left, top, left + regionW, top + regionH)
    }

    /**
     * Cỡ bitmap đầu ra của vùng cắt: thu về không quá [reqW] x [reqH], KHÔNG phóng to (ảnh nhỏ hơn màn thì giữ nguyên, `ImageView`
     * phóng khi vẽ). Tỉ lệ giữ nguyên tỉ lệ vùng cắt.
     */
    fun outputSize(regionW: Int, regionH: Int, reqW: Int, reqH: Int): Size {
        if (reqW <= 0 || reqH <= 0) return Size(regionW, regionH)
        val scale = min(1.0, min(reqW.toDouble() / regionW, reqH.toDouble() / regionH))
        if (scale >= 1.0) return Size(regionW, regionH)
        val w = (regionW * scale).roundToInt().coerceAtLeast(1)
        val h = (regionH * scale).roundToInt().coerceAtLeast(1)
        // Vùng cắt có tỉ lệ bằng màn (sai số làm tròn ≤ 1 px) thì trả đúng cỡ yêu cầu, tránh 1080x2339 so với 1080x2340
        if (abs(w - reqW) <= 1 && abs(h - reqH) <= 1) return Size(reqW, reqH)
        return Size(w, h)
    }

    /**
     * `inSampleSize` (lũy thừa 2) lớn nhất sao cho ảnh giải mã mỗi chiều vẫn không nhỏ hơn đích [dstW] x [dstH]; nếu ảnh giải mã
     * [w] x [h] vẫn vượt [maxDecodePixels] thì tăng gấp đôi cho tới khi hết vượt.
     */
    fun sampleSizeFor(w: Int, h: Int, dstW: Int, dstH: Int, maxDecodePixels: Long): Int {
        var sample = 1
        while (w / (sample * 2) >= dstW && h / (sample * 2) >= dstH) sample *= 2
        return boundSample(w, h, sample, maxDecodePixels)
    }

    /** Tăng [sample] (gấp đôi mỗi lần) cho tới khi ảnh [w] x [h] giải mã ở mức đó không quá [maxDecodePixels]. */
    fun boundSample(w: Int, h: Int, sample: Int, maxDecodePixels: Long): Int {
        var s = sample.coerceAtLeast(1)
        while ((w / s).toLong() * (h / s) > maxDecodePixels && s < MAX_SAMPLE) s *= 2
        return s
    }

    /** Cỡ file lưu khi nhập: thu về [MAX_SAVED_PIXELS] nếu vượt, không phóng to. */
    fun savedSize(srcW: Int, srcH: Int): Size {
        val pixels = srcW.toLong() * srcH
        if (pixels <= MAX_SAVED_PIXELS) return Size(srcW, srcH)
        val scale = sqrt(MAX_SAVED_PIXELS.toDouble() / pixels)
        return Size(
            (srcW * scale).toInt().coerceAtLeast(1),
            (srcH * scale).toInt().coerceAtLeast(1)
        )
    }

    /** Thu [bmp] về [w] x [h] (bilinear); trả nguyên nếu cùng cỡ, recycle [bmp] khi tạo bản mới. */
    fun scaleTo(bmp: Bitmap, w: Int, h: Int): Bitmap {
        if (bmp.width == w && bmp.height == h) return bmp
        val out = Bitmap.createScaledBitmap(bmp, w, h, true)
        if (out !== bmp) bmp.recycle()
        return out
    }

    private const val MAX_SAMPLE = 1 shl 12
}
