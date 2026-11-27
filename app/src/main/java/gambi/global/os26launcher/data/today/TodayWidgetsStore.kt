package gambi.global.os26launcher.data.today

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.domain.model.AppWidgetRef
import gambi.global.os26launcher.domain.today.TodayWidget
import gambi.global.os26launcher.domain.today.TodayWidgetsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Delegate ở CẤP FILE (bài học DataStoreModule). File riêng `today_widgets`: không đụng khóa cài đặt chung (config-schema.md).
private val Context.todayWidgetsDataStore: DataStore<Preferences> by preferencesDataStore(name = "today_widgets")

/**
 * TV-3 — lưu danh sách widget của Today View thành một chuỗi `id|kind|w;id|kind|n;...` (w = 4 × 2, n = 2 × 2) và id kế tiếp.
 * Mục hỏng hoặc loại lạ bị bỏ khi đọc. Khóa [WIDGETS] chưa có = chưa sửa lần nào: phát bộ mặc định.
 *
 * TA: widget app khác ghi 7 trường `id|app|w|appWidgetId|package|class|userSerial` (tên gói / lớp Java không chứa `|` hay `;`). Bản cũ chỉ nhận
 * mục 3 trường nên bỏ qua các mục này.
 */
@Singleton
class TodayWidgetsStore @Inject constructor(
    @ApplicationContext private val context: Context
) : TodayWidgetsRepository {

    override val widgets: Flow<List<TodayWidget>> = context.todayWidgetsDataStore.data
        .map(::read)
        .distinctUntilChanged()

    override suspend fun add(kindId: String, wide: Boolean) {
        if (!TodayWidget.isKnownKind(kindId) || kindId == TodayWidget.APP) return
        append { id -> TodayWidget(id, kindId, wide) }
    }

    override suspend fun addAppWidget(appWidgetId: Int, provider: AppWidgetRef, wide: Boolean) {
        append { id -> TodayWidget(id, TodayWidget.APP, wide, appWidgetId, provider) }
    }

    private suspend fun append(make: (Long) -> TodayWidget) {
        context.todayWidgetsDataStore.edit { p ->
            val id = (p[NEXT_ID] ?: TodayWidget.FIRST_FREE_ID).coerceAtLeast(TodayWidget.FIRST_FREE_ID)
            p[NEXT_ID] = id + 1
            p[WIDGETS] = encode(read(p) + make(id))
        }
    }

    override suspend fun remove(id: Long): TodayWidget? {
        var removed: TodayWidget? = null
        context.todayWidgetsDataStore.edit { p ->
            val current = read(p)
            removed = current.firstOrNull { it.id == id }
            p[WIDGETS] = encode(current.filterNot { it.id == id })
        }
        return removed
    }

    override suspend fun reorder(ids: List<Long>) {
        context.todayWidgetsDataStore.edit { p ->
            val current = read(p)
            val byId = current.associateBy { it.id }
            val ordered = ids.distinct().mapNotNull { byId[it] }
            val rest = current.filterNot { w -> ordered.any { it.id == w.id } }
            p[WIDGETS] = encode(ordered + rest)
        }
    }

    override suspend fun importLegacy(kinds: List<Pair<String, Boolean>>): Boolean {
        var written = false
        context.todayWidgetsDataStore.edit { p ->
            if (p[WIDGETS] != null) return@edit // đã có dữ liệu của kho này: không ghi đè
            var id = (p[NEXT_ID] ?: TodayWidget.FIRST_FREE_ID).coerceAtLeast(TodayWidget.FIRST_FREE_ID)
            val list = kinds.filter { TodayWidget.isKnownKind(it.first) && it.first != TodayWidget.APP }.map { (kind, wide) -> TodayWidget(id++, kind, wide) }
            p[NEXT_ID] = id
            p[WIDGETS] = encode(list)
            written = true
        }
        return written
    }

    private fun read(p: Preferences): List<TodayWidget> {
        val raw = p[WIDGETS] ?: return TodayWidget.DEFAULT
        if (raw.isEmpty()) return emptyList()
        return raw.split(';').mapNotNull { entry ->
            val parts = entry.split('|')
            if (parts.size != 3 && parts.size != APP_PARTS) return@mapNotNull null
            val id = parts[0].toLongOrNull()?.takeIf(TodayWidget::isTodayId) ?: return@mapNotNull null
            val kind = parts[1].takeIf(TodayWidget::isKnownKind) ?: return@mapNotNull null
            val wide = parts[2] == WIDE
            if (kind == TodayWidget.APP) {
                if (parts.size != APP_PARTS) return@mapNotNull null
                val appWidgetId = parts[3].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                val pkg = parts[4].takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val serial = parts[6].toLongOrNull() ?: return@mapNotNull null
                TodayWidget(id, kind, wide, appWidgetId, AppWidgetRef(pkg, parts[5], serial))
            } else {
                if (parts.size != 3) return@mapNotNull null
                TodayWidget(id, kind, wide)
            }
        }.distinctBy { it.id }
    }

    private fun encode(list: List<TodayWidget>): String =
        list.joinToString(";") { w ->
            val base = "${w.id}|${w.kindId}|${if (w.wide) WIDE else NARROW}"
            val ref = w.provider
            if (w.kindId == TodayWidget.APP && w.appWidgetId != null && ref != null) {
                "$base|${w.appWidgetId}|${ref.packageName}|${ref.className}|${ref.userSerial}"
            } else base
        }

    private companion object {
        val WIDGETS = stringPreferencesKey("widgets")
        val NEXT_ID = longPreferencesKey("next_id")
        const val WIDE = "w"
        const val NARROW = "n"
        const val APP_PARTS = 7 // TA: id|app|w|appWidgetId|package|class|userSerial
    }
}
