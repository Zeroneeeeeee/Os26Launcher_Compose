package gambi.global.os26launcher.ui.controlcenter

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * G3d: Night Shift = một cửa sổ overlay phủ màu hổ phách lên toàn màn hình (RE mục 5.4: cửa sổ thứ tư của app mẫu, flags 824 =
 * không chạm, không focus). Không đổi nhiệt độ màu thật của màn hình (app thường không làm được).
 *
 * Từ Android 12, chạm chỉ xuyên qua cửa sổ overlay khi độ mờ đục của CỬA SỔ (`LayoutParams.alpha`) ≤ 0,8
 * (`InputManager.getMaximumObscuringOpacityForTouch`). Vì vậy màu vẽ đục hoàn toàn và độ đậm đặt bằng `alpha` của cửa sổ, kẹp
 * dưới [MAX_ALPHA].
 */
internal class NightShiftOverlay(private val context: Context) {

    private val wm: WindowManager = context.getSystemService(WindowManager::class.java)
    private val view = View(context).apply { setBackgroundColor(AMBER) }
    private var attached = false

    /** [warmth] 0..1. Gọi lại khi đổi độ ấm thì chỉ cập nhật alpha. */
    fun show(warmth: Float) {
        val params = params(warmth)
        try {
            if (attached) wm.updateViewLayout(view, params) else {
                wm.addView(view, params)
                attached = true
            }
        } catch (e: RuntimeException) {
            attached = false // chưa có quyền vẽ đè hoặc ROM chặn
        }
    }

    fun hide() {
        if (!attached) return
        try {
            wm.removeViewImmediate(view)
        } catch (e: RuntimeException) {
            // đã bị hệ thống gỡ
        }
        attached = false
    }

    val isShowing get() = attached

    private fun params(warmth: Float) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        alpha = MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * warmth.coerceIn(0f, 1f)
        title = "OS26 Night Shift"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private companion object {
        /** Màu mặc định của app mẫu là #40ffd925 (vàng ấm, alpha 25%). Ta lấy cam ấm hơn một chút cho giống Night Shift. */
        val AMBER = Color.rgb(255, 170, 60)
        const val MIN_ALPHA = 0.08f
        const val MAX_ALPHA = 0.45f
    }
}
