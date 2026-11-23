package gambi.global.os26launcher.debug

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import gambi.global.os26launcher.R

/**
 * G3c Task 5 — CHỈ BẢN DEBUG. Provider thử E: một nút và một `ListView` (cuộn được, bấm mục được) để kiểm chạm của ô widget app
 * (nút bấm được, danh sách cuộn không lật trang, nhấn giữ vẫn vào edit mode). Bấm ghi log `AWPROBE click ...` (chỉ loại và số).
 */
class ProbeProviderE : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.probe_widget_list)
            val buttonIntent = Intent(context, ProbeClickReceiver::class.java).putExtra("kind", "button")
            views.setOnClickPendingIntent(
                R.id.probe_button,
                PendingIntent.getBroadcast(context, id * 2, buttonIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            val serviceIntent = Intent(context, ProbeListService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(R.id.probe_list, serviceIntent)
            val template = Intent(context, ProbeClickReceiver::class.java).putExtra("kind", "item")
            views.setPendingIntentTemplate(
                R.id.probe_list,
                PendingIntent.getBroadcast(context, id * 2 + 1, template, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            manager.updateAppWidget(id, views)
        }
    }
}

class ProbeListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = object : RemoteViewsFactory {
        override fun onCreate() = Unit
        override fun onDataSetChanged() = Unit
        override fun onDestroy() = Unit
        override fun getCount(): Int = 40
        override fun getViewAt(position: Int): RemoteViews =
            RemoteViews(packageName, R.layout.probe_list_item).apply {
                setTextViewText(R.id.probe_item_text, "item $position")
                setOnClickFillInIntent(R.id.probe_item_text, Intent().putExtra("n", position))
            }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = true
    }
}

class ProbeClickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("AWPROBE", "click kind=${intent.getStringExtra("kind")} n=${intent.getIntExtra("n", -1)}")
    }
}
