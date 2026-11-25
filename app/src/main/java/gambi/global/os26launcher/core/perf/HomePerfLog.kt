package gambi.global.os26launcher.core.perf

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import gambi.global.os26launcher.BuildConfig

/**
 * SW Task 1 — log ĐO độ mượt khi vuốt trang Home. Chỉ chạy ở bản `debug` và `perf` ([enabled]); bản `release` không làm gì.
 *
 * - Khung chậm (tag [TAG_FRAME]): nghe `Window.addOnFrameMetricsAvailableListener` trên luồng riêng, log khung có tổng thời gian vượt
 *   [SLOW_FACTOR] lần chu kỳ vsync, kèm từng pha (input, animation, layout/measure, draw, sync, GPU) và ngữ cảnh: đang cuộn pager không,
 *   trang hiện tại, trang nào vừa dựng trong [RECENT_MS] trước đó.
 * - Dựng trang (tag [TAG_PAGE]): `HomeScreen` báo mỗi lần một trang của pager được dựng lần đầu ([pageComposed]), kèm thời gian dựng xấp xỉ
 *   (từ lúc bắt đầu dựng nội dung trang tới `SideEffect` của nó).
 *
 * Đọc: `adb logcat -s SwFrame SwPage` (hoặc `tools/perf/measure-home.ps1`, lưu logcat mỗi lượt đo).
 */
object HomePerfLog {
    const val TAG_FRAME = "SwFrame"
    const val TAG_PAGE = "SwPage"
    const val TAG_DOCK = "SwDock"
    private const val SLOW_FACTOR = 1.5f
    private const val RECENT_MS = 300L

    val enabled: Boolean = BuildConfig.DEBUG || BuildConfig.PERF_SWITCHES

    // Ngữ cảnh do HomeScreen cập nhật trên main thread; luồng đo chỉ đọc (volatile, không cần chính xác tuyệt đối).
    @Volatile var scrolling: Boolean = false
    @Volatile var currentPage: Int = 0
    @Volatile private var lastComposedPage: Int = -1
    @Volatile private var lastComposedAtMs: Long = 0L

    private var thread: HandlerThread? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null

    /** Gọi ở `onResume`. */
    fun attach(activity: Activity) {
        if (!enabled || listener != null) return
        val t = HandlerThread("SwFrameMetrics").also { it.start() }
        thread = t
        @Suppress("DEPRECATION")
        val refresh = activity.windowManager.defaultDisplay.refreshRate.takeIf { it > 1f } ?: 60f
        val slowNs = (1_000_000_000f / refresh * SLOW_FACTOR).toLong()
        val l = Window.OnFrameMetricsAvailableListener { _, m, _ ->
            val total = m.getMetric(FrameMetrics.TOTAL_DURATION)
            if (total < slowNs) return@OnFrameMetricsAvailableListener
            val gpu = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) m.getMetric(FrameMetrics.GPU_DURATION) else -1L
            val recent = lastComposedPage.takeIf { SystemClock.uptimeMillis() - lastComposedAtMs <= RECENT_MS }
            Log.i(
                TAG_FRAME,
                "slow total=${ms(total)} input=${ms(m.getMetric(FrameMetrics.INPUT_HANDLING_DURATION))} " +
                    "anim=${ms(m.getMetric(FrameMetrics.ANIMATION_DURATION))} layout=${ms(m.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION))} " +
                    "draw=${ms(m.getMetric(FrameMetrics.DRAW_DURATION))} sync=${ms(m.getMetric(FrameMetrics.SYNC_DURATION))} " +
                    "issue=${ms(m.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION))} swap=${ms(m.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION))} " +
                    "gpu=${if (gpu >= 0) ms(gpu) else "-"} scrolling=$scrolling page=$currentPage recentPageComposed=${recent ?: "-"} " +
                    "refresh=${refresh.toInt()}"
            )
        }
        listener = l
        activity.window.addOnFrameMetricsAvailableListener(l, Handler(t.looper))
    }

    /** Gọi ở `onPause`. */
    fun detach(activity: Activity) {
        val l = listener ?: return
        try {
            activity.window.removeOnFrameMetricsAvailableListener(l)
        } catch (e: IllegalArgumentException) {
            // chưa gắn: bỏ qua
        }
        listener = null
        thread?.quitSafely()
        thread = null
    }

    /** Một trang của pager vừa được dựng lần đầu (vào composition); [composeMs] = thời gian dựng xấp xỉ. Main thread. */
    fun pageComposed(index: Int, composeMs: Double, widgets: Int, appWidgets: Int, items: Int) {
        if (!enabled) return
        lastComposedPage = index
        lastComposedAtMs = SystemClock.uptimeMillis()
        Log.i(TAG_PAGE, "composed page=$index compose=${"%.1f".format(composeMs)}ms items=$items widgets=$widgets appWidgets=$appWidgets scrolling=$scrolling current=$currentPage")
    }

    /** SW Task 3: kính dock vừa vẽ "sống" (RenderEffect chạy). Kỳ vọng: chỉ vài lần lúc mở Home / đổi nền, không theo từng khung lúc lướt trang. */
    fun dockGlassLiveDraw() {
        if (!enabled) return
        Log.i(TAG_DOCK, "live draw scrolling=$scrolling page=$currentPage")
    }

    /** SW Task 3: đã chụp kính dock thành ảnh. */
    fun dockGlassCaptured(width: Int, height: Int) {
        if (!enabled) return
        Log.i(TAG_DOCK, "captured ${width}x$height")
    }

    private fun ms(ns: Long): String = "%.1f".format(ns / 1_000_000.0)
}
