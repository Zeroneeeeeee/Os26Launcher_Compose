package gambi.global.os26launcher.ui.home.compose.library

import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import gambi.global.os26launcher.domain.model.AppItem

/**
 * LD Task 3 — nhấn giữ một app trong App Library, nhận ở `HomeActivity.dispatchTouchEvent` (thấy mọi MotionEvent, kể cả sau khi Library đóng và
 * bị gỡ khỏi composition giữa cử chỉ — lý do không dùng `pointerInput` của ô).
 *
 * - DOWN trên một app ([HomeLibraryState.appAt]) → ứng viên; sự kiện vẫn đi tiếp (bấm, cuộn, vuốt đóng Library như cũ).
 * - Trôi quá touch slop trước `longPressTimeout` → bỏ ứng viên.
 * - Đủ giờ mà chưa trôi → [Host.onHold]: rung, vào chế độ sửa, Activity gửi ACTION_CANCEL xuống cây view và hai controller vuốt (cú bấm không thành,
 *   danh sách không cuộn). Từ đây nuốt mọi sự kiện của cử chỉ ([Result.CONSUME]).
 * - Đang giữ mà ngón tay ra khỏi vùng ô nở thêm [EXIT_MARGIN_DP] → [Host.onDragOut] một lần (Task 4 bàn giao sang Home); sự kiện sau đó đi qua
 *   [Host.onDragMove] / [Host.onDragEnd]. Nhấc tay khi chưa ra khỏi ô → chỉ ở lại chế độ sửa.
 */
internal class LibraryDragGesture(
    private val state: HomeLibraryState,
    private val host: Host,
    config: ViewConfiguration,
    density: Float
) {
    interface Host {
        /** Đủ giờ giữ yên trên [app]: rung + chế độ sửa + huỷ chạm của cây view. [down]: sự kiện gốc để dựng ACTION_CANCEL. */
        fun onHold(app: AppItem, down: MotionEvent)

        /** Ngón tay rời ô đang giữ: bắt đầu mang [app] ra Home tại ([x], [y]) (toạ độ cửa sổ); [icon] = khung icon trong Library. */
        fun onDragOut(app: AppItem, icon: RectF, x: Float, y: Float)

        fun onDragMove(x: Float, y: Float)

        /** [cancelled]: cử chỉ bị huỷ (ACTION_CANCEL), không phải nhấc tay. */
        fun onDragEnd(x: Float, y: Float, cancelled: Boolean)
    }

    enum class Result { PASS, CONSUME }

    private enum class Phase { IDLE, CANDIDATE, HELD, DRAGGING }

    private val slop = config.scaledTouchSlop.toFloat()
    private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
    private val exitMargin = EXIT_MARGIN_DP * density
    private val handler = Handler(Looper.getMainLooper())

    private var phase = Phase.IDLE
    private var target: HomeLibraryState.AppHit? = null
    private var pointerId = -1
    private var downX = 0f
    private var downY = 0f
    private var down: MotionEvent? = null

    private val holdRunnable = Runnable {
        val t = target ?: return@Runnable
        val ev = down ?: return@Runnable
        if (phase != Phase.CANDIDATE) return@Runnable
        phase = Phase.HELD
        host.onHold(t.app, ev)
    }

    /** Đang nuốt cử chỉ (đã giữ hoặc đang kéo ra). */
    val isActive: Boolean get() = phase == Phase.HELD || phase == Phase.DRAGGING

    fun onTouch(ev: MotionEvent): Result {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                reset()
                val hit = state.appAt(ev.x, ev.y) ?: return Result.PASS
                target = hit
                pointerId = ev.getPointerId(0)
                downX = ev.x
                downY = ev.y
                down = MotionEvent.obtain(ev)
                phase = Phase.CANDIDATE
                handler.postDelayed(holdRunnable, longPressMs)
                return Result.PASS
            }

            MotionEvent.ACTION_MOVE -> {
                val i = ev.findPointerIndex(pointerId)
                if (i < 0) return if (isActive) Result.CONSUME else Result.PASS
                val x = ev.getX(i)
                val y = ev.getY(i)
                when (phase) {
                    Phase.CANDIDATE -> if (dist(x, y) > slop) reset()
                    Phase.HELD -> {
                        val t = target
                        if (t != null && !t.hit.expanded(exitMargin).contains(x, y)) {
                            phase = Phase.DRAGGING
                            host.onDragOut(t.app, RectF(t.icon()), x, y)
                        }
                    }

                    Phase.DRAGGING -> host.onDragMove(x, y)
                    Phase.IDLE -> Unit
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Ngón đang giữ nhấc mà ngón khác còn đặt: coi như nhấc tay (C18 của Home: chỉ theo ngón đầu).
                if (ev.getPointerId(ev.actionIndex) == pointerId) return finish(ev.getX(ev.actionIndex), ev.getY(ev.actionIndex), cancelled = false)
            }

            MotionEvent.ACTION_UP -> return finish(ev.x, ev.y, cancelled = false)
            MotionEvent.ACTION_CANCEL -> return finish(ev.x, ev.y, cancelled = true)
        }
        return if (isActive) Result.CONSUME else Result.PASS
    }

    private fun finish(x: Float, y: Float, cancelled: Boolean): Result {
        val wasActive = isActive
        if (phase == Phase.DRAGGING) host.onDragEnd(x, y, cancelled)
        reset()
        return if (wasActive) Result.CONSUME else Result.PASS
    }

    private fun reset() {
        handler.removeCallbacks(holdRunnable)
        phase = Phase.IDLE
        target = null
        pointerId = -1
        down?.recycle()
        down = null
    }

    private fun dist(x: Float, y: Float): Float = kotlin.math.hypot(x - downX, y - downY)

    private fun RectF.expanded(by: Float) = RectF(left - by, top - by, right + by, bottom + by)

    private companion object {
        /** Ra khỏi ô (nở thêm chừng này) mới tính là kéo ra; rung tay trong ô vẫn chỉ là giữ. */
        const val EXIT_MARGIN_DP = 16f
    }
}
