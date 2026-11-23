package gambi.global.os26launcher.ui.controlcenter

import android.graphics.Bitmap
import gambi.global.os26launcher.domain.controlcenter.ControlCenterBackgroundStore
import gambi.global.os26launcher.domain.model.controlcenter.BackgroundMode
import gambi.global.os26launcher.domain.repository.BackdropRepository
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * G3d: nền panel theo [BackgroundMode] (RE mục 5.4). Ảnh nền của launcher hoặc ảnh tự chọn, thu nhỏ rồi làm mờ MỘT lần (RE mục
 * 1.4: app mẫu cũng dùng ảnh tĩnh, inSampleSize 4, thu 0,2 lần, stack blur bán kính 30). Ta thu về bề rộng [SMALL_WIDTH] px rồi
 * box blur 3 lượt (gần Gauss); ImageView phóng to bằng lọc song tuyến nên mịn, API 31+ thêm RenderEffect ở
 * [gambi.global.os26launcher.ui.controlcenter.compose.ControlCenterPanel.setBackdrop] (JS7b). Tắt làm mờ thì giữ ảnh ở cỡ [SHARP_DIVISOR].
 *
 * Nhớ theo (chế độ, làm mờ, phiên bản ảnh): đổi gì thì dựng lại ở lần mở panel kế tiếp.
 */
internal class PanelBackdrop(
    private val launcherBackdrop: BackdropRepository,
    private val customBackground: ControlCenterBackgroundStore
) {
    private var cachedKey: Triple<BackgroundMode, Boolean, Long>? = null
    private var cached: Bitmap? = null

    /** Gọi ngoài main thread. [screenW]x[screenH]: cỡ màn để giữ đúng tỉ lệ khi centerCrop. null = không có ảnh nền. */
    suspend fun load(mode: BackgroundMode, blur: Boolean, screenW: Int, screenH: Int): Bitmap? {
        if (mode == BackgroundMode.NONE) return null
        val version = when (mode) {
            BackgroundMode.WALLPAPER -> launcherBackdrop.backdropVersion()
            BackgroundMode.CUSTOM_IMAGE -> customBackground.version()
            BackgroundMode.NONE -> 0L
        }
        val key = Triple(mode, blur, version)
        if (key == cachedKey) cached?.let { return it }
        val divisor = if (blur) LOAD_DIVISOR else SHARP_DIVISOR
        val reqW = max(1, screenW / divisor)
        val reqH = max(1, screenH / divisor)
        val source = when (mode) {
            BackgroundMode.WALLPAPER ->
                (if (launcherBackdrop.hasBackdrop()) launcherBackdrop.loadBackdrop(reqW, reqH) else null)
                    ?: launcherBackdrop.loadDefaultBackdrop(reqW, reqH)

            BackgroundMode.CUSTOM_IMAGE -> customBackground.load(reqW, reqH)
                ?: launcherBackdrop.loadBackdrop(reqW, reqH) // chưa chọn ảnh: dùng ảnh nền của launcher
                ?: launcherBackdrop.loadDefaultBackdrop(reqW, reqH)

            BackgroundMode.NONE -> null
        } ?: return null
        val out = if (blur) blur(source) else source
        cachedKey = key
        cached = out
        return out
    }

    private fun blur(source: Bitmap): Bitmap {
        val w = SMALL_WIDTH
        val h = max(1, (source.height * (SMALL_WIDTH.toFloat() / source.width)).roundToInt())
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(PASSES) {
            boxBlur(px, tmp, w, h, RADIUS, horizontal = true)
            boxBlur(tmp, px, w, h, RADIUS, horizontal = false)
        }
        val out = if (small.isMutable) small else small.copy(Bitmap.Config.ARGB_8888, true)
        out.setPixels(px, 0, w, 0, 0, w, h)
        if (out !== small && small !== source) small.recycle()
        return out
    }

    /** Box blur một chiều, kẹp mép. */
    private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (line in 0 until lines) {
            var a = 0
            var rr = 0
            var g = 0
            var b = 0
            fun at(i: Int): Int {
                val k = i.coerceIn(0, len - 1)
                return if (horizontal) src[line * w + k] else src[k * w + line]
            }
            for (i in -r..r) {
                val c = at(i)
                a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            for (i in 0 until len) {
                val idx = if (horizontal) line * w + i else i * w + line
                dst[idx] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
                val out = at(i - r)
                val inn = at(i + r + 1)
                a += (inn ushr 24) - (out ushr 24)
                rr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }

    private companion object {
        const val LOAD_DIVISOR = 4
        const val SHARP_DIVISOR = 2
        const val SMALL_WIDTH = 72
        const val RADIUS = 3
        const val PASSES = 3
    }
}
