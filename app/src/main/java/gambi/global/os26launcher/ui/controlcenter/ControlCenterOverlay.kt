package gambi.global.os26launcher.ui.controlcenter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.content.ContextCompat
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.controlcenter.EdgeConfig
import gambi.global.os26launcher.domain.model.controlcenter.EdgePosition
import gambi.global.os26launcher.ui.controlcenter.compose.ControlCenterPanel
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * G3d: hai cửa sổ `TYPE_APPLICATION_OVERLAY` của Trung tâm điều khiển (RE mục 2):
 * - **dải mép** [EdgeTriggerView]: luôn có khi tính năng bật; `NOT_FOCUSABLE | NOT_TOUCH_MODAL` nên chạm ngoài dải vẫn tới app bên
 *   dưới và không lấy focus (giống flags 808 của app mẫu). Vị trí và cỡ theo [EdgeConfig] (RE mục 5.4);
 * - **panel** [ControlCenterPanel] (Compose, JS7b): toàn màn, CHỈ thêm khi bắt đầu kéo và gỡ khi đóng xong; focusable để nhận phím Back.
 *
 * Mọi thao tác WindowManager bọc `try/catch`: ROM có thể thu quyền vẽ đè bất cứ lúc nào (BadTokenException, IllegalArgument).
 * Context là của service: không giữ Activity nào.
 */
internal class ControlCenterOverlay(
    private val context: Context,
    private val host: Host
) {
    interface Host {
        /** Panel vừa được thêm (bắt đầu kéo mở): đọc trạng thái, nạp nền, đăng ký theo dõi. */
        fun onPanelOpening()

        /** Panel vừa gỡ: huỷ theo dõi. */
        fun onPanelClosed()
    }

    private enum class Phase { CLOSED, PULLING, OPEN, CLOSING }

    private val wm: WindowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val flingMinDistance = context.resources.getDimension(R.dimen.cc_fling_min_distance)
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()

    val panel = ControlCenterPanel(context) // JS7b: panel Compose; cửa sổ thêm/gỡ [ControlCenterPanel.view]
    private val motion = ControlCenterMotion(panel, context.resources.getDimension(R.dimen.cc_reveal_threshold))

    /** Hất ngược (về phía mép) nhanh hơn mức này thì huỷ mở (REF a38f769: rung tay nhẹ lúc nhấc ngón không tính). */
    private val cancelFlingVelocity = CANCEL_FLING_DP_PER_S * density
    private val tracker = EdgeGestureTracker(
        ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
        PULL_END_ZONE_DP * density,
        { screenSize() },
        EdgeListener()
    )
    private val edge = EdgeTriggerView(context, tracker)

    var edgeConfig = EdgeConfig()
        set(value) {
            if (field == value) return
            field = value
            edge.apply(value)
            relayout()
        }

    /** RE mục 5.4: `vibration_control`. */
    var vibrateOnOpen = true

    private var edgeAttached = false
    private var panelAttached = false
    private var phase = Phase.CLOSED
    private var receiverRegistered = false

    /** Nút Home / tắt màn hình khi panel đang mở thì đóng (D4). `homekey` qua CLOSE_SYSTEM_DIALOGS là suy luận, kiểm ở Task 4. */
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            closeNow()
        }
    }

    // CG: blur xuyên cửa sổ cho chế độ nền "Không" (API 31+). Giữ params của panel để đổi bán kính theo nhịp mở/đóng.
    private val panelLayoutParams = panelParams()
    private val maxBlurBehindPx = context.resources.getDimensionPixelSize(R.dimen.cc_blur_behind_radius)
    private var appliedBlurPx = 0
    private var currentBackgroundAlpha = 0f
    private val blurEnabledListener: Consumer<Boolean>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Consumer { enabled -> onCrossWindowBlurChanged(enabled) } else null

    init {
        edge.apply(edgeConfig)
        panel.onBackgroundAlpha = { alpha -> applyBlurBehind(alpha) }
        panel.dismissDrag = object : ControlCenterPanel.DismissDrag {
            override fun onDismissDrag(dy: Float) {
                if (phase == Phase.OPEN) motion.dragDismiss(dy)
            }

            override fun onDismissRelease(dy: Float, velocityY: Float) {
                if (phase != Phase.OPEN) return
                val fling = velocityY > minFlingVelocity * DISMISS_FLING_FACTOR
                if (dy >= motion.dismissDistance() || (fling && dy > 0f)) close()
                else motion.settleOpen { panel.requestFocus() }
            }
        }
    }

    private fun onCrossWindowBlurChanged(enabled: Boolean) {
        panel.blurBehindAvailable = enabled
        applyBlurBehind(currentBackgroundAlpha, force = true)
    }

    /**
     * CG: bán kính làm mờ màn phía sau = độ đậm nền × [maxBlurBehindPx]. Chỉ gọi `updateViewLayout` khi bán kính đổi ít nhất
     * [BLUR_STEP_PX] hoặc chạm 0 / tối đa, không phải mỗi khung kéo.
     */
    private fun applyBlurBehind(alpha: Float, force: Boolean = false) {
        currentBackgroundAlpha = alpha
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val target = if (panel.wantsBlurBehind && panel.blurBehindAvailable) {
            (alpha.coerceIn(0f, 1f) * maxBlurBehindPx).roundToInt()
        } else 0
        if (!force) {
            if (target == appliedBlurPx) return
            val atEnd = target == 0 || target == maxBlurBehindPx
            if (!atEnd && abs(target - appliedBlurPx) < BLUR_STEP_PX) return
        }
        appliedBlurPx = target
        panelLayoutParams.blurBehindRadius = target
        if (!panelAttached) return
        try {
            wm.updateViewLayout(panel.view, panelLayoutParams)
        } catch (e: RuntimeException) {
            // cửa sổ đã bị hệ thống gỡ
        }
    }

    val isPanelOpen get() = panelAttached

    fun showEdge() {
        if (edgeAttached) return
        try {
            wm.addView(edge, edgeParams())
            edgeAttached = true
        } catch (e: RuntimeException) {
            edgeAttached = false // chưa có quyền vẽ đè hoặc ROM chặn: thử lại ở lần refresh kế
        }
    }

    fun hideAll() {
        closeNow()
        if (!edgeAttached) return
        try {
            wm.removeViewImmediate(edge)
        } catch (e: RuntimeException) {
            // đã bị hệ thống gỡ
        }
        edgeAttached = false
    }

    /**
     * JS7b: gỡ mọi cửa sổ rồi huỷ composition của panel (vòng đời Compose về DESTROYED). Service gọi khi huỷ presenter (ngắt
     * listener, service bị huỷ, dựng lại vì đổi ngôn ngữ); sau lệnh này overlay không dùng lại được.
     */
    fun destroy() {
        hideAll()
        panel.destroy()
    }

    /** Xoay màn, đổi cỡ cửa sổ hoặc đổi cấu hình dải mép: đặt lại dải theo kích thước mới. */
    fun relayout() {
        if (edgeAttached) {
            try {
                wm.updateViewLayout(edge, edgeParams())
            } catch (e: RuntimeException) {
                edgeAttached = false
            }
        }
        panel.topInset = statusBarHeight()
        panel.bottomInset = navigationBarHeight()
    }

    /** Đóng có hoạt ảnh (chạm vùng trống, Back). */
    fun close() {
        if (!panelAttached || phase == Phase.CLOSING) return
        phase = Phase.CLOSING
        motion.settleClosed { detachPanel() }
    }

    /** Đóng ngay rồi chạy [then]: dùng trước khi mở một màn khác (app mẫu cũng gỡ panel trước rồi mới startActivity). */
    fun closeNow(then: (() -> Unit)? = null) {
        if (panelAttached) {
            motion.cancel()
            detachPanel()
        }
        then?.invoke()
    }

    private fun attachPanel(): Boolean {
        if (panelAttached) return true
        panel.topInset = statusBarHeight()
        panel.bottomInset = navigationBarHeight()
        panel.resetForOpen()
        motion.reset()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            panel.blurBehindAvailable = wm.isCrossWindowBlurEnabled
            appliedBlurPx = 0
            panelLayoutParams.blurBehindRadius = 0
        }
        try {
            wm.addView(panel.view, panelLayoutParams)
            panelAttached = true
        } catch (e: RuntimeException) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurEnabledListener?.let { wm.addCrossWindowBlurEnabledListener(context.mainExecutor, it) }
        }
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                @Suppress("DEPRECATION") // chỉ nhận, không gửi; xem closeReceiver
                addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            ContextCompat.registerReceiver(context, closeReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            receiverRegistered = true
        }
        host.onPanelOpening()
        return true
    }

    private fun detachPanel() {
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(closeReceiver)
            } catch (e: IllegalArgumentException) {
                // đã gỡ
            }
            receiverRegistered = false
        }
        if (panelAttached) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurEnabledListener?.let { wm.removeCrossWindowBlurEnabledListener(it) }
            }
            try {
                wm.removeViewImmediate(panel.view)
            } catch (e: RuntimeException) {
                // đã bị hệ thống gỡ
            }
            panelAttached = false
        }
        phase = Phase.CLOSED
        host.onPanelClosed()
    }

    private inner class EdgeListener : EdgeGestureTracker.Listener {
        private var wasAtEnd = false

        override fun onPullStart() {
            if (phase == Phase.CLOSING) closeNow()
            wasAtEnd = false
            phase = if (attachPanel()) Phase.PULLING else Phase.CLOSED
        }

        override fun onPull(distance: Float, atEnd: Boolean) {
            if (phase != Phase.PULLING) return
            // Vào vùng hết cỡ: rung nhẹ một lần, khối ẩn đi để báo trước thả tay sẽ đóng.
            if (atEnd && !wasAtEnd && vibrateOnOpen) panel.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            wasAtEnd = atEnd
            motion.dragFromEdge(distance, collapsed = atEnd)
        }

        override fun onPullEnd(distance: Float, velocity: Float, atEnd: Boolean) {
            if (phase != Phase.PULLING) return
            // D3: quá 1/5 chiều cao, hoặc fling vào đủ xa thì mở; fling ngược lại thì đóng.
            // REF a38f769: kéo hết cỡ xuống đáy thì đóng; chỉ hất ngược dứt khoát (> CANCEL_FLING_DP_PER_S) mới huỷ.
            val open = when {
                atEnd -> false
                velocity < -cancelFlingVelocity -> false
                velocity > minFlingVelocity && distance >= flingMinDistance -> true
                // CG: thả tay khi panel đã hiện một nửa là mở (chủ dự án 2026-11-20); trước là 1/5 chiều cao màn.
                else -> distance >= motion.shownDistance() * OPEN_SHOWN_PART
            }
            if (open) {
                phase = Phase.OPEN
                if (vibrateOnOpen) panel.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                motion.settleOpen { panel.requestFocus() }
            } else {
                close()
            }
        }

        override fun onPullCancel() {
            if (phase == Phase.PULLING) close()
        }
    }

    private fun edgeParams(): WindowManager.LayoutParams {
        val (sw, sh) = screenSize()
        val thick = (edgeConfig.thicknessDp * density).toInt()
        val vertical = edgeConfig.position == EdgePosition.LEFT_SIDE || edgeConfig.position == EdgePosition.RIGHT_SIDE
        val w = if (vertical) thick else (sw * edgeConfig.lengthFraction).toInt()
        val h = if (vertical) (sh * edgeConfig.lengthFraction).toInt() else thick
        return WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or when (edgeConfig.position) {
                EdgePosition.TOP_RIGHT, EdgePosition.RIGHT_SIDE -> Gravity.END
                EdgePosition.TOP_LEFT, EdgePosition.LEFT_SIDE -> Gravity.START
            }
            // D2: dải trên nằm ngay dưới thanh trạng thái (chạm trong thanh trạng thái vẫn là của hệ thống). Dải cạnh lệch xuống
            // theo offsetFraction, không nhỏ hơn đáy thanh trạng thái.
            y = if (vertical) maxOf(statusBarHeight(), (sh * edgeConfig.offsetFraction).toInt()) else statusBarHeight()
            title = "OS26 Control Center edge"
            applyCutoutMode()
        }
    }

    private fun panelParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "OS26 Control Center"
        applyCutoutMode()
        // Như iOS: nền panel phủ kín cả dải thanh điều hướng (API 30+ cửa sổ overlay mặc định chừa inset hệ thống, để lộ ảnh nền sắc ở đáy).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
        // CG: bật cờ một lần; bán kính 0 = không mờ, đổi theo nhịp mở ở applyBlurBehind.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            blurBehindRadius = 0
        }
    }

    private fun WindowManager.LayoutParams.applyCutoutMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            return b.width() to b.height()
        }
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        return dm.widthPixels to dm.heightPixels
    }

    private fun statusBarHeight(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return wm.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.statusBars()).top
        }
        return systemDimen("status_bar_height", 24)
    }

    private fun navigationBarHeight(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return wm.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
        }
        return systemDimen("navigation_bar_height", 48)
    }

    /** API < 30 không có WindowMetrics: dimen hệ thống, dự phòng theo chuẩn AOSP. */
    private fun systemDimen(name: String, fallbackDp: Int): Int {
        @Suppress("DiscouragedApi", "InternalInsetResource")
        val id = context.resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else (fallbackDp * density).toInt()
    }

    private companion object {
        const val OPEN_SHOWN_PART = 0.5f

        /** Hất ngược nhanh hơn mức này (dp/s) mới huỷ mở; hằng chỉnh tay (REF a38f769). */
        const val CANCEL_FLING_DP_PER_S = 1000f

        /** Vùng sát mép đối diện: kéo tới đây là "hết cỡ", thả tay thì đóng (REF `cc_pull_end_zone` 96dp). */
        const val PULL_END_ZONE_DP = 96f

        /** CG: vuốt xuống trên trang chính nhanh hơn ngưỡng fling × hệ số này thì đóng dù quãng ngắn. */
        const val DISMISS_FLING_FACTOR = 4f
        const val BLUR_STEP_PX = 3
    }
}
