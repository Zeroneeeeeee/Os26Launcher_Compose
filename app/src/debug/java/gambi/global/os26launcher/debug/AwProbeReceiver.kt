package gambi.global.os26launcher.debug

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import gambi.global.os26launcher.data.layout.db.LauncherItemEntity
import gambi.global.os26launcher.domain.model.AppWidgetRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * G3c Task 1 — CHỈ BẢN DEBUG. `adb shell am broadcast -a gambi.global.os26launcher.AW_PROBE -p gambi.global.os26launcher --es cmd <lệnh> [--es p A|B|C|D] [--ei id N]`
 * Lệnh: `counts` (số provider thấy được, số id của host) · `ids` · `alloc` (cấp một id, KHÔNG giải phóng: thử rò) · `trybind` (cấp id, bindAppWidgetIdIfAllowed provider `p`,
 * log true/false, rồi giải phóng) · `info` (id: getAppWidgetInfo có null không) · `delete` (id) · `deletehost` · `unbound` (getAppWidgetInfo với id chưa từng cấp).
 * Receiver khai ở src/debug/AndroidManifest.xml, exported + DUMP (mẫu CP).
 */
class AwProbeReceiver : BroadcastReceiver() {
    /** Chạy [block] trên IO với `goAsync` (broadcast không bị hệ thống kết thúc giữa chừng); lỗi chỉ log tên lớp. */
    private fun async(block: suspend (AwProbe.Access) -> Unit) {
        val pending = goAsync()
        val a = AwProbe.access(appContext ?: return)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                block(a)
            } catch (e: Exception) {
                Log.w(AwProbe.TAG, "async cmd threw ${e.javaClass.name}")
            } finally {
                pending.finish()
            }
        }
    }

    private var appContext: Context? = null

    override fun onReceive(context: Context, intent: Intent) {
        appContext = context
        val host = AwProbe.host(context)
        val manager = AppWidgetManager.getInstance(context)
        val id = intent.getIntExtra("id", AppWidgetManager.INVALID_APPWIDGET_ID)
        when (val cmd = intent.getStringExtra("cmd")) {
            "counts" -> AwProbe.counts(context)
            "ids" -> Log.i(AwProbe.TAG, "ids=${AwProbe.safe("getAppWidgetIds") { host.appWidgetIds }?.joinToString(",")}")
            "alloc" -> Log.i(AwProbe.TAG, "alloc id=${AwProbe.safe("allocate") { host.allocateAppWidgetId() }}")
            "trybind" -> {
                val provider = AwProbe.providerOf(intent.getStringExtra("p") ?: "A")
                if (provider == null) {
                    Log.w(AwProbe.TAG, "trybind unknown provider")
                    return
                }
                val newId = AwProbe.safe("allocate") { host.allocateAppWidgetId() } ?: return
                val ok = AwProbe.safe("bindAppWidgetIdIfAllowed") {
                    manager.bindAppWidgetIdIfAllowed(newId, provider, AwProbe.options())
                }
                Log.i(AwProbe.TAG, "trybind id=$newId allowed=$ok")
                host.deleteAppWidgetId(newId)
                Log.i(AwProbe.TAG, "trybind released id=$newId")
            }

            "info" -> {
                val info = AwProbe.safe("getAppWidgetInfo") { manager.getAppWidgetInfo(id) }
                Log.i(AwProbe.TAG, "info id=$id null=${info == null} own=${info?.provider?.packageName == context.packageName}")
            }

            // ---- Task 2: hàng widget app trong DB + giải phóng id (docs/plans/G3c-appwidget-host.md Task 2). Mọi log chỉ có số. ----
            // addrow --es p A|B|C|D --ei sx N --ei sy N [--es pkg <package giả, để thử catchUp xóa hàng của app đã gỡ>]
            "addrow" -> async { a ->
                val provider = AwProbe.providerOf(intent.getStringExtra("p") ?: "A") ?: return@async
                val sx = intent.getIntExtra("sx", 2)
                val sy = intent.getIntExtra("sy", 2)
                // --ei wid N: dùng id N có sẵn (không cấp, không gắn): thử hàng với id vô hiệu/lạ (placeholder, plan L10)
                val given = intent.hasExtra("wid")
                val newId = if (given) intent.getIntExtra("wid", 0) else host.allocateAppWidgetId()
                if (!given) a.appWidgets().markInFlight(newId)
                val allowed = given || manager.bindAppWidgetIdIfAllowed(newId, provider, AwProbe.options())
                var page: Long? = null
                if (allowed) {
                    val pkg = intent.getStringExtra("pkg") ?: provider.packageName
                    page = a.layout().addAppWidget(AppWidgetRef(pkg, provider.className, 0L), newId, sx, sy, null)
                }
                a.appWidgets().clearInFlight(newId)
                if (page == null) a.appWidgets().release(listOf(newId))
                Log.i(AwProbe.TAG, "addrow id=$newId allowed=$allowed span=${sx}x$sy page=$page ids=${host.appWidgetIds.joinToString(",")}")
            }
            // rows: các hàng itemType 3 (số)
            "rows" -> async { a ->
                val rows = a.dao().getAll().filter { it.itemType == LauncherItemEntity.ITEM_TYPE_WIDGET }
                Log.i(AwProbe.TAG, "rows widgets=${rows.size} hostIds=${host.appWidgetIds.joinToString(",")}")
                rows.forEach { Log.i(AwProbe.TAG, "row item=${it.id} appWidgetId=${it.appWidgetId} page=${it.pageId} at=${it.gridX},${it.gridY} span=${it.spanX}x${it.spanY}") }
            }
            // rmitem --ei item N: đường xóa hàng như dấu "−" (RemoveWidgetUseCase -> LayoutReconciler.removeItem)
            "rmitem" -> async { a ->
                val item = intent.getIntExtra("item", -1).toLong()
                a.layout().removeItem(item)
                Log.i(AwProbe.TAG, "rmitem item=$item done hostIds=${host.appWidgetIds.joinToString(",")}")
            }
            // catchup: chạy LayoutReconciler.catchUp với danh sách app hiện tại (hàng widget của package đã gỡ bị xóa, id giải phóng, sweep)
            "catchup" -> async { a ->
                a.reconciler().catchUp(a.apps().apps.first { it.isNotEmpty() })
                Log.i(AwProbe.TAG, "catchup done hostIds=${host.appWidgetIds.joinToString(",")}")
            }
            // removepkg --es pkg X: đường PackageEvent.REMOVED (LayoutReconciler.removePackages) cho hồ sơ chính: hàng widget của package bị xóa phẳng (KHÔNG dồn ô), id giải phóng
            "removepkg" -> async { a ->
                val pkg = intent.getStringExtra("pkg") ?: return@async
                a.reconciler().removePackages(listOf(pkg), 0L)
                Log.i(AwProbe.TAG, "removepkg done hostIds=${host.appWidgetIds.joinToString(",")}")
            }
            // flow: trạng thái luồng thêm widget app (Idle, Binding, Configuring) và id; chỉ số, không tên
            "flow" -> {
                val s = AwProbe.access(context).flowState().current
                Log.i(AwProbe.TAG, "flow state=${s.javaClass.simpleName} ids=${host.appWidgetIds.joinToString(",")}")
            }
            // refreshall: đẩy nội dung mới (chữ có giờ) cho mọi widget của host (thử cập nhật nội dung, Task 4)
            "refreshall" -> {
                val ids = AwProbe.safe("getAppWidgetIds") { host.appWidgetIds } ?: IntArray(0)
                for (wid in ids) AwProbe.safe("updateAppWidget") { manager.updateAppWidget(wid, ProbeViews.build(context, "R", wid)) }
                Log.i(AwProbe.TAG, "refreshall ids=${ids.size}")
            }
            // allocflight: cấp id và đánh dấu luồng đang chạy (sweep không được đụng); endflight --ei id: bỏ đánh dấu
            "allocflight" -> {
                val newId = host.allocateAppWidgetId()
                AwProbe.access(context).appWidgets().markInFlight(newId)
                Log.i(AwProbe.TAG, "allocflight id=$newId")
            }
            "endflight" -> {
                AwProbe.access(context).appWidgets().clearInFlight(id)
                Log.i(AwProbe.TAG, "endflight id=$id")
            }

            "unbound" -> {
                val bogus = 999_999
                val info = AwProbe.safe("getAppWidgetInfo") { manager.getAppWidgetInfo(bogus) }
                Log.i(AwProbe.TAG, "unbound id=$bogus null=${info == null}")
            }

            "delete" -> {
                AwProbe.safe("deleteAppWidgetId") { host.deleteAppWidgetId(id) }
                Log.i(AwProbe.TAG, "delete id=$id done ids=${AwProbe.safe("getAppWidgetIds") { host.appWidgetIds }?.joinToString(",")}")
            }

            "deletehost" -> {
                AwProbe.safe("deleteHost") { host.deleteHost() }
                Log.i(AwProbe.TAG, "deletehost done ids=${AwProbe.safe("getAppWidgetIds") { host.appWidgetIds }?.joinToString(",")}")
            }

            else -> Log.w(AwProbe.TAG, "unknown cmd=$cmd")
        }
    }
}
