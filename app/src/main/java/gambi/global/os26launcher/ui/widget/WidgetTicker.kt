package gambi.global.os26launcher.ui.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.TimeZone

/**
 * G2c Task 5 — nhịp cập nhật chung cho mọi widget đang HIỆN. `object` sống xuyên Activity nhưng chỉ giữ tham chiếu tới view đang gắn
 * vào cửa sổ đang hiện: view tự [register] khi (đã attach VÀ cửa sổ hiện) và [unregister] khi ngược lại, nên không rò `View` của
 * Activity đã hủy (cùng mẫu `FolderOrigins`, `DragHelper.registerTarget`).
 *
 * - Không có widget hiện: không `Handler` chờ, không `BroadcastReceiver` nào (Review Focus 8).
 * - Nhịp: hẹn tại ranh giới phút kế (`60000 - now % 60000`, tính lại từ giờ treo tường mỗi lần để không trôi), không phải 60 s cố định.
 * - `TIME_SET` và `TIMEZONE_CHANGED` làm mới ngay; đổi múi giờ thì `TimeZone.setDefault(null)` trước (suy luận: `ZoneId.systemDefault()`
 *   cache múi giờ của tiến trình; kiểm trên máy ở Task 5).
 * - Làm mới ngay khi một view đăng ký (cửa sổ hiện lại sau khi tắt màn hình, quay về từ Cài đặt: đọc lại 12/24 giờ, múi giờ).
 */
object WidgetTicker {
    interface Tickable {
        /** Dựng lại nội dung theo thời điểm hiện tại. */
        fun tickWidget()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val views = LinkedHashSet<Tickable>()
    private var appContext: Context? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) TimeZone.setDefault(null)
            tickAll()
            scheduleNext() // giờ treo tường vừa đổi: hẹn lại tại ranh giới phút kế (Handler tính theo uptime nên lịch cũ có thể trễ tới một nhịp)
        }
    }

    private val tick = Runnable {
        tickAll()
        scheduleNext()
    }

    fun register(context: Context, view: Tickable) {
        if (!views.add(view)) return
        if (views.size == 1) start(context.applicationContext)
        view.tickWidget()
    }

    fun unregister(view: Tickable) {
        if (!views.remove(view)) return
        if (views.isEmpty()) stop()
    }

    /** G3e: làm mới ngay mọi widget đang hiện (vd dữ liệu thời tiết vừa đổi). Gọi từ luồng bất kỳ; chạy trên luồng chính. */
    fun tickNow() {
        handler.post { if (views.isNotEmpty()) tickAll() }
    }

    /** Số view đang đăng ký (để kiểm "không có widget hiện thì không có gì chạy"). */
    val registeredCount: Int get() = views.size

    private fun start(context: Context) {
        appContext = context
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        scheduleNext()
    }

    private fun stop() {
        handler.removeCallbacks(tick)
        appContext?.let {
            try {
                it.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // chưa đăng ký / đã hủy: bỏ qua
            }
        }
        appContext = null
    }

    private fun scheduleNext() {
        handler.removeCallbacks(tick)
        val now = System.currentTimeMillis()
        handler.postDelayed(tick, 60_000L - now % 60_000L + EDGE_MS)
    }

    private fun tickAll() {
        for (view in views.toList()) view.tickWidget()
    }

    /** CHỈNH TAY: trễ nhỏ sau ranh giới phút để phút mới chắc chắn đã đổi. */
    private const val EDGE_MS = 40L
}
