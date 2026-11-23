package gambi.global.os26launcher.debug

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * G3c Task 1 — CHỈ BẢN DEBUG. Chạy đúng chuỗi của luồng thêm widget: cấp id → bindAppWidgetIdIfAllowed → (nếu false) hộp thoại ACTION_APPWIDGET_BIND →
 * (nếu provider có `configure` và không phải OPTIONAL + RECONFIGURABLE) activity cấu hình → log kết quả. Không tạo hàng DB.
 * `adb shell am start -n gambi.global.os26launcher/.debug.AwProbeActivity --es p A|B|C|D --es after keep|delete`
 * `after` = giữ id (keep) hay giải phóng khi xong (delete, mặc định).
 * Task 4+5 phần C (Oppo, API 34: cấu hình trả RESULT_CANCELED ngay hai lần, màn không hiện) thêm biến thể đo:
 * `--es opts none|bal|bal2|direct` (mặc định bal): none = như Task 1 (options null); bal = `ActivityOptions` cho phép khởi chạy nền phía người gửi
 * (`OsAppWidgetHost.startConfigure`); bal2 = thêm phía người tạo; direct = KHÔNG qua hệ thống, mở thẳng `Intent(ACTION_APPWIDGET_CONFIGURE)` tới activity của provider.
 * `--es when create|resume` (mặc định resume): mở cấu hình ngay trong `onCreate` (như Task 1, activity chưa RESUMED) hay ở `onResume` đầu tiên.
 * Kết quả tới hai lần cho cùng id thì lần sau bị bỏ qua (cờ [resultHandled]; Task 6 làm tương đương bằng trạng thái luồng `Idle`).
 */
class AwProbeActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var info: AppWidgetProviderInfo? = null
    private var after = "delete"
    private var opts = "bal"
    private var whenStart = "resume"
    private var pendingConfigure = false
    private var configureStarted = false
    private var resultHandled = false
    private var resultCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        after = intent.getStringExtra("after") ?: "delete"
        opts = intent.getStringExtra("opts") ?: "bal"
        whenStart = intent.getStringExtra("when") ?: "resume"
        val provider = AwProbe.providerOf(intent.getStringExtra("p") ?: "A")
        if (provider == null) {
            Log.w(TAG, "flow unknown provider")
            finish()
            return
        }
        info = AwProbe.infoOf(this, provider)
        if (info == null) {
            Log.w(TAG, "flow provider NOT visible in getInstalledProviders")
            finish()
            return
        }
        val host = AwProbe.host(this)
        appWidgetId = host.allocateAppWidgetId()
        Log.i(TAG, "flow allocated id=$appWidgetId")
        val allowed = AwProbe.safe("bindAppWidgetIdIfAllowed") {
            AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(appWidgetId, provider, AwProbe.options())
        } == true
        Log.i(TAG, "flow bindAppWidgetIdIfAllowed=$allowed")
        if (allowed) {
            afterBind()
        } else {
            val bind = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, AwProbe.options())
            try {
                startActivityForResult(bind, REQ_BIND)
                Log.i(TAG, "flow bind dialog started")
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "flow bind dialog NOT FOUND")
                finishFlow()
            } catch (e: SecurityException) {
                Log.w(TAG, "flow bind dialog SecurityException")
                finishFlow()
            }
        }
    }

    @Deprecated("probe")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val what = if (requestCode == REQ_BIND) "bind" else "configure"
        resultCount++
        Log.i(TAG, "flow result request=$what resultCode=$resultCode extraId=${data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)} n=$resultCount")
        if (resultHandled && requestCode == REQ_CONFIG) {
            Log.i(TAG, "flow result ignored (already handled)")
            return
        }
        if (requestCode == REQ_CONFIG) resultHandled = true
        when {
            requestCode == REQ_BIND && resultCode == RESULT_OK -> afterBind()
            requestCode == REQ_CONFIG && resultCode == RESULT_OK -> finishFlow()
            else -> {
                AwProbe.host(this).deleteAppWidgetId(appWidgetId)
                Log.i(TAG, "flow cancelled, released id=$appWidgetId")
                finish()
            }
        }
    }

    private fun afterBind() {
        val i = info
        if (i == null) {
            finishFlow()
            return
        }
        val optional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (i.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0 &&
                (i.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0
        val features = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) i.widgetFeatures else 0
        Log.i(TAG, "flow afterBind configure=${i.configure != null} widgetFeatures=$features skipConfigure=$optional")
        if (i.configure == null || optional) {
            finishFlow()
            return
        }
        if (whenStart == "resume" && !hasWindowFocus() && !resumedOnce) {
            pendingConfigure = true // mở ở onResume đầu tiên (activity đã hiện)
            return
        }
        startConfigureNow()
    }

    private var resumedOnce = false

    override fun onResume() {
        super.onResume()
        resumedOnce = true
        if (pendingConfigure) {
            pendingConfigure = false
            window.decorView.post { startConfigureNow() }
        }
    }

    private fun startConfigureNow() {
        if (configureStarted) return
        configureStarted = true
        val i = info ?: return
        try {
            val host = AwProbe.host(this)
            if (opts == "direct") {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setComponent(i.configure)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                startActivityForResult(intent, REQ_CONFIG)
            } else if (opts == "none") {
                host.startAppWidgetConfigureActivityForResult(this, appWidgetId, 0, REQ_CONFIG, null)
            } else {
                host.startConfigure(this, appWidgetId, REQ_CONFIG, creatorToo = opts == "bal2")
            }
            Log.i(TAG, "flow configure started opts=$opts when=$whenStart resumed=$resumedOnce")
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "flow configure NOT FOUND")
            finishFlow()
        } catch (e: SecurityException) {
            Log.w(TAG, "flow configure SecurityException")
            finishFlow()
        }
    }

    private fun finishFlow() {
        val host = AwProbe.host(this)
        val manager = AppWidgetManager.getInstance(this)
        Log.i(
            TAG, "flow done id=$appWidgetId infoNonNull=${manager.getAppWidgetInfo(appWidgetId) != null} " +
                    "inHostIds=${host.appWidgetIds.contains(appWidgetId)} after=$after"
        )
        if (after == "delete") host.deleteAppWidgetId(appWidgetId)
        Log.i(TAG, "flow ids=${host.appWidgetIds.joinToString(",")}")
        finish()
    }

    override fun onDestroy() {
        Log.i(TAG, "flow onDestroy isFinishing=$isFinishing")
        super.onDestroy()
    }

    private companion object {
        const val TAG = AwProbe.TAG
        const val REQ_BIND = 7001
        const val REQ_CONFIG = 7002
    }
}
