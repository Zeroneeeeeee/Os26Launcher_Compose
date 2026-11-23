package gambi.global.os26launcher.core.glass

import android.view.View
import gambi.global.os26launcher.R
import kotlin.math.roundToInt

/**
 * GF: vị trí màn hình của một view dùng để chọn lát cắt nền cho kính mờ, BỎ phép dịch (`translationX/Y`) của các tổ tiên đánh dấu bằng tag
 * [R.id.glass_ignore_translation] (`home_content` trượt sang App Library). Không đụng phép biến đổi của chính view, cuộn của pager hay vị trí
 * layout của con trong cha. Không cấp phát: chỉ đi ngược cây cha.
 */
object GlassLocation {
    fun locationOnScreen(view: View, out: IntArray) {
        view.getLocationOnScreen(out)
        var dx = 0f
        var dy = 0f
        var p = view.parent
        while (p is View) {
            if (p.getTag(R.id.glass_ignore_translation) != null) {
                dx += p.translationX
                dy += p.translationY
            }
            p = p.parent
        }
        if (dx != 0f) out[0] -= dx.roundToInt()
        if (dy != 0f) out[1] -= dy.roundToInt()
    }
}
