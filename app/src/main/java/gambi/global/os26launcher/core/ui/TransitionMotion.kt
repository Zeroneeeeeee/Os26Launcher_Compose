package gambi.global.os26launcher.core.ui

import gambi.global.os26launcher.domain.config.MotionConfig
import gambi.global.os26launcher.domain.config.MotionConfigRepository
import gambi.global.os26launcher.domain.config.MotionSpeed
import gambi.global.os26launcher.domain.config.TransitionStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * TR — cấu hình "Hiệu ứng chuyển cảnh" (màn 4.1) cho các chuyển động mở/đóng: panel thư mục, panel nhóm App Library, lớp tìm kiếm,
 * lớp phủ App Library và Cài đặt nhanh (Trung tâm điều khiển). View không được Hilt tiêm nên đọc bản sao ở đây; [init] (gọi một lần ở
 * `LauncherApp`) thu `MotionConfigRepository` suốt đời process, đổi ở Cài đặt là áp từ lần mở/đóng kế tiếp. Chỉ đọc ở main thread.
 *
 * - [style]: kiểu đã tính "Giảm chuyển động" (Mượt / Trượt / Phóng to -> Mờ dần).
 * - [isInstant]: kiểu "Không có": nhảy thẳng tới trạng thái cuối.
 * - Tốc độ: nhân thời lượng [durationScale] (Chậm 1.5, Vừa 1, Nhanh 0.65); spring đổi độ cứng theo [spring] cho cùng tỉ lệ thời gian.
 * - Làm mờ nền: độ đậm của nền mờ đi theo tiến độ mở/đóng; phần hiện dần khi ảnh mờ tới muộn (`BlurredSnapshotScrim`, nền màn nhóm
 *   `BackdropBlurDrawable`) cũng theo [duration], "Không có" thì hiện ngay.
 */
object TransitionMotion {

    @Volatile
    private var config = MotionConfig()

    private var started = false

    fun init(repository: MotionConfigRepository, scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch { repository.config.collect { config = it } }
    }

    val style: TransitionStyle
        get() {
            val c = config
            return if (c.reduceMotion && c.transitionStyle in REDUCED) TransitionStyle.FADE else c.transitionStyle
        }

    val isInstant: Boolean get() = style == TransitionStyle.NONE

    val durationScale: Float
        get() = when (config.transitionSpeed) {
            MotionSpeed.SLOW -> SLOW_SCALE
            MotionSpeed.MEDIUM -> 1f
            MotionSpeed.FAST -> FAST_SCALE
        }

    /** Thời lượng [baseMs] theo tốc độ; 0 khi [isInstant]. */
    fun duration(baseMs: Long): Long = if (isInstant) 0L else (baseMs * durationScale).toLong()

    /** [base] theo tốc độ: chu kỳ spring tỉ lệ 1/√độ cứng nên thời gian ×s thì độ cứng ÷s². Giảm chấn giữ nguyên. */
    fun spring(base: SpringProgress.Spec): SpringProgress.Spec {
        val s = durationScale
        return if (s == 1f) base else base.copy(stiffness = base.stiffness / (s * s))
    }

    private val REDUCED = setOf(TransitionStyle.SMOOTH, TransitionStyle.SLIDE, TransitionStyle.ZOOM)
    private const val SLOW_SCALE = 1.5f
    private const val FAST_SCALE = 0.65f
}
