package gambi.global.os26launcher.data.config

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** CF — đọc một nhóm: lỗi đọc file (hỏng, IO) trả mặc định thay vì để Flow chết kéo cả màn hình theo; chỉ phát khi nhóm đổi. */
internal fun <T> DataStore<Preferences>.groupFlow(tag: String, read: (Preferences) -> T): Flow<T> = data
    .catch { e ->
        if (e is IOException) {
            Log.w(tag, "read failed, using defaults", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }
    .map { read(it) }
    .distinctUntilChanged()

/**
 * CF — ghi một nhóm trong MỘT `edit`: đọc giá trị hiện tại bằng chính [read] (nhất quán với Flow), áp [transform], rồi [write] chỉ ghi
 * khóa có giá trị đổi. Không đổi gì thì không ghi.
 */
internal suspend fun <T> DataStore<Preferences>.updateGroup(
    read: (Preferences) -> T,
    transform: (T) -> T,
    write: MutablePreferences.(old: T, new: T) -> Unit
) {
    edit { prefs ->
        val old = read(prefs)
        val new = transform(old)
        if (new != old) prefs.write(old, new)
    }
}

internal fun <V : Any> MutablePreferences.putIfChanged(key: Preferences.Key<V>, old: V, new: V) {
    if (old != new) this[key] = new
}

/** null (hoặc chuỗi trống) = xóa khóa. */
internal fun MutablePreferences.putNullableIfChanged(key: Preferences.Key<String>, old: String?, new: String?) {
    if (old == new) return
    if (new.isNullOrBlank()) remove(key) else this[key] = new
}

internal fun <E : Enum<E>> MutablePreferences.putEnumIfChanged(key: Preferences.Key<String>, old: E, new: E) {
    if (old != new) this[key] = new.name
}

/** Enum có thể vắng (null = xóa khóa). */
internal fun <E : Enum<E>> MutablePreferences.putNullableEnumIfChanged(key: Preferences.Key<String>, old: E?, new: E?) {
    if (old == new) return
    if (new == null) remove(key) else this[key] = new.name
}

internal fun Preferences.percent(key: Preferences.Key<Int>, default: Int, min: Int = 0, max: Int = 100): Int =
    (this[key] ?: default).coerceIn(min, max)
