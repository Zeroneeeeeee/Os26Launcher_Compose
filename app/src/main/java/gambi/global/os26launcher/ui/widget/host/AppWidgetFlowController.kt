package gambi.global.os26launcher.ui.widget.host

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.appwidget.OsAppWidgetHost
import gambi.global.os26launcher.domain.layout.AppWidgetLimits
import gambi.global.os26launcher.domain.repository.AppWidgetRepository

/**
 * G3c Task 6 — luồng thêm một widget app: cấp id -> gắn (`bindAppWidgetIdIfAllowed`, không được thì hộp thoại `ACTION_APPWIDGET_BIND`) -> cấu hình nếu provider
 * đòi -> ghi hàng ([onCommit]). Một luồng tại một thời điểm; trạng thái ở [AppWidgetFlowState] (singleton) nên `recreate()` không làm mất luồng.
 *
 * Mọi đường kết thúc để id hoặc đã ghi hàng, hoặc đã giải phóng (bảng L1-L3 của plan): hủy hộp thoại gắn (L1), cấu hình trả không-OK/lỗi (L2), nhấn Home hoặc
 * quá [TIMEOUT_MS] khi activity cấu hình không bao giờ trả kết quả (L3, đo được Live 4: nhấn Home lúc cấu hình thì không có kết quả nào).
 *
 * **Kết quả cấu hình có thể tới hai lần** (đo được Oppo A3x Task 1, khi chưa có `ActivityOptions` D9): kết quả ĐẦU đưa luồng về `Idle`, kết quả sau bị bỏ qua vì
 * không còn trạng thái `Configuring` khớp. Mở activity cấu hình qua [OsAppWidgetHost.startConfigure] (D9 đã đo xong 2026-11-16, Oppo A3x API 34: có
 * `ActivityOptions` thì `resultCode` -1 đúng một lần và màn cấu hình hiện; không có thì `resultCode` 0 hai lần, không màn).
 *
 * Dùng trên luồng chính. `ActivityResultLauncher` đăng ký ở khởi tạo (gọi trong `onCreate`, trước STARTED: bài học G3a). Không giữ view nào.
 */
class AppWidgetFlowController(
    private val activity: FragmentActivity,
    private val host: OsAppWidgetHost,
    private val state: AppWidgetFlowState,
    private val repository: AppWidgetRepository,
    /** Ghi hàng cho widget đã gắn (và cấu hình xong). Nơi gọi chịu trách nhiệm `clearInFlight` và giải phóng id khi không ghi được. */
    private val onCommit: (AppWidgetAddRequest, Int) -> Unit,
    /** Báo người dùng bằng một chuỗi tài nguyên (toast). */
    private val onMessage: (Int) -> Unit
) {
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)

    private val bindLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onBindResult(result.resultCode)
    }

    /** Luồng đang chạy: `HomeActivity.onStop` không thoát edit mode (D11) và bảng chọn không cho bắt đầu luồng thứ hai. */
    val isActive: Boolean get() = state.current !is AppWidgetFlow.Idle

    /**
     * Bắt đầu luồng cho [request]. Trả false nếu luồng khác đang chạy (đã báo người dùng) hoặc không bắt đầu được; true nếu luồng đã chạy
     * (có thể đã xong ngay khi không cần hộp thoại nào).
     */
    fun start(request: AppWidgetAddRequest): Boolean {
        expireStale()
        if (isActive) {
            onMessage(R.string.aw_busy)
            return false
        }
        if (!AppWidgetLimits.allows(request.spanX, request.spanY)) {
            onMessage(R.string.aw_too_large)
            return false
        }
        val id = try {
            host.allocateAppWidgetId()
        } catch (e: RuntimeException) {
            Log.w(TAG, "allocate failed ${e.javaClass.name}")
            onMessage(R.string.aw_add_failed)
            return false
        }
        repository.markInFlight(id)
        state.current = AppWidgetFlow.Binding(request, id, SystemClock.elapsedRealtime())
        Log.d(TAG, "start id=$id")
        val provider = ComponentName(request.provider.packageName, request.provider.className)
        val allowed = try {
            manager.bindAppWidgetIdIfAllowed(id, provider, optionsOf(request))
        } catch (e: RuntimeException) {
            Log.w(TAG, "bindAppWidgetIdIfAllowed failed ${e.javaClass.name}")
            failAndTell(id)
            return false
        }
        if (allowed) {
            afterBind()
        } else {
            val bind = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, optionsOf(request))
            try {
                bindLauncher.launch(bind)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "bind dialog not found")
                failAndTell(id)
                return false
            } catch (e: RuntimeException) {
                Log.w(TAG, "bind dialog failed ${e.javaClass.name}")
                failAndTell(id)
                return false
            }
        }
        return true
    }

    /** Kết quả hộp thoại quyền gắn. Chỉ có nghĩa khi đang `Binding` (kết quả muộn của luồng đã hủy bị bỏ qua). */
    private fun onBindResult(resultCode: Int) {
        val s = state.current as? AppWidgetFlow.Binding
        if (s == null) {
            Log.d(TAG, "bind result ignored")
            return
        }
        if (resultCode == Activity.RESULT_OK) {
            afterBind()
        } else {
            Log.d(TAG, "bind cancelled id=${s.appWidgetId}") // L1
            fail(s.appWidgetId)
        }
    }

    /** Đã gắn xong id của luồng `Binding` hiện tại: cấu hình nếu provider đòi, không thì ghi hàng. */
    private fun afterBind() {
        val s = state.current as? AppWidgetFlow.Binding ?: return
        val id = s.appWidgetId
        val info = try {
            manager.getAppWidgetInfo(id)
        } catch (e: RuntimeException) {
            null
        }
        if (info == null) {
            Log.w(TAG, "bound but no info id=$id")
            failAndTell(id)
            return
        }
        if (!needsConfigure(info)) {
            commit(s.request, id)
            return
        }
        state.current = AppWidgetFlow.Configuring(s.request, id, s.startedAtMs, started = false)
        startConfigureWhenResumed()
    }

    /**
     * `HomeActivity.onResume`: dọn luồng quá hạn, rồi mở activity cấu hình đang chờ. Kết quả hộp thoại gắn tới TRƯỚC `onResume` (activity mới STARTED), mà
     * mở activity cấu hình từ trạng thái chưa RESUMED dễ bị chặn (D9), nên việc mở luôn đợi tới đây.
     */
    fun onResume() {
        expireStale()
        startConfigureWhenResumed()
    }

    private fun startConfigureWhenResumed() {
        val s = state.current as? AppWidgetFlow.Configuring ?: return
        if (s.started) return
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        state.current = s.copy(started = true) // đánh dấu trước: onResume lặp không mở hai lần
        activity.window.decorView.post { launchConfigure(s.appWidgetId) }
    }

    private fun launchConfigure(id: Int) {
        val s = state.current as? AppWidgetFlow.Configuring ?: return
        if (s.appWidgetId != id) return
        try {
            host.startConfigure(activity, id, REQ_CONFIGURE)
            Log.d(TAG, "configure started id=$id")
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "configure not found")
            failAndTell(id)
        } catch (e: RuntimeException) { // gồm SecurityException
            Log.w(TAG, "configure failed ${e.javaClass.name}")
            failAndTell(id)
        }
    }

    /** `HomeActivity.onActivityResult`: kết quả của activity cấu hình (host chỉ có API `startActivityForResult`). Trả true nếu là yêu cầu của ta. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQ_CONFIGURE) return false
        val s = state.current as? AppWidgetFlow.Configuring
        Log.d(TAG, "configure result code=$resultCode active=${s != null && s.started}") // D9: đếm số kết quả mỗi lần cấu hình (kỳ vọng đúng một)
        if (s == null || !s.started) {
            Log.d(TAG, "configure result ignored") // kết quả thứ hai của cùng id, hoặc của luồng đã hủy
            return true
        }
        val resultId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (resultId != AppWidgetManager.INVALID_APPWIDGET_ID && resultId != s.appWidgetId) {
            Log.d(TAG, "configure result for another id ignored")
            return true
        }
        if (resultCode == Activity.RESULT_OK) {
            commit(s.request, s.appWidgetId)
        } else {
            Log.d(TAG, "configure cancelled id=${s.appWidgetId}") // L2
            fail(s.appWidgetId)
        }
        return true
    }

    /**
     * Hủy luồng đang chạy (nhấn Home: L3; activity cấu hình có thể bị hệ thống đóng mà không trả kết quả): giải phóng id, về `Idle`. An toàn khi gọi
     * nhiều lần và khi không có luồng.
     */
    fun abort() {
        val id = when (val s = state.current) {
            is AppWidgetFlow.Binding -> s.appWidgetId
            is AppWidgetFlow.Configuring -> s.appWidgetId
            AppWidgetFlow.Idle -> return
        }
        Log.d(TAG, "abort id=$id")
        fail(id)
    }

    private fun expireStale() {
        val startedAt = when (val s = state.current) {
            is AppWidgetFlow.Binding -> s.startedAtMs
            is AppWidgetFlow.Configuring -> s.startedAtMs
            AppWidgetFlow.Idle -> return
        }
        if (SystemClock.elapsedRealtime() - startedAt > TIMEOUT_MS) abort()
    }

    /** Về `Idle` TRƯỚC khi làm gì khác (kết quả kép chỉ thấy `Idle`), rồi giao cho [onCommit]. */
    private fun commit(request: AppWidgetAddRequest, id: Int) {
        state.current = AppWidgetFlow.Idle
        onCommit(request, id)
    }

    /** Luồng thất bại hoặc bị hủy: về `Idle`, nhả đánh dấu "đang trong luồng", giải phóng id (idempotent). */
    private fun fail(id: Int) {
        state.current = AppWidgetFlow.Idle
        repository.clearInFlight(id)
        repository.release(listOf(id))
    }

    /** [fail] rồi báo lỗi cho người dùng (lỗi hệ thống, khác với việc người dùng tự hủy). */
    private fun failAndTell(id: Int) {
        fail(id)
        onMessage(R.string.aw_add_failed)
    }

    private fun needsConfigure(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val optional = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
            val reconfigurable = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
            if (optional && reconfigurable) return false
        }
        return true
    }

    /** `OPTION_APPWIDGET_*` (dp) theo cỡ ô lưới; một cỡ vì Home khóa dọc. */
    private fun optionsOf(request: AppWidgetAddRequest): Bundle = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, request.widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, request.widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, request.heightDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, request.heightDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
    }

    companion object {
        /** Mã yêu cầu của activity cấu hình: < 65536 (FragmentActivity), không đụng mã do `ActivityResultRegistry` cấp (>= 0x10000). */
        const val REQ_CONFIGURE = 0x4701

        /** Luồng quá hạn này thì giải phóng id (activity cấu hình treo không trả kết quả: L3). */
        const val TIMEOUT_MS = 10 * 60 * 1000L

        private const val TAG = "AppWidgetFlow"
    }
}
