package gambi.global.os26launcher.ui.home

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * TL1 Task 2 — chuyển động đổi trang của Home, dùng chung cho vuốt tay ([TunedPagerSnapHelper]) và lật có chủ đích
 * (`PagerFlipAnimator`, Task 3) để cả hai cùng cảm giác.
 *
 * **Các hằng dưới đây là HẰNG CHỈNH TAY theo cảm nhận trên Vsmart Live 4, không suy ra từ đặc tả iOS** (Apple không công
 * bố). Dải thử cho [FULL_MS]: 400–500; [MIN_MS] = 300 để đoạn ngắn vẫn không vội và gần với đường "nhấc tay chậm" mặc định (~300 ms).
 */
internal object PageMotion {
    /** Thời lượng đổi trọn một trang. */
    const val FULL_MS = 450

    /** Thời lượng tối thiểu khi chỉ còn một đoạn ngắn tới đích (vòng 2/5: nâng từ 220). */
    const val MIN_MS = 300

    /**
     * Nghỉ tối thiểu giữa hai lượt lật kéo-thả liên tiếp: lượt sau chỉ chạy khi ngón tay còn giữ sát mép ít nhất chừng này
     * SAU khi pager về IDLE (cùng nhịp nghỉ như iOS). Hằng chỉnh tay.
     */
    const val EDGE_FLIP_REST_MS = 500L

    /**
     * easeOutQuint, cubic-bezier(0.22, 1, 0.36, 1): cùng họ với `sQuinticInterpolator` mà `RecyclerView` đã dùng cho
     * đoạn "nhấc tay chậm" (`smoothScrollBy` mặc định), nên hai đường vuốt tay trông cùng kiểu. Bắt đầu nhanh (liền mạch
     * với ngón tay) và kết thúc với vận tốc ~0.
     */
    val CURVE: Interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)

    /** [fraction] = quãng còn lại / bề rộng một trang, kẹp vào [0, 1]. */
    fun durationMs(fraction: Float): Int =
        MIN_MS + ((FULL_MS - MIN_MS) * fraction.coerceIn(0f, 1f)).toInt()
}
