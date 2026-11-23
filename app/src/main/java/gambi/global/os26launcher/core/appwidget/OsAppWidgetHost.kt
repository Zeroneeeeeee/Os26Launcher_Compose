package gambi.global.os26launcher.core.appwidget

import android.app.Activity
import android.app.ActivityOptions
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * G3c — AppWidgetHost duy nhất của app (singleton Hilt, `di/AppWidgetModule`). Đặt ở `core/appwidget` vì cả `data` (cấp/giải phóng id)
 * lẫn `ui` (tạo `AppWidgetHostView`) cùng dùng; ngoại lệ phân lớp có chủ ý như `core/glass` (điều phối chấp nhận 2026-11-13).
 *
 * [HOST_ID] **không bao giờ đổi**: đổi thì mọi id đã cấp cho host cũ thành mồ côi (đọc tài liệu: host id "persists across all uses of
 * the host", docs/plans/G3c-appwidget-host.md D5).
 *
 * Task 4: [onCreateView] trả [OsAppWidgetHostView] (chữ lỗi của ta); [listener] nhận "provider đổi" để Home bind lại ô. Các callback của host chạy trên luồng
 * chính (đọc tài liệu: host tạo `Handler` theo `Context.getMainLooper()`).
 */
class OsAppWidgetHost(context: Context) : AppWidgetHost(context.applicationContext, HOST_ID) {

    /** Chủ của sổ host view (Activity đang sống). Đặt/gỡ bởi `HostedWidgetViews`. */
    interface Listener {
        fun onProviderChanged(appWidgetId: Int)
        fun onProvidersChanged()
    }

    @Volatile
    var listener: Listener? = null

    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        OsAppWidgetHostView(context)

    override fun onProviderChanged(appWidgetId: Int, appWidget: AppWidgetProviderInfo?) {
        super.onProviderChanged(appWidgetId, appWidget)
        listener?.onProviderChanged(appWidgetId)
    }

    override fun onProvidersChanged() {
        super.onProvidersChanged()
        listener?.onProvidersChanged()
    }

    /** API 30+: ý nghĩa chưa rõ (G3c plan, đo ở Task 4); chỉ ghi id số để thấy khi nào hệ thống gọi. */
    override fun onAppWidgetRemoved(appWidgetId: Int) {
        super.onAppWidgetRemoved(appWidgetId)
        Log.d(TAG, "onAppWidgetRemoved id=$appWidgetId")
    }

    /**
     * Mở activity cấu hình của provider (qua `IntentSender` do hệ thống tạo). API 34+: truyền `ActivityOptions` cho phép khởi chạy nền
     * (`setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED)`, cách Launcher3 `Utilities.allowBGLaunch`), vì đo Oppo A3x (API 34,
     * Task 4+5 phần C) thấy lần mở trả `RESULT_CANCELED` ngay mà không hiện màn nếu không có (nghi do hạn chế khởi chạy activity nền của Android 14: suy luận
     * chưa kiểm chứng, biến thể trong `AwProbeActivity` đo lại). Gọi từ trạng thái RESUMED của [activity] khi có thể.
     */
    fun startConfigure(activity: Activity, appWidgetId: Int, requestCode: Int, creatorToo: Boolean = false) {
        startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, requestCode, backgroundStartOptions(creatorToo))
    }

    /** `null` dưới API 34 (không có khái niệm này). [creatorToo]: cũng đặt phía người tạo `PendingIntent` (chỉ để đo biến thể). */
    fun backgroundStartOptions(creatorToo: Boolean = false): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val options = ActivityOptions.makeBasic()
        options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        if (creatorToo) {
            try {
                options.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            } catch (e: NoSuchMethodError) {
                Log.w(TAG, "creator mode unavailable")
            }
        }
        return options.toBundle()
    }

    /** `clearViews()` của lớp cha là `protected`; `HostedWidgetViews.clear()` (Activity `onDestroy`) gọi qua đây để host không giữ view mang `Context` Activity. */
    fun clearHostViews() = clearViews()

    companion object {
        /** 0x4F533236 = "OS26". */
        const val HOST_ID = 0x4F533236
        private const val TAG = "OsAppWidgetHost"
    }
}
