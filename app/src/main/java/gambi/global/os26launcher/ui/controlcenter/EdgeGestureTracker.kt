package gambi.global.os26launcher.ui.controlcenter

import android.view.MotionEvent
import android.view.VelocityTracker
import gambi.global.os26launcher.domain.model.controlcenter.EdgePosition
import kotlin.math.abs

/**
 * G3d: nhận cử chỉ kéo trên dải mép (RE mục 2.3: app mẫu khóa trục sau 11 px rồi gửi quãng kéo mỗi khung). Dùng toạ độ màn hình
 * (rawX/rawY) vì cửa sổ dải mép rất nhỏ, ngón tay đi ra ngoài nó ngay; chuỗi chạm vẫn thuộc cửa sổ đã nhận ACTION_DOWN.
 *
 * Dải ở mép trên: kéo xuống. Dải ở cạnh trái/phải ([position]): kéo xuống hoặc kéo ngang vào trong màn hình; quãng kéo là trục
 * đã khóa. Hướng khác trước khi vượt [slopPx] thì bỏ cả chuỗi (không mở panel).
 *
 * "Hết cỡ" (`atEnd`): ngón tay vào vùng [endZonePx] sát mép đối diện theo hướng kéo (đáy màn hình khi kéo xuống, mép kia khi kéo
 * ngang); [screenSize] trả (rộng, cao) màn hình thật, đọc mỗi lần vì xoay màn đổi kích thước.
 */
class EdgeGestureTracker(
    private val slopPx: Float,
    private val endZonePx: Float,
    private val screenSize: () -> Pair<Int, Int>,
    private val listener: Listener
) {
    interface Listener {
        fun onPullStart()

        /** [distance] ≥ 0 px tính từ lúc vượt slop (panel không giật một đoạn bằng slop); [atEnd]: đã kéo hết cỡ. */
        fun onPull(distance: Float, atEnd: Boolean)

        /** [velocity] px/s theo hướng kéo, dương = đi vào. */
        fun onPullEnd(distance: Float, velocity: Float, atEnd: Boolean)

        fun onPullCancel()
    }

    private enum class Phase { IDLE, DOWN, PULLING, IGNORED }
    private enum class Axis { DOWN, RIGHT, LEFT }

    var position: EdgePosition = EdgePosition.TOP_RIGHT

    private var phase = Phase.IDLE
    private var axis = Axis.DOWN
    private var downX = 0f
    private var downY = 0f
    private var start = 0f
    private var velocity: VelocityTracker? = null

    fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            recycle()
            velocity = VelocityTracker.obtain()
        }
        velocity?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                phase = Phase.DOWN
                downX = ev.rawX
                downY = ev.rawY
            }

            MotionEvent.ACTION_MOVE -> when (phase) {
                Phase.DOWN -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (abs(dx) > slopPx || abs(dy) > slopPx) {
                        val picked = when {
                            dy > 0f && dy >= abs(dx) -> Axis.DOWN
                            position == EdgePosition.LEFT_SIDE && dx > 0f && dx > abs(dy) -> Axis.RIGHT
                            position == EdgePosition.RIGHT_SIDE && dx < 0f && -dx > abs(dy) -> Axis.LEFT
                            else -> null
                        }
                        if (picked != null) {
                            axis = picked
                            phase = Phase.PULLING
                            start = coord(ev)
                            listener.onPullStart()
                            listener.onPull(0f, atEnd(ev))
                        } else {
                            phase = Phase.IGNORED
                        }
                    }
                }

                Phase.PULLING -> listener.onPull(distance(ev), atEnd(ev))
                else -> Unit
            }

            MotionEvent.ACTION_UP -> {
                if (phase == Phase.PULLING) {
                    val vt = velocity
                    vt?.computeCurrentVelocity(1000)
                    val v = when (axis) {
                        Axis.DOWN -> vt?.yVelocity ?: 0f
                        Axis.RIGHT -> vt?.xVelocity ?: 0f
                        Axis.LEFT -> -(vt?.xVelocity ?: 0f)
                    }
                    listener.onPullEnd(distance(ev), v, atEnd(ev))
                }
                phase = Phase.IDLE
                recycle()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (phase == Phase.PULLING) listener.onPullCancel()
                phase = Phase.IDLE
                recycle()
            }
        }
        return true
    }

    private fun coord(ev: MotionEvent) = if (axis == Axis.DOWN) ev.rawY else ev.rawX

    private fun distance(ev: MotionEvent): Float {
        val d = coord(ev) - start
        return (if (axis == Axis.LEFT) -d else d).coerceAtLeast(0f)
    }

    private fun atEnd(ev: MotionEvent): Boolean {
        val (w, h) = screenSize()
        return when (axis) {
            Axis.DOWN -> ev.rawY >= h - endZonePx
            Axis.RIGHT -> ev.rawX >= w - endZonePx
            Axis.LEFT -> ev.rawX <= endZonePx
        }
    }

    private fun recycle() {
        velocity?.recycle()
        velocity = null
    }
}
