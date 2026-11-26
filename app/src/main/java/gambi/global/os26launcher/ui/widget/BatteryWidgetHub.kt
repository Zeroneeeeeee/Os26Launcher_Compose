package gambi.global.os26launcher.ui.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.ContextCompat

/**
 * BW — nguồn dữ liệu của widget Pin (cùng mẫu [WeatherWidgetHub] / [WidgetTicker]: `object` sống xuyên Activity, không giữ `View`).
 *
 * - Chỉ nghe pin khi có người dùng: ô widget Pin đang hiện, hoặc bản xem trước trong bảng chọn ([acquire] / [release], đếm tham chiếu). Không
 *   ai dùng thì gỡ receiver (không giữ suốt đời process).
 * - `ACTION_BATTERY_CHANGED` là broadcast dính (sticky): đăng ký là có ngay giá trị hiện tại; `ACTION_POWER_SAVE_MODE_CHANGED` cho tiết kiệm
 *   pin. Không cần quyền. Lúc sạc hệ thống gửi rất dày (đổi nhiệt độ, điện áp) nên chỉ báo khi %, cắm sạc hoặc tiết kiệm pin THẬT SỰ đổi:
 *   [WidgetTicker.tickNow] (ô trên lưới / Today vẽ lại) và tăng [version] (bản xem trước Compose đọc để vẽ lại).
 * - Mọi hàm chạy trên luồng chính (receiver động nhận trên luồng chính).
 */
object BatteryWidgetHub {
    /** [level] 0..100; [plugged] đang cắm sạc (kể cả đã đầy); [powerSave] chế độ tiết kiệm pin của hệ thống. */
    data class State(val level: Int, val plugged: Boolean, val powerSave: Boolean)

    @Volatile
    var state: State? = null
        private set

    /** Tăng mỗi khi [state] đổi (đọc trong composition để vẽ lại bản xem trước). */
    val version = mutableIntStateOf(0)

    private var refs = 0
    private var appContext: Context? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            update(context, intent.takeIf { it.action == Intent.ACTION_BATTERY_CHANGED })
        }
    }

    fun acquire(context: Context) {
        refs++
        if (refs == 1) start(context.applicationContext)
    }

    fun release() {
        if (refs == 0) return
        refs--
        if (refs == 0) stop()
    }

    private fun start(context: Context) {
        appContext = context
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        // Broadcast của hệ thống vẫn tới receiver NOT_EXPORTED. Giá trị trả về là bản dính của ACTION_BATTERY_CHANGED.
        val sticky = ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        update(context, sticky)
    }

    private fun stop() {
        val context = appContext ?: return
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // chưa đăng ký (không xảy ra theo đếm tham chiếu), bỏ qua
        }
        appContext = null
    }

    /** [battery] null (đổi tiết kiệm pin) thì đọc lại bản dính. */
    private fun update(context: Context, battery: Intent?) {
        // receiver null = chỉ đọc bản dính, không đăng ký gì (broadcast hệ thống, không cần cờ export).
        val b = battery ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (l >= 0 && s > 0) l * 100 / s else null
        } ?: context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
            ?: return
        val plugged = (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        val next = State(level.coerceIn(0, 100), plugged, powerSave)
        if (next == state) return
        state = next
        version.intValue++
        WidgetTicker.tickNow()
    }
}
