package gambi.global.os26launcher.core.ui

import android.animation.ValueAnimator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs

/**
 * AN Task 1 — một số thực chạy theo spring (AndroidX DynamicAnimation 1.1.0). View sở hữu một [SpringProgress] cho mỗi
 * chuyển động (mở/đóng panel, nhấn co, hover, trượt, hạ cánh) và đặt ĐÍCH theo dữ liệu bằng [animateTo]; tiến độ giữa chừng
 * ([value]) chỉ là hiệu ứng ở view, không phải nguồn sự thật (bài học M7/B2: trạng thái ổn định ở ViewModel/object ngoài view).
 *
 * Ngắt giữa chừng = gọi [animateTo] với đích mới: spring đang chạy đổi đích và GIỮ vị trí lẫn vận tốc (đảo chiều liền mạch).
 * Đứng yên thì không có spring nào chạy (chi phí 0). Người dùng tắt hoạt ảnh của hệ thống
 * ([ValueAnimator.areAnimatorsEnabled], API 26) thì [animateTo] thành [snapTo].
 *
 * Chỉ dùng trên main thread (DynamicAnimation ném `AndroidRuntimeException` nếu khởi động từ luồng khác).
 * Gọi [cancel] ở `onDetachedFromWindow` của view chủ.
 *
 * @param onUpdate gọi mỗi khung với [value] mới (có thể vượt [0, 1] một chút nếu spring nảy).
 * @param onSettled gọi một lần khi spring dừng hẳn ở đích (hoặc sau [snapTo]); KHÔNG gọi khi bị [cancel].
 * @param initialValue giá trị lúc chưa chạy (vd 1 cho scale); không gọi [onUpdate].
 */
class SpringProgress(
    private var spec: Spec,
    private val onUpdate: (Float) -> Unit,
    private val onSettled: (Float) -> Unit = {},
    initialValue: Float = 0f
) {
    /**
     * Thông số spring. Các preset là **điểm xuất phát chỉnh tay, chưa đo cảm giác** (AN, 2026-11-04); chủ dự án phán cảm
     * giác, agent chỉnh theo mắt ở Task 12. Độ cứng càng lớn càng nhanh; giảm chấn < 1 thì nảy (0.6 nảy rõ, 0.85 gần như không).
     */
    data class Spec(
        val stiffness: Float,
        val dampingRatio: Float,
        /** Dừng khi thay đổi nhỏ hơn mức này; mặc định hợp với tiến độ 0..1 và scale. */
        val minVisibleChange: Float = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE
    ) {
        companion object {
            /** Mở/đóng panel từ icon/ô. */
            val PANEL = Spec(stiffness = 400f, dampingRatio = 0.85f)

            /** Nhấn co / thả ra. */
            val PRESS = Spec(stiffness = 1500f, dampingRatio = 0.75f)

            /** Kính hover và phóng thư mục khi kéo qua. */
            val HOVER = Spec(stiffness = 900f, dampingRatio = 0.6f)

            /** Icon trượt vào chỗ (độ dời, đơn vị px nên mức thay đổi nhìn thấy lớn hơn). */
            val SLIDE = Spec(stiffness = 800f, dampingRatio = 0.8f, minVisibleChange = 0.5f)

            /**
             * FM-3 — HẰNG CHỈNH TAY (chủ dự án 2026-11-20 "chạy chậm lại cho mượt", rồi tự hạ 350 -> 300): icon dời chỗ để chừa ô cho
             * icon đang kéo, dùng chung cho lưới Home, dock và panel thư mục. Chậm hơn SLIDE (~1,6 lần), giảm chấn 0,85 gần như không nảy.
             */
            val REORDER = Spec(stiffness = 300f, dampingRatio = 0.85f, minVisibleChange = 0.5f)

            /** Icon bị gỡ/ẩn thu về 0 tại chỗ (tới hạn, không nảy, ~200 ms). */
            val EXIT = Spec(stiffness = 900f, dampingRatio = 1f)

            /** Ảnh bay lúc thả hạ cánh vào ô. */
            val SETTLE = Spec(stiffness = 600f, dampingRatio = 0.8f)
        }
    }

    private val holder = FloatValueHolder(initialValue)
    private var animation: SpringAnimation? = null

    /** Giá trị hiện tại (đang chạy thì là vị trí giữa chừng). */
    val value: Float get() = holder.value

    /** Đích gần nhất đã đặt. */
    var target: Float = initialValue
        private set

    val isRunning: Boolean get() = animation?.isRunning == true

    /**
     * Chạy tới [target]. Đang chạy thì đổi đích, giữ vị trí và vận tốc. [startVelocity] chỉ dùng khi spring đang đứng yên
     * (đơn vị giá trị/giây), mặc định 0. [newSpec] đổi độ cứng/giảm chấn từ lúc này (vd cùng một scale lúc nhấn dùng PRESS, lúc hover dùng HOVER).
     */
    fun animateTo(target: Float, startVelocity: Float = 0f, newSpec: Spec? = null) {
        if (newSpec != null && newSpec != spec) {
            spec = newSpec
            animation?.spring?.apply {
                stiffness = newSpec.stiffness
                dampingRatio = newSpec.dampingRatio
            }
        }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            snapTo(target)
            return
        }
        this.target = target
        val running = animation
        if (running != null && running.isRunning) {
            running.animateToFinalPosition(target)
            return
        }
        if (abs(holder.value - target) < spec.minVisibleChange) {
            // Đã ở đích: không cần chạy, nhưng vẫn báo giá trị chính xác để view khớp.
            holder.value = target
            onUpdate(target)
            onSettled(target)
            return
        }
        val spring = animation ?: createAnimation().also { animation = it }
        if (startVelocity != 0f) spring.setStartVelocity(startVelocity)
        spring.animateToFinalPosition(target)
    }

    /** Đặt thẳng [value], huỷ spring đang chạy (không gọi `onSettled` của lần chạy bị huỷ), rồi báo update + settled. */
    fun snapTo(value: Float) {
        cancel()
        target = value
        holder.value = value
        onUpdate(value)
        onSettled(value)
    }

    /** Dừng tại chỗ (giữ [value] hiện tại), không báo `onSettled`. */
    fun cancel() {
        val running = animation ?: return
        if (running.isRunning) running.cancel()
        target = holder.value
    }

    private fun createAnimation(): SpringAnimation =
        SpringAnimation(holder).apply {
            spring = SpringForce(this@SpringProgress.target).apply {
                stiffness = spec.stiffness
                dampingRatio = spec.dampingRatio
            }
            setMinimumVisibleChange(spec.minVisibleChange)
            addUpdateListener { _, v, _ -> onUpdate(v) }
            addEndListener { _, canceled, v, _ -> if (!canceled) onSettled(v) }
        }
}
