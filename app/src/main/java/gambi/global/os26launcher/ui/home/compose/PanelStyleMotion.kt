package gambi.global.os26launcher.ui.home.compose

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import gambi.global.os26launcher.core.ui.TransitionMotion
import gambi.global.os26launcher.domain.config.TransitionStyle

/** PG1 F3 — phép biến đổi của một panel ở tiến độ `p` (0 = đóng, 1 = mở): tỉ lệ (quanh tâm panel), dời (px), độ trong. */
internal data class PanelTransform(val scale: Float, val translationX: Float, val translationY: Float, val alpha: Float)

/**
 * PG1 F3 — bản Compose của bảng kiểu trong `PanelMotion` (repo XML, TR màn 4.1) cho panel thư mục và màn nhóm Thư viện. Hàm thuần.
 *
 * - Mượt: chuyển động riêng của từng panel (thư mục phóng từ icon, màn nhóm phình từ giữa, P2-3), do nơi gọi tính và truyền vào [smooth].
 * - Mờ dần / Không có: chỉ độ trong, tại chỗ (Không có thì nơi gọi `snapTo` nên chỉ thấy 0 hoặc 1).
 * - Trượt: đỉnh panel bắt đầu ở đáy màn rồi trồi lên; độ trong như Mượt.
 * - Phóng to: phóng từ [ZOOM_START_SCALE] tại tâm panel; độ trong như Mượt.
 *
 * Kiểu đọc một lần lúc bắt đầu mỗi lần mở/đóng ([TransitionMotion.style], đã tính "Giảm chuyển động"), không đổi giữa chừng.
 */
internal object PanelStyleMotion {
    /** TR: kiểu "Phóng to" bắt đầu từ cỡ này của panel (như `PanelMotion.ZOOM_START_SCALE`). Hằng chỉnh tay. */
    const val ZOOM_START_SCALE = 0.85f

    /** [slideFromPx]: quãng dời dọc ở p = 0 cho kiểu Trượt (đáy màn − đỉnh panel). */
    fun transform(style: TransitionStyle, p: Float, smooth: PanelTransform, slideFromPx: Float): PanelTransform = when (style) {
        TransitionStyle.SMOOTH -> smooth
        TransitionStyle.FADE, TransitionStyle.NONE -> PanelTransform(1f, 0f, 0f, p.coerceIn(0f, 1f))
        TransitionStyle.SLIDE -> PanelTransform(1f, 0f, slideFromPx * (1f - p), smooth.alpha)
        TransitionStyle.ZOOM -> PanelTransform(ZOOM_START_SCALE + (1f - ZOOM_START_SCALE) * p, 0f, 0f, smooth.alpha)
    }

    /** Spring mở/đóng theo tốc độ của màn 4.1 (như `TransitionMotion.spring`: thời gian ×s thì độ cứng ÷s², giảm chấn giữ nguyên). */
    fun scaledSpring(dampingRatio: Float, stiffness: Float): SpringSpec<Float> {
        val s = TransitionMotion.durationScale
        return spring(dampingRatio = dampingRatio, stiffness = if (s == 1f) stiffness else stiffness / (s * s))
    }
}
