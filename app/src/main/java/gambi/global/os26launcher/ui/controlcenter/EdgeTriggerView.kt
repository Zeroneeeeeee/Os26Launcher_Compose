package gambi.global.os26launcher.ui.controlcenter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.MotionEvent
import android.view.View
import gambi.global.os26launcher.domain.model.controlcenter.EdgeConfig
import gambi.global.os26launcher.domain.model.controlcenter.EdgePosition

/**
 * G3d: dải chạm ở mép màn hình (cửa sổ overlay riêng, D2; vị trí, độ dài, độ dày và màu đổi được như app mẫu, RE mục 5.4).
 * Vẽ kín cả dải (đúng bằng vùng nhận chạm của cửa sổ) thành viên thuốc bo tròn để người dùng thấy chính xác chỗ vuốt; mọi
 * chạm chuyển cho [tracker].
 */
@SuppressLint("ViewConstructor")
class EdgeTriggerView(context: Context, private val tracker: EdgeGestureTracker) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var vertical = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO // TalkBack mở panel qua Cài đặt của launcher, không qua dải mép
    }

    fun apply(config: EdgeConfig) {
        paint.color = config.color
        vertical = config.position == EdgePosition.LEFT_SIDE || config.position == EdgePosition.RIGHT_SIDE
        tracker.position = config.position
        invalidate()
        requestLayout() // cập nhật vùng loại trừ cử chỉ
    }

    /**
     * Dải ở cạnh trái/phải: với điều hướng cử chỉ (API 29+) vuốt vào từ cạnh là cử chỉ Back của hệ thống, sẽ lấy mất chuỗi chạm.
     * Xin hệ thống loại trừ vùng dải; hệ thống chỉ nhận tối đa 200 dp mỗi cạnh nên kẹp quanh giữa dải. Phần dải ngoài vùng
     * loại trừ vẫn mở được bằng vuốt xuống.
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        systemGestureExclusionRects = if (vertical) {
            val max = (MAX_EXCLUSION_DP * resources.displayMetrics.density).toInt()
            val h = minOf(height, max)
            val top0 = (height - h) / 2
            listOf(Rect(0, top0, width, top0 + h))
        } else emptyList()
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        val r = minOf(width, height) / 2f
        canvas.drawRoundRect(rect, r, r, paint)
    }

    @SuppressLint("ClickableViewAccessibility") // dải mép chỉ nhận cử chỉ kéo, không có hành động bấm
    override fun onTouchEvent(event: MotionEvent): Boolean = tracker.onTouchEvent(event)

    private companion object {
        const val MAX_EXCLUSION_DP = 200
    }
}
