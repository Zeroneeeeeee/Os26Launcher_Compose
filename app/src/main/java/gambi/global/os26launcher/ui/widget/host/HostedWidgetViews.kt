package gambi.global.os26launcher.ui.widget.host

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.LayoutInflater
import android.view.ViewGroup
import gambi.global.os26launcher.core.appwidget.OsAppWidgetHost
import gambi.global.os26launcher.domain.model.AppWidgetRef
import gambi.global.os26launcher.domain.model.PlacedAppWidget

/**
 * G3c Task 4 — sổ host view của một Activity: MỘT [AppWidgetHostView] mỗi `appWidgetId`, dùng lại giữa các ô và các trang (`ViewPager2` tháo/gắn trang liên
 * tục; `AppWidgetHost.createView` giữ con trỏ tới view vừa tạo nên hai view cùng id thì cái mới thay cái cũ trong sổ của host: đọc tài liệu, suy luận cho
 * hệ quả). Ô lấy view bằng [obtain]; ô khác lấy cùng view thì tự gỡ nó khỏi cha cũ (xem `IconCellView.attachHostView`).
 *
 * Vòng đời gắn với Activity: tạo ở `onCreate`, [clear] ở `onDestroy` (host giữ tham chiếu mạnh view đã tạo, mà view mang `Context` Activity; suy luận chưa
 * kiểm chứng, đo `Activities:` trong `dumpsys meminfo` sau `recreate()`). Chỉ dùng trên luồng chính.
 */
class HostedWidgetViews(
    private val context: Context,
    private val host: OsAppWidgetHost
) : OsAppWidgetHost.Listener {

    private val manager: AppWidgetManager = AppWidgetManager.getInstance(context)

    /**
     * Context để dựng view widget của app khác: vẫn là Activity (bấm widget mở app, theme), nhưng `LayoutInflater` KHÔNG mang factory của AppCompat.
     * Lý do (đo được, Live 4 API 30, 2026-11-16): `RemoteViews` thổi layout bằng inflater của context host view; inflater của `AppCompatActivity` đổi
     * `ImageView` thành `AppCompatImageView` (và các view khác), mà lớp đó không có phương thức `@RemotableViewMethod` (`setImageResource`...) nên
     * `RemoteViews$ActionException` / `InflateException` và host hiện chữ lỗi "Không tải được widget" (Lịch Vsmart, YouTube ReVanced...).
     * Launcher3 tránh được vì Activity của nó không dùng AppCompat.
     */
    private val widgetContext: Context = object : ContextWrapper(context) {
        private var plainInflater: LayoutInflater? = null
        override fun getSystemService(name: String): Any? {
            if (Context.LAYOUT_INFLATER_SERVICE == name) {
                return plainInflater ?: LayoutInflater.from(baseContext.applicationContext).cloneInContext(this).also { plainInflater = it }
            }
            return super.getSystemService(name)
        }
    }
    private val views = HashMap<Int, AppWidgetHostView>()
    private val lastPushedDp = HashMap<Int, Long>()

    /** Provider đổi hoặc tập provider đổi: Home bind lại các trang (ô placeholder ↔ widget). Đặt bởi `HomeActivity`. */
    var onChanged: (() -> Unit)? = null

    init {
        host.listener = this
    }

    /**
     * Host view cho widget [widget], hoặc null nếu provider không dùng được (vắng, bị tắt, đang cập nhật, hàng của máy khác với id vô hiệu: id không có info hoặc
     * info thuộc provider KHÁC provider trong hàng, plan L10). null thì ô vẽ placeholder; hàng KHÔNG bị xóa (plan D14).
     */
    fun obtain(widget: PlacedAppWidget): AppWidgetHostView? {
        val id = widget.appWidgetId
        // View đã tạo: coi là hợp lệ, không gọi lại getAppWidgetInfo (IPC) mỗi lần composable recompose (scroll/kéo).
        // Provider đổi/mất báo qua onProviderChanged → onChanged?.invoke() rebind trang, lúc đó hàm này được gọi lại từ đầu.
        views[id]?.let { return it }
        val info = try {
            manager.getAppWidgetInfo(id)
        } catch (e: RuntimeException) {
            Log.w(TAG, "getAppWidgetInfo failed ${e.javaClass.name}")
            null
        }
        if (info == null || !matches(widget.provider, info)) {
            drop(id)
            return null
        }
        return try {
            Log.d(TAG, "createView id=$id") // số lần tạo view mỗi id (kiểm "một view mỗi id": đổi trang, recreate chỉ tạo lại sau clear())
            host.createView(widgetContext, id, info).also { views[id] = it }
        } catch (e: RuntimeException) {
            Log.w(TAG, "createView failed ${e.javaClass.name}")
            null
        }
    }

    /** View đã tạo của [appWidgetId] (không tạo mới, không IPC): ảnh nổi lúc kéo chụp lại view này, không gỡ nó khỏi ô trên lưới. */
    fun peek(appWidgetId: Int): AppWidgetHostView? = views[appWidgetId]

    private fun matches(ref: AppWidgetRef, info: AppWidgetProviderInfo): Boolean =
        info.provider.packageName == ref.packageName &&
                (ref.className.isEmpty() || info.provider.className == ref.className)

    /**
     * Ô báo cỡ nội dung (px) của widget [appWidgetId]: đẩy cho provider theo dp (`onAppWidgetOptionsChanged`), bỏ qua nếu cỡ dp không đổi (chống gọi lặp mỗi lần layout).
     * Một cỡ duy nhất vì Home khóa dọc (`nosensor`).
     *
     * B13: `updateAppWidgetSize` tự trừ padding mặc định ([AppWidgetHostView.getDefaultPaddingForWidget]) khỏi cỡ ta đưa, kể cả khi view không còn padding
     * thật ([gambi.global.os26launcher.core.appwidget.OsAppWidgetHostView] đặt về 0). Đọc mã nguồn AOSP + đo được trên Live 4 API 30 (đưa 317×77, hệ thống
     * giữ 301×61): cộng bù đúng phần sẽ bị trừ để provider nhận cỡ nội dung thật.
     */
    fun onSized(appWidgetId: Int, view: AppWidgetHostView, widthPx: Int, heightPx: Int, density: Float) {
        if (widthPx <= 0 || heightPx <= 0 || density <= 0f) return
        val pad = view.appWidgetInfo?.provider?.let { provider ->
            try {
                @Suppress("DEPRECATION")
                AppWidgetHostView.getDefaultPaddingForWidget(context, provider, null)
            } catch (e: RuntimeException) {
                Log.w(TAG, "getDefaultPaddingForWidget failed ${e.javaClass.name}")
                null
            }
        }
        // Cùng phép làm tròn với AOSP: (int) ((left + right) / density).
        val padXDp = if (pad != null) ((pad.left + pad.right) / density).toInt() else 0
        val padYDp = if (pad != null) ((pad.top + pad.bottom) / density).toInt() else 0
        val wDp = (widthPx / density).toInt() + padXDp
        val hDp = (heightPx / density).toInt() + padYDp
        val key = (wDp.toLong() shl 32) or hDp.toLong()
        if (lastPushedDp[appWidgetId] == key) return
        lastPushedDp[appWidgetId] = key
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.updateAppWidgetSize(Bundle(), listOf(SizeF(wDp.toFloat(), hDp.toFloat())))
            } else {
                view.updateAppWidgetSize(Bundle(), wDp, hDp, wDp, hDp)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "updateAppWidgetSize failed ${e.javaClass.name}")
        }
    }

    /** Bỏ view của [appWidgetId] khỏi sổ (hàng bị xóa, provider không còn). Gỡ khỏi ô đang giữ nó. */
    fun release(appWidgetId: Int) = drop(appWidgetId)

    private fun drop(id: Int) {
        lastPushedDp.remove(id)
        views.remove(id)?.let { (it.parent as? ViewGroup)?.removeView(it) }
    }

    /** `HomeActivity.onDestroy`: tháo mọi view khỏi cha, xóa sổ của ta và của host, gỡ listener. */
    fun clear() {
        for (view in views.values) (view.parent as? ViewGroup)?.removeView(view)
        views.clear()
        lastPushedDp.clear()
        host.clearHostViews()
        if (host.listener === this) host.listener = null
        onChanged = null
    }

    override fun onProviderChanged(appWidgetId: Int) {
        // obtain() không còn gọi getAppWidgetInfo khi đã có view cache; provider đổi phải tự drop ở đây để lần obtain() kế tiếp tạo lại view mới.
        drop(appWidgetId)
        onChanged?.invoke()
    }

    override fun onProvidersChanged() {
        onChanged?.invoke()
    }

    private companion object {
        const val TAG = "HostedWidgetViews"
    }
}
