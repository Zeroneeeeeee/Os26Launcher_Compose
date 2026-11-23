package gambi.global.os26launcher.data.appwidget

import android.content.Context
import android.content.pm.PackageManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.core.appwidget.AppWidgetAssets
import gambi.global.os26launcher.core.appwidget.OsAppWidgetHost
import gambi.global.os26launcher.domain.model.AppWidgetAppEntry
import gambi.global.os26launcher.domain.model.AppWidgetProviderEntry
import gambi.global.os26launcher.domain.model.AppWidgetRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import gambi.global.os26launcher.domain.repository.AppWidgetRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * G3c Task 2 — vòng đời id của [OsAppWidgetHost]. Log chỉ có số đếm và tên lớp ngoại lệ (riêng tư: không package/lớp provider).
 *
 * `getAppWidgetIds()` là API công khai từ API 26 = minSdk (đọc tài liệu), đo được trên Live 4 (Task 1) rằng nó liệt kê đúng id đã cấp và id còn sau khi
 * tiến trình bị giết. Máy không có tính năng widget (`FEATURE_APP_WIDGETS`) thì mọi hàm là no-op.
 */
@Singleton
class AppWidgetRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val host: OsAppWidgetHost,
    private val assets: AppWidgetAssets
) : AppWidgetRepository {

    private val inFlight: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    override val isSupported: Boolean get() = supported

    private val supported: Boolean by lazy {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)
    }

    override fun release(appWidgetIds: Collection<Int>) {
        if (!supported) return
        var released = 0
        for (id in appWidgetIds) {
            if (id in inFlight) continue
            try {
                host.deleteAppWidgetId(id)
                released++
            } catch (e: RuntimeException) {
                Log.w(TAG, "deleteAppWidgetId failed ${e.javaClass.name}")
            }
        }
        if (appWidgetIds.isNotEmpty()) Log.d(TAG, "release requested=${appWidgetIds.size} released=$released")
    }

    override fun sweep(keepIds: Set<Int>) {
        if (!supported) return
        val held = try {
            host.appWidgetIds
        } catch (e: RuntimeException) {
            Log.w(TAG, "getAppWidgetIds failed ${e.javaClass.name}")
            return
        }
        val orphans = held.filter { it !in keepIds && it !in inFlight }
        Log.d(TAG, "sweep held=${held.size} keep=${keepIds.size} orphans=${orphans.size}")
        release(orphans)
    }

    override fun resetHost() {
        if (!supported) return
        if (inFlight.isNotEmpty()) {
            sweep(emptySet())
            return
        }
        try {
            host.deleteHost()
            Log.d(TAG, "resetHost done")
        } catch (e: RuntimeException) {
            Log.w(TAG, "deleteHost failed ${e.javaClass.name}")
        }
    }

    override fun markInFlight(appWidgetId: Int) {
        inFlight.add(appWidgetId)
    }

    override fun clearInFlight(appWidgetId: Int) {
        inFlight.remove(appWidgetId)
    }

    override suspend fun catalog(): List<AppWidgetAppEntry> = withContext(Dispatchers.IO) {
        if (!supported) return@withContext emptyList()
        val userSerial = try {
            context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
        } catch (e: RuntimeException) {
            0L
        }
        val pm = context.packageManager
        val byPackage = LinkedHashMap<String, MutableList<AppWidgetProviderEntry>>()
        for (info in assets.pickerProviders()) {
            byPackage.getOrPut(info.provider.packageName) { ArrayList() }.add(entryOf(info, userSerial))
        }
        val apps = byPackage.map { (pkg, providers) ->
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
            AppWidgetAppEntry(pkg, userSerial, label, providers)
        }.sortedBy { it.appLabel.lowercase() }
        Log.d(TAG, "catalog apps=${apps.size} providers=${apps.sumOf { it.providers.size }}")
        apps
    }

    private fun entryOf(info: AppWidgetProviderInfo, userSerial: Long): AppWidgetProviderEntry {
        val density = context.resources.displayMetrics.density
        fun dp(px: Int): Int = if (px <= 0) 0 else (px / density).toInt()
        val s = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        return AppWidgetProviderEntry(
            ref = AppWidgetRef(info.provider.packageName, info.provider.className, userSerial),
            minWidthDp = dp(info.minWidth),
            minHeightDp = dp(info.minHeight),
            minResizeWidthDp = dp(info.minResizeWidth),
            minResizeHeightDp = dp(info.minResizeHeight),
            maxResizeWidthDp = if (s) dp(info.maxResizeWidth) else 0,
            maxResizeHeightDp = if (s) dp(info.maxResizeHeight) else 0,
            resizeHorizontal = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
            resizeVertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
            targetCellWidth = if (s) info.targetCellWidth else 0,
            targetCellHeight = if (s) info.targetCellHeight else 0
        )
    }

    private companion object {
        const val TAG = "AppWidgetRepo"
    }
}
