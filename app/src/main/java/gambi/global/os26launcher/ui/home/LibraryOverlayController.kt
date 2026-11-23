package gambi.global.os26launcher.ui.home

import android.view.MotionEvent
import android.view.VelocityTracker
import gambi.global.os26launcher.core.ui.SpringProgress
import gambi.global.os26launcher.core.ui.TransitionMotion
import gambi.global.os26launcher.ui.home.compose.AppLibraryMotion
import kotlin.math.abs

/**
 * G2a B2 (D17) — cử chỉ + animation của lớp phủ App Library, tách khỏi `ViewPager2` (mẫu `LauncherOverlay` của Launcher3).
 * Chỉ giữ TIẾN ĐỘ [progress] (0 = đóng, 1 = mở hẳn); mọi hiệu ứng nhìn thấy (trượt, tối nền, mờ Home) do [Host.onProgress] áp.
 *
 * Gọi [onTouch] đầu `dispatchTouchEvent` cho mọi chạm khi không có phiên kéo icon. Kết quả:
 * - [Result.PASS]: không liên quan, tiếp tục xử lý bình thường;
 * - [Result.TAKEOVER]: vừa tiếp quản chuỗi chạm — Activity gửi một `ACTION_CANCEL` giả xuống cây view rồi nuốt sự kiện;
 * - [Result.CONSUME]: đang kéo, nuốt sự kiện.
 *
 * Mở: vuốt ngang sang trái khi [Host.canOpen]; đóng: vuốt ngang sang phải khi đang mở. Dọc trội thì bỏ (để cuộn/tìm kiếm).
 * Spring mở/đóng ngắt được (D2 vòng 4): chạm lúc spring còn chạy thì vuốt ngang bắt lấy Library tại tiến độ hiện tại và kéo tiếp, trừ vuốt phải lúc spring
 * đang đóng: chiều đó thuộc pager (lùi trang, LB).
 */
class LibraryOverlayController(
    private val host: Host,
    private val slopPx: Float,
    private val flingVelocityPxPerSec: Float,
    /**
     * TV (D3): chiều mở. true (mặc định, App Library) = vuốt sang TRÁI mở; false (màn Today) = vuốt sang PHẢI mở. Mọi chỗ đọc `dx`/`vx` nhân với
     * [dir] để đổi về "trục mở" (âm = về phía mở), nên phần còn lại của controller không biết mình đang chạy chiều nào.
     */
    opensLeftward: Boolean = true
) {
    private val dir = if (opensLeftward) 1f else -1f

    interface Host {
        /** Bề rộng quãng trượt (px). */
        val width: Int

        /** Đang ở trang thật cuối, đứng yên, và không vướng trạng thái nào chặn (D14). */
        fun canOpen(): Boolean

        /** GF: false khi màn nhóm đang mở hoặc đang mở/đóng: vuốt rời Library bị bỏ qua (Home không trượt vào sau màn nhóm). */
        fun canClose(): Boolean

        /** [progress] đổi (0..1): áp vị trí/độ tối/độ mờ. */
        fun onProgress(progress: Float)

        /** Đã dừng hẳn ở trạng thái [open] (sau cử chỉ hoặc animation). */
        fun onSettled(open: Boolean)
    }

    enum class Result { PASS, TAKEOVER, CONSUME }

    var progress = 0f
        private set

    /** Trạng thái ổn định gần nhất (không tính lúc đang kéo/animate). */
    var isOpen = false
        private set

    /** GRAB (D2 vòng 4): chạm xuống lúc spring mở/đóng còn chạy; vuốt ngang bắt lấy Library tại [progress] hiện tại, trừ vuốt phải lúc spring đang đóng (LB: để pager lùi trang). */
    private enum class Candidate { NONE, OPEN, CLOSE, GRAB }

    private var candidate = Candidate.NONE
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var baseProgress = 0f
    private var velocity: VelocityTracker? = null

    // D2: hoạt ảnh kết thúc là spring nhận vận tốc thả tay (trước là ValueAnimator thời lượng cố định, bỏ vận tốc: tay nhanh thì hoạt ảnh chậm hơn tay
    // ("đứng"), tay chậm thì nhanh hơn tay ("nhảy")). `settling` chỉ true từ lúc ta chủ động chạy spring tới lúc nó dừng; các lần `snapTo(progress)` để đồng bộ
    // giá trị đầu của spring không được tính là cập nhật.
    private var settling = false
    private var settleOpen = false
    private val spring = SpringProgress(
        AppLibraryMotion.SWIPE_SETTLE_SPRING,
        onUpdate = { if (settling) setProgress(it.coerceIn(0f, 1f)) },
        onSettled = {
            if (settling) {
                settling = false
                setProgress(if (settleOpen) 1f else 0f)
                settle(settleOpen)
            }
        }
    )

    val isAnimatingOrDragging: Boolean get() = dragging || settling

    fun onTouch(ev: MotionEvent): Result {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                candidate = Candidate.NONE
                if (dragging || host.width <= 0) return Result.PASS
                downX = ev.rawX
                downY = ev.rawY
                // D2 vòng 4: spring mở/đóng NGẮT ĐƯỢC như iOS. Chạm lúc spring còn chạy không bị bỏ qua nữa (trước: vuốt đóng cách vuốt mở < ~0,5 s bị nuốt, mọi
                // cú sau lệch một nhịp). Spring cứ chạy tiếp cho tới khi cử chỉ vượt slop (lúc đó mới bắt lấy tại `progress` hiện tại, xem TAKEOVER); chạm
                // nhẹ hay vuốt dọc thì không đụng gì tới spring nên Library không bao giờ kẹt giữa chừng.
                candidate = when {
                    settling -> if (host.canClose()) Candidate.GRAB else Candidate.NONE
                    isOpen -> if (host.canClose()) Candidate.CLOSE else Candidate.NONE
                    host.canOpen() -> Candidate.OPEN
                    else -> Candidate.NONE
                }
                if (candidate != Candidate.NONE) {
                    velocity?.recycle()
                    velocity = VelocityTracker.obtain().also { it.addMovement(ev) }
                    lastVx = 0f
                    lastMoveEventTime = 0L
                }
                return Result.PASS
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!dragging) cancelCandidate()
                return if (dragging) Result.CONSUME else Result.PASS
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    trackMove(ev)
                    setProgress((baseProgress - dir * (ev.rawX - downX) / host.width).coerceIn(0f, 1f))
                    return Result.CONSUME
                }
                if (candidate == Candidate.NONE) return Result.PASS
                trackMove(ev)
                // TV: dx theo trục mở (âm = về phía mở). Library: giữ nguyên dấu thật; Today: đảo dấu. abs(dx) không đổi.
                val dx = dir * (ev.rawX - downX)
                val dy = ev.rawY - downY
                when {
                    abs(dy) > slopPx && abs(dy) >= abs(dx) -> cancelCandidate() // dọc trội: không phải cử chỉ này
                    abs(dx) > slopPx && abs(dx) > abs(dy) -> {
                        if (candidate == Candidate.GRAB && !settling) {
                            // Spring đã dừng hẳn từ lúc DOWN tới giờ: không còn gì để bắt lấy, xử lý như cú vuốt mở/đóng thường (đúng chiều mới tiếp quản).
                            candidate = if (isOpen) Candidate.CLOSE else Candidate.OPEN
                        }
                        if (candidate == Candidate.GRAB && !settleOpen && dx > 0) {
                            // LB: spring đang ĐÓNG Library mà ngón vuốt tiếp sang phải = lùi trang (chủ dự án chốt 2026-11-12). Không bắt Library (trước:
                            // bắt lấy ở p vài phần trăm, kẹp về 0, pager bị hủy nên cú vuốt mất); trả cho pager, vốn đã nhận DOWN và các MOVE trước slop.
                            // Spring đóng chạy nốt (không cắt, không nhảy). Vuốt trái (mở lại) vẫn GRAB như FX7.
                            cancelCandidate()
                            return Result.PASS
                        }
                        val rightDirection = when (candidate) {
                            Candidate.OPEN -> dx < 0
                            Candidate.CLOSE -> dx > 0
                            else -> true // GRAB: cả hai chiều
                        }
                        if (!rightDirection) {
                            cancelCandidate() // sai chiều (vd ở trang cuối vuốt phải = lật về trang trước)
                        } else if (candidate == Candidate.GRAB && (if (dx < 0) !host.canOpen() else !host.canClose())) {
                            cancelCandidate() // điều kiện mở/đóng đổi sau lúc DOWN: để spring chạy tiếp
                        } else if (candidate == Candidate.CLOSE && !host.canClose()) {
                            cancelCandidate() // GF: màn nhóm vừa mở sau lúc DOWN
                        } else if (candidate == Candidate.OPEN && !host.canOpen()) {
                            // TL1 Task 9: điều kiện mở có thể đổi SAU lúc DOWN (nhấn giữ icon -> menu/phiên kéo xuất hiện): không cướp
                            // chuỗi chạm đó, để nó thành kéo icon bình thường.
                            cancelCandidate()
                        } else {
                            dragging = true
                            baseProgress = when (candidate) {
                                Candidate.OPEN -> 0f
                                Candidate.CLOSE -> 1f
                                else -> {
                                    // GRAB: dừng spring tại chỗ (không gọi onSettled), kéo tiếp từ `progress` hiện tại; thả tay sẽ chạy spring mới với vận tốc mới.
                                    settling = false
                                    spring.cancel()
                                    progress
                                }
                            }
                            candidate = Candidate.NONE
                            setProgress((baseProgress - dx / host.width).coerceIn(0f, 1f))
                            return Result.TAKEOVER
                        }
                    }
                }
                return Result.PASS
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) {
                    cancelCandidate()
                    return Result.PASS
                }
                dragging = false
                velocity?.addMovement(ev)
                velocity?.computeCurrentVelocity(1000)
                var vx = dir * (velocity?.xVelocity ?: 0f) // TV: theo trục mở (lastVx cũng đổi dấu ở trackMove)
                // D2 (vòng 2): bộ đo vận tốc coi con trỏ đã dừng và xóa lịch sử khi hai mẫu cách nhau >= 40 ms; luồng chính kẹt lúc Library hiện lần đầu
                // (dựng ô) kèm bộ bơm sự kiện dùng giờ lúc bơm làm mất hết mẫu: vx = 0 dù ngón vừa lướt nhanh. Nếu mẫu cuối còn "tươi" (UP sát MOVE cuối,
                // ngón không đứng lại) mà bộ đo trả 0 thì dùng vận tốc đo được ở lần MOVE trước đó.
                if (vx == 0f && ev.eventTime - lastMoveEventTime < FRESH_MOVE_MS) vx = lastVx
                velocity?.recycle()
                velocity = null
                val open = when {
                    ev.actionMasked == MotionEvent.ACTION_CANCEL -> progress > 0.5f
                    vx <= -flingVelocityPxPerSec -> true
                    vx >= flingVelocityPxPerSec -> false
                    else -> progress > 0.5f
                }
                animateTo(open, startVelocityPerSec = -vx / host.width) // p tăng khi vuốt về phía mở (vx < 0 trên trục mở)
                return Result.CONSUME
            }
        }
        return if (dragging) Result.CONSUME else Result.PASS
    }

    /** Đặt thẳng trạng thái (khôi phục sau recreate, hoặc đóng do trạng thái khác chặn). */
    fun snapTo(open: Boolean, animate: Boolean) {
        if (dragging) return
        if (!animate) {
            settling = false
            spring.cancel()
            setProgress(if (open) 1f else 0f)
            isOpen = open
            host.onSettled(open)
        } else if (open != isOpen || progress != (if (open) 1f else 0f)) {
            animateTo(open)
        }
    }

    private var lastVx = 0f
    private var lastMoveEventTime = 0L

    /** Đưa MOVE vào bộ đo vận tốc và nhớ vận tốc khác 0 gần nhất (xem `ACTION_UP`). Bộ đo được nuôi từ `ACTION_DOWN` (khi chọn ứng viên) tới hết cử chỉ. */
    private fun trackMove(ev: MotionEvent) {
        val tracker = velocity ?: return
        tracker.addMovement(ev)
        tracker.computeCurrentVelocity(1000)
        val v = dir * tracker.xVelocity
        if (v != 0f) lastVx = v
        lastMoveEventTime = ev.eventTime
    }

    private fun cancelCandidate() {
        candidate = Candidate.NONE
        velocity?.recycle()
        velocity = null
    }

    /**
     * Chạy spring tới trạng thái [open] (D2). [startVelocityPerSec]: vận tốc của `p` lúc thả tay (đơn vị p mỗi giây, dương = về phía mở), kẹp ±[MAX_RELEASE_P_PER_SEC]
     * để cú vuốt rất nhanh không đẩy `p` quá xa (đã kẹp `0..1` khi áp, nên chỉ ảnh hưởng thời điểm tới đích).
     *
     * TR: Library đi theo ngón tay nên kiểu chuyển cảnh không đổi hình trượt; chỉ áp tốc độ (độ cứng spring) và "Không có" (nhảy thẳng tới đích).
     */
    private fun animateTo(open: Boolean, startVelocityPerSec: Float = 0f) {
        val target = if (open) 1f else 0f
        settleOpen = open
        settling = false
        if (TransitionMotion.isInstant) {
            spring.cancel()
            setProgress(target)
            settle(open)
            return
        }
        spring.snapTo(progress) // đồng bộ giá trị đầu của spring với p hiện tại (callback bị bỏ qua vì settling = false)
        settling = true
        spring.animateTo(
            target,
            startVelocityPerSec.coerceIn(-MAX_RELEASE_P_PER_SEC, MAX_RELEASE_P_PER_SEC),
            newSpec = TransitionMotion.spring(AppLibraryMotion.SWIPE_SETTLE_SPRING)
        )
    }

    private fun settle(open: Boolean) {
        isOpen = open
        host.onSettled(open)
    }

    private fun setProgress(p: Float) {
        if (p == progress) return
        progress = p
        host.onProgress(p)
    }

    private companion object {
        /** Trần vận tốc thả tay đưa vào spring (p mỗi giây; 12 ≈ 13.000 px/s trên màn 1080 px). */
        const val MAX_RELEASE_P_PER_SEC = 12f

        /** UP cách MOVE cuối dưới chừng này (ms) thì ngón chưa đứng lại: tin vận tốc gần nhất khi bộ đo trả 0. */
        const val FRESH_MOVE_MS = 24L
    }
}
