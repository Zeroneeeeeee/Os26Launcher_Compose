package gambi.global.os26launcher.ui.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.util.LruCache
import gambi.global.os26launcher.core.bitmap.BitmapScaler
import gambi.global.os26launcher.domain.model.photo.PhotoRef
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Khóa cache: một ảnh (đúng phiên bản vùng cắt) ở một cỡ widget. Không chứa đường dẫn/URI. */
internal data class PhotoKey(
    val itemId: Long,
    val photoId: String,
    val version: Int,
    val width: Int,
    val height: Int
)

/**
 * G3b Task 2 (D3) — cache bitmap của widget Ảnh. LRU theo byte, trần [maxBytes] = min(24 MB, maxMemory/16). Giải mã vùng cắt ở đúng cỡ widget chạy trên MỘT
 * luồng riêng (nối tiếp: không nhiều widget cùng cấp phát), không bao giờ trên luồng chính. Renderer giữ tham chiếu mạnh tới bitmap đang vẽ nên cache chỉ phục
 * vụ ô tái dùng / quay lại trang (bài học FX5: bộ nhớ chính nằm ở view). Mọi lỗi (file hỏng, hết bộ nhớ) nhớ vào [failed] để không thử lại vô hạn.
 * Không bao giờ log đường dẫn hay khóa.
 */
internal object PhotoBitmapCache {
    /** CHỈNH TAY: trần cache (byte). */
    private val maxBytes: Int = minOf(24L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16).toInt()

    private val cache = object : LruCache<PhotoKey, Bitmap>(maxBytes) {
        override fun sizeOf(key: PhotoKey, value: Bitmap): Int = value.byteCount
    }
    private val pending = HashSet<PhotoKey>() // chỉ truy cập trên luồng chính
    private val failed = HashSet<PhotoKey>() // chỉ truy cập trên luồng chính
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "photo-widget-decode").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Luồng chính. Bitmap đã giải mã, hoặc null. */
    fun get(key: PhotoKey): Bitmap? = cache.get(key)

    /** Luồng chính. Khóa này từng giải mã lỗi (file mất/hỏng, hết bộ nhớ): renderer bỏ qua ảnh đó. Xóa khi cấu hình ảnh đổi. */
    fun isFailed(key: PhotoKey): Boolean = key in failed

    /**
     * Luồng chính. Xếp lệnh giải mã [ref] từ [file] ở cỡ [PhotoKey.width] x [PhotoKey.height]; xong thì `WidgetTicker.tickNow()`. Không làm gì nếu đã
     * có lệnh đang chờ, bitmap đã có trong cache, hoặc khóa này từng lỗi.
     */
    fun request(key: PhotoKey, ref: PhotoRef, file: File) {
        if (key in pending || key in failed || cache.get(key) != null) return
        pending += key
        executor.execute {
            val bmp = try {
                decode(file, ref, key.width, key.height)
            } catch (e: OutOfMemoryError) {
                null
            } catch (e: Exception) {
                null
            }
            mainHandler.post {
                pending -= key
                if (bmp != null) cache.put(key, bmp) else failed += key
                WidgetTicker.tickNow()
            }
        }
    }

    /** Luồng chính. Cấu hình ảnh đổi: cho phép thử lại các khóa từng lỗi, bỏ bitmap của ảnh không còn trong [isLive]. */
    fun onConfigChanged(isLive: (PhotoKey) -> Boolean) {
        failed.clear()
        for (key in cache.snapshot().keys) if (!isLive(key)) cache.remove(key)
    }

    fun evictAll() {
        cache.evictAll()
    }

    /**
     * Vùng cắt chuẩn hóa -> pixel của ảnh nguồn, rồi chỉnh theo tỉ lệ widget THẬT ([outW]:[outH]): lệch tỉ lệ lúc cắt thì co cạnh dài của vùng quanh tâm cho
     * phủ kín (plan D2). Giải mã đúng vùng bằng [BitmapRegionDecoder] + `inSampleSize` rồi thu về [outW] x [outH]; đường lui giải mã cả ảnh (có chặn pixel).
     */
    private fun decode(file: File, ref: PhotoRef, outW: Int, outH: Int): Bitmap? {
        if (outW <= 0 || outH <= 0) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null

        var left = ref.crop.left * w
        var top = ref.crop.top * h
        var cw = (ref.crop.right - ref.crop.left) * w
        var ch = (ref.crop.bottom - ref.crop.top) * h
        val target = outW.toFloat() / outH
        if (cw / ch > target) {
            val nw = ch * target
            left += (cw - nw) / 2f
            cw = nw
        } else {
            val nh = cw / target
            top += (ch - nh) / 2f
            ch = nh
        }
        val region = Rect(
            left.roundToInt().coerceIn(0, w - 1),
            top.roundToInt().coerceIn(0, h - 1),
            (left + cw).roundToInt().coerceIn(1, w),
            (top + ch).roundToInt().coerceIn(1, h)
        )
        if (region.width() <= 0 || region.height() <= 0) return null

        val sample = BitmapScaler.sampleSizeFor(region.width(), region.height(), outW, outH, BitmapScaler.MAX_DECODE_PIXELS)
        val raw = decodeRegion(file, region, sample) ?: decodeWholeThenCrop(file, w, h, region, outW, outH) ?: return null
        return BitmapScaler.scaleTo(raw, outW, outH)
    }

    @Suppress("DEPRECATION") // newInstance(String) chỉ có từ API 31, minSdk 26
    private fun decodeRegion(file: File, region: Rect, sample: Int): Bitmap? = try {
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

    private fun decodeWholeThenCrop(file: File, w: Int, h: Int, region: Rect, outW: Int, outH: Int): Bitmap? {
        val quality = BitmapScaler.sampleSizeFor(region.width(), region.height(), outW, outH, Long.MAX_VALUE)
        val sample = BitmapScaler.boundSample(w, h, quality, BitmapScaler.MAX_DECODE_PIXELS)
        val whole = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val left = (region.left / sample).coerceIn(0, whole.width - 1)
        val top = (region.top / sample).coerceIn(0, whole.height - 1)
        val cropW = (region.width() / sample).coerceIn(1, whole.width - left)
        val cropH = (region.height() / sample).coerceIn(1, whole.height - top)
        val cropped = Bitmap.createBitmap(whole, left, top, cropW, cropH)
        if (cropped !== whole) whole.recycle()
        return cropped
    }
}
