package gambi.global.os26launcher.debug

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import gambi.global.os26launcher.R

/** G3c Task 1 — CHỈ BẢN DEBUG. Bốn provider thử (A: không cấu hình; B: cấu hình bắt buộc; C: cấu hình không kết thúc; D: cấu hình lại được + tùy chọn). */
open class ProbeWidgetProvider(private val tag: String) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) manager.updateAppWidget(id, ProbeViews.build(context, tag, id))
    }

    /** G3c Task 4: log cỡ dp host báo (chỉ số) để kiểm `updateAppWidgetSize` đúng và không bị gọi lặp. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        android.util.Log.i(
            "AWPROBE",
            "options id=$appWidgetId tag=$tag minW=${newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)} minH=${newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)} " +
                    "maxW=${newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)} maxH=${newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)}"
        )
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
    }
}

class ProbeProviderA : ProbeWidgetProvider("A")
class ProbeProviderB : ProbeWidgetProvider("B")
class ProbeProviderC : ProbeWidgetProvider("C")
class ProbeProviderD : ProbeWidgetProvider("D")

internal object ProbeViews {
    fun build(context: Context, tag: String, id: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.probe_widget).apply {
            setTextViewText(R.id.probe_text, "probe $tag id=$id t=${System.currentTimeMillis() / 1000 % 1000}")
        }
}
