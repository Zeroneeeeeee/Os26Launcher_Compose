package gambi.global.os26launcher.data.search

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.data.config.SearchKeys
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.RecentApp
import gambi.global.os26launcher.data.layout.db.HiddenAppDao
import gambi.global.os26launcher.domain.repository.RecentAppsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M8d Task 5. Lịch sử mở app nằm trong MỘT FILE ở `noBackupFilesDir` (ghi nguyên tử bằng [AtomicFile], như `LabelCache`), KHÔNG ở
 * DataStore: `launcher_prefs` được Auto Backup sao lưu, còn lịch sử phải chỉ nằm ở máy. Mỗi dòng
 * `serial|package|class|count|lastMs`; dòng hỏng bị bỏ qua. Cờ bật/tắt "gợi ý" vẫn ở DataStore (không nhạy cảm).
 * Mọi thao tác đi qua [mutex]; trạng thái giữ trong bộ nhớ ([state]) để UI quan sát.
 */
@Singleton
class RecentAppsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
    private val hiddenAppDao: HiddenAppDao // P3-7: không ghi app đang ẩn
) : RecentAppsRepository {

    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))
    private val mutex = Mutex()
    private var loaded = false
    private val state = MutableStateFlow<List<RecentApp>>(emptyList())

    override val entries: Flow<List<RecentApp>> = state.asStateFlow()

    init {
        scope.launch(io) { mutex.withLock { ensureLoaded() } }
    }

    override fun record(key: ComponentKey) {
        scope.launch(io) {
            mutex.withLock {
                ensureLoaded()
                val recentOn = try {
                    val p = dataStore.data.first()
                    (p[SearchKeys.RECENT_APPS] ?: p[SearchKeys.SUGGESTIONS]) != false // P3-5: khóa mới, chưa có thì theo khóa cũ
                } catch (e: IOException) {
                    true
                }
                if (!recentOn) return@withLock // đã tắt "App gần đây": không ghi
                // P3-7: app ẩn mở từ màn "Ứng dụng đã ẩn" không được để lại dấu vết trong lịch sử.
                val hidden = try {
                    hiddenAppDao.isHidden(key.packageName, key.className, key.userSerial)
                } catch (e: RuntimeException) {
                    true // không đọc được thì coi như ẩn: thà thiếu một mục lịch sử còn hơn lộ app ẩn
                }
                if (hidden) return@withLock
                val list = state.value.toMutableList()
                val i = list.indexOfFirst { it.key == key }
                val now = System.currentTimeMillis()
                if (i >= 0) {
                    val old = list.removeAt(i)
                    list.add(
                        0,
                        old.copy(launchCount = old.launchCount + 1, lastLaunchedMillis = now)
                    )
                } else {
                    list.add(0, RecentApp(key, 1, now))
                }
                val next = list.sortedByDescending { it.lastLaunchedMillis }
                    .take(RecentAppsRepository.MAX_ENTRIES)
                state.value = next
                write(next)
            }
        }
    }

    override suspend fun remove(key: ComponentKey) {
        withContext(io) {
            mutex.withLock {
                ensureLoaded()
                val next = state.value.filterNot { it.key == key }
                if (next.size == state.value.size) return@withLock
                state.value = next
                write(next)
            }
        }
    }

    override suspend fun clear() {
        withContext(io) {
            mutex.withLock {
                ensureLoaded()
                state.value = emptyList()
                try {
                    file.delete()
                } catch (e: Exception) {
                    Log.w(TAG, "clear: delete failed", e)
                }
            }
        }
    }

    /** Gọi trong [mutex]. Đọc file một lần; dọn khóa DataStore cũ (bản trước đây từng lưu ở đó và bị backup). */
    private suspend fun ensureLoaded() {
        if (loaded) return
        loaded = true
        state.value = try {
            if (file.baseFile.exists()) decode(
                String(
                    file.readFully(),
                    Charsets.UTF_8
                )
            ) else emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "recent apps unreadable, starting empty", e)
            emptyList()
        }
        try {
            dataStore.edit {
                if (it.contains(SearchKeys.LEGACY_RECENT_APPS)) it.remove(
                    SearchKeys.LEGACY_RECENT_APPS
                )
            }
        } catch (e: IOException) {
            Log.w(TAG, "legacy key cleanup failed", e)
        }
    }

    private fun write(list: List<RecentApp>) {
        val out = try {
            file.startWrite()
        } catch (e: IOException) {
            Log.w(TAG, "record: cannot open file", e)
            return
        }
        try {
            out.write(encode(list).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: IOException) {
            Log.w(TAG, "record: write failed", e)
            file.failWrite(out)
        }
    }

    private fun encode(list: List<RecentApp>): String = list.joinToString("\n") {
        "${it.key.userSerial}|${it.key.packageName}|${it.key.className}|${it.launchCount}|${it.lastLaunchedMillis}"
    }

    private fun decode(raw: String): List<RecentApp> {
        if (raw.isEmpty()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val p = line.split('|')
            if (p.size != 5) return@mapNotNull null
            val serial = p[0].toLongOrNull() ?: return@mapNotNull null
            val count = p[3].toIntOrNull() ?: return@mapNotNull null
            val last = p[4].toLongOrNull() ?: return@mapNotNull null
            RecentApp(ComponentKey(p[1], p[2], serial), count, last)
        }.toList()
    }

    private companion object {
        const val TAG = "RecentAppsRepo"
        const val FILE_NAME = "search_recent_apps.txt"
    }
}
