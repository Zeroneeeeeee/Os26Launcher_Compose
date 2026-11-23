package gambi.global.os26launcher.data.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.core.locale.AppLocaleContext
import gambi.global.os26launcher.domain.model.SearchSettingEntry
import gambi.global.os26launcher.domain.repository.SystemSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M8d Task 3. Chỉ nhận trình xử lý của HỆ THỐNG (đo 2026-11-05: Oppo có app ngoài `com.instabridge.android` cũng đăng ký
 * `WIFI_SETTINGS`; Live 4 có hai action trả `ResolverActivity`), rồi mở bằng component cụ thể nên không bao giờ hiện hộp chọn app.
 * Không cần khai thêm `<queries>`: package Settings đã thấy được nhờ `SETTINGS`/`HOME_SETTINGS` (đo Task 1).
 */
@Singleton
class SystemSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : SystemSettingsRepository {

    private class Built(
        val locale: String,
        val entries: List<SearchSettingEntry>,
        val targets: Map<String, Intent>
    )

    @Volatile
    private var built: Built? = null

    override suspend fun entries(): List<SearchSettingEntry> = withContext(io) {
        // LG: khóa theo ngôn ngữ app (không phải Locale.getDefault): đổi ngôn ngữ trong app thì dựng lại tên mục
        val tag = AppLocaleContext.localeKey(context)
        built?.takeIf { it.locale == tag }?.entries ?: build(tag).also { built = it }.entries
    }

    override fun open(id: String): Boolean {
        val intent = built?.targets?.get(id) ?: return false
        return try {
            context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: RuntimeException) { // ActivityNotFoundException, SecurityException: ROM không cho mở dù resolve được
            Log.w(TAG, "open($id) failed", e)
            false
        }
    }

    private fun build(localeTag: String): Built {
        val pm = context.packageManager
        val strings = AppLocaleContext.wrap(context)
        val entries = ArrayList<SearchSettingEntry>()
        val targets = HashMap<String, Intent>()
        for (item in SystemSettingsCatalog.items) {
            try {
                val probe = Intent(item.action)
                val handlers = pm.queryIntentActivities(probe, 0).filter {
                    (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && it.activityInfo.name != RESOLVER
                }
                val preferred = pm.resolveActivity(probe, 0)?.activityInfo
                    ?.takeIf { info -> handlers.any { it.activityInfo.name == info.name && it.activityInfo.packageName == info.packageName } }
                val chosen = preferred ?: handlers.firstOrNull()?.activityInfo ?: continue
                targets[item.id] =
                    Intent(item.action).setComponent(ComponentName(chosen.packageName, chosen.name))
                entries.add(
                    SearchSettingEntry(
                        item.id,
                        strings.getString(item.label),
                        strings.getString(item.keywords).split(',').map { it.trim() }
                            .filter { it.isNotEmpty() }
                    )
                )
            } catch (e: RuntimeException) {
                Log.w(TAG, "skip ${item.id}", e)
            }
        }
        return Built(localeTag, entries, targets)
    }

    private companion object {
        const val TAG = "SystemSettingsRepo"
        const val RESOLVER = "com.android.internal.app.ResolverActivity"
    }
}
