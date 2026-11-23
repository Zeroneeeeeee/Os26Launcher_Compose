package gambi.global.os26launcher.ui.controlcenter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import gambi.global.os26launcher.ui.controlcenter.compose.ControlCenterPanel
import android.view.animation.PathInterpolator
import gambi.global.os26launcher.core.ui.TransitionMotion
import gambi.global.os26launcher.domain.config.TransitionStyle
import kotlin.math.abs

/**
 * G3d: hoạt ảnh của panel theo quãng kéo, học từ Launcher OS (RE mục 2.5), viết lại cho dự án:
 * - nền: `alpha = clamp(6·dy / H)`, đậm hẳn khi kéo được 1/6 màn hình;
 * - mỗi khối: dưới [revealThresholdPx] thì ẩn (alpha 0, scale 0,8 trong 100 ms); vượt ngưỡng thì hiện (alpha 0→1, scale 0,8→1
 *   trong 250 ms) rồi dịch `translationY = (dy − ngưỡng)·speed / 3`, `speed` 1,45/1,6 xen kẽ nên các khối lệch nhau như cao su;
 * - thả tay mở: mọi khối về `translationY = 0`, alpha 1, scale 1 bằng `PathInterpolator(0.16, 0.545, 0.455, 1)`, thời lượng
 *   `clamp(|translationY|·730/700, 150, 730)` ms;
 * - đóng: khối mờ và co (100 ms), nền mờ dần, xong gọi `onClosed`.
 *
 * CG (2026-11-20): kéo mở theo tay trực tiếp, hiện đủ ở [shownDistance] (thay ngưỡng alpha 1/6 màn và hoạt ảnh hiện 250 ms ở trên);
 * kéo xuống trên trang chính khi đang mở ([dragDismiss]) thì nền và khối mờ dần theo tay; nhả tay thì [settleOpen] hoặc
 * [settleClosed].
 *
 * TR — theo `TransitionMotion` (màn 4.1): mọi thời lượng nhân theo tốc độ, "Không có" = 0 ms. Kiểu: Mượt = như trên; Mờ dần = khối chỉ
 * mờ (không co, không giãn theo tay); Trượt = không co, giữ giãn theo tay; Phóng to = co sâu hơn ([ZOOM_HIDDEN_SCALE]), không giãn.
 *
 * JS7b: khối là [gambi.global.os26launcher.ui.controlcenter.compose.CcBlockAnim] của panel Compose (trạng thái alpha/scale/dịch, chạy
 * bằng ValueAnimator) thay cho `View` + `view.animate()`; mọi hằng, công thức và thời lượng giữ nguyên.
 */
class ControlCenterMotion(
    private val view: ControlCenterPanel,
    private val revealThresholdPx: Float
) {
    private val blocks get() = view.animatedBlocks
    // Cấp lại ở reset(): số khối đổi khi người dùng đổi ô tùy chỉnh.
    private var revealed = BooleanArray(0)
    private var backgroundAlpha = 0f
    private var bgAnimator: ValueAnimator? = null

    /** Bắt đầu một lần kéo mở từ dải mép: mọi khối ẩn, nền trong suốt. */
    fun reset() {
        cancel()
        collapsedHidden = false
        backgroundAlpha = 0f
        view.setBackgroundAlpha(0f)
        revealed = BooleanArray(blocks.size)
        blocks.forEachIndexed { i, b ->
            mark(i, false)
            b.alpha = 0f
            b.scale = hiddenScale
            b.translationY = 0f
        }
    }

    private fun screenHeight(): Int = view.height.takeIf { it > 0 } ?: view.resources.displayMetrics.heightPixels

    /**
     * CG: quãng kéo (px) mà panel đã hiện đủ: nền đậm hẳn, khối đủ alpha và cỡ. Thả tay từ quãng này trở đi là mở hẳn
     * (chủ dự án 2026-11-20: "hiện được tầm 50% là hiển thị được rồi").
     */
    fun shownDistance(): Float = screenHeight() * SHOWN_FRACTION

    /**
     * Đang kéo xuống từ dải mép, [dy] ≥ 0 px. CG: khối theo tay trực tiếp (không chạy hoạt ảnh hiện 250 ms nữa) nên không có cảm
     * giác "tù": tiến độ p = (dy − ngưỡng) / (quãng hiện đủ − ngưỡng); quá quãng hiện đủ thì khối giãn nhẹ theo tay như cao su.
     */
    fun dragFromEdge(dy: Float, collapsed: Boolean = false) {
        val full = shownDistance()
        setBackground((dy / full).coerceIn(0f, 1f))
        // REF a38f769: kéo hết cỡ ([collapsed]) thì khối ẩn như khi dưới ngưỡng (một lần, HIDE_MS) để báo trước thả tay sẽ đóng;
        // nền giữ theo tay, [settleClosed] làm mờ khi đóng hẳn.
        if (collapsed) {
            if (!collapsedHidden) {
                collapsedHidden = true
                blocks.forEachIndexed { i, b ->
                    mark(i, false)
                    b.animateTo(0f, hiddenScale, null, TransitionMotion.duration(HIDE_MS))
                }
            }
            return
        }
        val p = ((dy - revealThresholdPx) / (full - revealThresholdPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val over = (dy - full).coerceAtLeast(0f)
        val scale = hiddenScale + (1f - hiddenScale) * p
        val leavingCollapse = collapsedHidden
        collapsedHidden = false
        blocks.forEachIndexed { i, b ->
            mark(i, p > 0f)
            val ty = if (stretches) over * speed(i) / STRETCH_DIVISOR else 0f
            if (leavingCollapse) {
                // Ra khỏi vùng hết cỡ: khối hiện lại bằng hoạt ảnh (REF REVEAL_MS), không bật phụt.
                b.animateTo(p, scale, null, TransitionMotion.duration(REVEAL_MS))
            } else if (!b.isAnimating) {
                b.alpha = p
                b.scale = scale
            }
            b.translationY = ty
        }
    }

    /** Đang ẩn khối vì kéo hết cỡ (REF a38f769). */
    private var collapsedHidden = false

    /** CG: quãng kéo xuống trên trang chính để đóng; thả tay quá [dismissDistance] (hoặc vuốt nhanh) thì đóng mờ dần. */
    fun dismissDistance(): Float = screenHeight() * DISMISS_FRACTION

    /**
     * CG: đang kéo XUỐNG trên trang chính của panel đã mở, [dy] ≥ 0 px: nền và khối mờ dần theo tay, khối co nhẹ và trôi xuống
     * một chút, để thấy rõ thả tay là đóng.
     */
    fun dragDismiss(dy: Float) {
        val fade = (dy / (dismissDistance() * DISMISS_FADE_SPAN)).coerceIn(0f, 1f)
        setBackground(1f - fade)
        val scale = 1f - (1f - DISMISS_MIN_SCALE) * fade
        blocks.forEachIndexed { i, b ->
            b.cancel()
            mark(i, true)
            b.alpha = 1f - fade
            b.scale = scale
            b.translationY = dy * DISMISS_FOLLOW
        }
    }

    fun settleOpen(onSettled: () -> Unit = {}) {
        var longest = MIN_SETTLE_MS
        blocks.forEachIndexed { i, b ->
            mark(i, true)
            val ms = settleDuration(b.translationY)
            longest = maxOf(longest, ms)
            b.cancel()
            b.animateTo(1f, 1f, 0f, TransitionMotion.duration(ms), SETTLE)
        }
        animateBackground(1f, TransitionMotion.duration(longest)) { onSettled() }
    }

    fun settleClosed(onClosed: () -> Unit) {
        blocks.forEachIndexed { i, b ->
            mark(i, false)
            b.cancel()
            b.animateTo(0f, hiddenScale, null, TransitionMotion.duration(HIDE_MS))
        }
        animateBackground(0f, TransitionMotion.duration(CLOSE_BACKGROUND_MS), onClosed)
    }

    fun cancel() {
        bgAnimator?.removeAllListeners()
        bgAnimator?.cancel()
        bgAnimator = null
        blocks.forEach { it.cancel() }
    }

    private fun setBackground(alpha: Float) {
        bgAnimator?.cancel()
        backgroundAlpha = alpha
        view.setBackgroundAlpha(alpha)
    }

    private fun animateBackground(target: Float, durationMs: Long, onEnd: () -> Unit) {
        bgAnimator?.removeAllListeners()
        bgAnimator?.cancel()
        val anim = ValueAnimator.ofFloat(backgroundAlpha, target).setDuration(durationMs)
        anim.interpolator = SETTLE
        anim.addUpdateListener {
            backgroundAlpha = it.animatedValue as Float
            view.setBackgroundAlpha(backgroundAlpha)
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) onEnd()
            }
        })
        bgAnimator = anim
        anim.start()
    }

    private fun mark(i: Int, v: Boolean) {
        if (i < revealed.size) revealed[i] = v
    }

    /** TR: cỡ của khối lúc ẩn theo kiểu chuyển cảnh. */
    private val hiddenScale: Float
        get() = when (TransitionMotion.style) {
            TransitionStyle.SMOOTH -> HIDDEN_SCALE
            TransitionStyle.ZOOM -> ZOOM_HIDDEN_SCALE
            else -> 1f
        }

    /** TR: khối giãn theo tay (cao su) chỉ ở Mượt và Trượt. */
    private val stretches: Boolean
        get() = TransitionMotion.style.let { it == TransitionStyle.SMOOTH || it == TransitionStyle.SLIDE }

    private fun speed(i: Int) = if (i % 2 == 0) SPEED_EVEN else SPEED_ODD

    private fun settleDuration(translationY: Float): Long =
        (abs(translationY) * SETTLE_MS_PER_PX).toLong().coerceIn(MIN_SETTLE_MS, MAX_SETTLE_MS)

    private companion object {
        /** CG: kéo mở được 30% chiều cao màn là panel hiện đủ; Overlay mở khi thả tay ở nửa quãng này (panel hiện 50%). */
        const val SHOWN_FRACTION = 0.3f

        /** CG: kéo xuống trên trang chính quá 12% chiều cao màn rồi thả là đóng; mờ hết ở 2 lần quãng đó. */
        const val DISMISS_FRACTION = 0.12f
        const val DISMISS_FADE_SPAN = 2f
        const val DISMISS_MIN_SCALE = 0.9f
        const val DISMISS_FOLLOW = 0.35f
        const val HIDDEN_SCALE = 0.8f
        const val ZOOM_HIDDEN_SCALE = 0.6f // TR: hằng chỉnh tay
        const val STRETCH_DIVISOR = 3f
        const val SPEED_EVEN = 1.45f
        const val SPEED_ODD = 1.6f
        const val HIDE_MS = 180L
        const val REVEAL_MS = 250L
        const val CLOSE_BACKGROUND_MS = 240L
        const val MIN_SETTLE_MS = 150L
        const val MAX_SETTLE_MS = 730L
        const val SETTLE_MS_PER_PX = 730f / 700f
        val SETTLE = PathInterpolator(0.16f, 0.545f, 0.455f, 1f)
    }
}
