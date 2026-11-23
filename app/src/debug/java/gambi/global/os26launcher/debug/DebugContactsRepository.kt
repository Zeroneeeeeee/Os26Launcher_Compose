package gambi.global.os26launcher.debug

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.data.search.ContactAvatar
import gambi.global.os26launcher.data.search.ContactsRepositoryImpl
import gambi.global.os26launcher.debug.FakeContactsSource.Mode
import gambi.global.os26launcher.domain.model.ContactEntry
import gambi.global.os26launcher.domain.repository.ContactsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CP — CHỈ BẢN DEBUG (src/debug, không có trong release). Mặc định chế độ OFF = danh bạ thật (hành vi như bản release).
 * Chế độ SMALL/LOAD dùng [FakeContactsSource]: báo đã có quyền, KHÔNG đọc ContactsContract, KHÔNG mở app Danh bạ/Quay số
 * (openContact/dial/message trả false nên lớp tìm kiếm hiện Toast lỗi và giữ nguyên). Độ trễ delay() chỉ mô phỏng, không phải
 * số đo provider.
 */
@Singleton
class DebugContactsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val real: ContactsRepositoryImpl,
    private val fake: FakeContactsSource,
    @IoDispatcher private val io: CoroutineDispatcher
) : ContactsRepository {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile
    private var mode: Mode = Mode.parse(prefs.getString(KEY_MODE, null))

    private val modeChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Gọi từ [FakeContactsModeReceiver]. Chỉ lưu chuỗi chế độ (không phải dữ liệu danh bạ) để sống qua khởi động lại tiến trình. */
    fun setMode(newMode: Mode) {
        mode = newMode
        prefs.edit().putString(KEY_MODE, newMode.name).apply()
        modeChanged.tryEmit(Unit)
    }

    private val faking: Boolean get() = mode != Mode.OFF

    override fun hasPermission(): Boolean = if (faking) true else real.hasPermission()

    override suspend fun loadNames(limit: Int): List<ContactEntry> {
        if (!faking) return real.loadNames(limit)
        delay(FAKE_NAMES_DELAY_MS)
        return fake.people(mode).take(limit).map { it.entry } // số đến ở pha 2 (loadPhones)
    }

    override suspend fun loadPhones(ids: Set<Long>): Map<Long, List<String>> {
        if (!faking) return real.loadPhones(ids)
        delay(FAKE_PHONES_DELAY_MS)
        val out = HashMap<Long, List<String>>()
        for (p in fake.people(mode)) if (p.entry.contactId in ids && p.phones.isNotEmpty()) out[p.entry.contactId] = p.phones
        return out
    }

    override fun changes(): Flow<Unit> = merge(real.changes(), modeChanged)

    override suspend fun avatar(entry: ContactEntry, sizePx: Int): Bitmap =
        if (faking) withContext(io) { ContactAvatar.initialsCircle(entry.name, sizePx) } else real.avatar(entry, sizePx)

    override fun openContact(entry: ContactEntry): Boolean = if (faking) false else real.openContact(entry)
    override fun dial(number: String): Boolean = if (faking) false else real.dial(number)
    override fun message(number: String): Boolean = if (faking) false else real.message(number)
    override fun canCallAndMessage(): Boolean = if (faking) true else real.canCallAndMessage()

    private companion object {
        const val PREFS = "cp_debug_fake_contacts"
        const val KEY_MODE = "mode"
        const val FAKE_NAMES_DELAY_MS = 300L
        const val FAKE_PHONES_DELAY_MS = 400L
    }
}
