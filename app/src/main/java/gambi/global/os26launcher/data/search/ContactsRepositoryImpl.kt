package gambi.global.os26launcher.data.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.OperationCanceledException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.domain.model.ContactEntry
import gambi.global.os26launcher.domain.repository.ContactsRepository
import gambi.global.os26launcher.domain.search.PhoneMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * G3a Task 1. Chỉ ĐỌC `ContactsContract`, không lưu gì, không log tên/số (chỉ tên lớp ngoại lệ, tag `G3A`).
 * Mọi `SecurityException` (mất quyền giữa chừng) coi như chưa cấp quyền. Không cần `<queries>`: chỉ `startActivity`, không `resolveActivity`.
 */
@Singleton
class ContactsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : ContactsRepository {

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * C7: chạy một truy vấn chặn ở IO với [CancellationSignal]; coroutine bị hủy (vd phím mới làm `mapLatest` hủy phép tính, hoặc
     * `close()`/ON_STOP hủy việc nạp) thì hủy cả lời gọi `ContentResolver.query` đang chạy thay vì để nó chạy hết.
     */
    private suspend fun <T> queryCancellable(block: (CancellationSignal) -> T): T = coroutineScope {
        val signal = CancellationSignal()
        val job = async(io) { block(signal) }
        try {
            job.await()
        } catch (e: CancellationException) {
            signal.cancel()
            throw e
        }
    }

    override suspend fun loadNames(limit: Int): List<ContactEntry> = queryCancellable { signal ->
        if (!hasPermission()) return@queryCancellable emptyList()
        val uri = ContactsContract.Contacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, limit.toString()).build()
        val out = ArrayList<ContactEntry>()
        try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
                ),
                "${ContactsContract.Contacts.IN_VISIBLE_GROUP} = 1",
                null,
                ContactsContract.Contacts.SORT_KEY_PRIMARY,
                signal
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val key = c.getString(1) ?: continue
                    val name = c.getString(2)?.trim().orEmpty()
                    if (name.isEmpty()) continue
                    out.add(ContactEntry(c.getLong(0), key, name, c.getString(3)))
                }
            }
        } catch (e: OperationCanceledException) { // đã bị hủy chủ ý: không phải lỗi, không log
            return@queryCancellable emptyList()
        } catch (e: SecurityException) {
            Log.w(TAG, "contacts permission lost while loading names")
            return@queryCancellable emptyList()
        } catch (e: RuntimeException) {
            Log.w(TAG, "loadNames failed: ${e.javaClass.simpleName}")
            return@queryCancellable emptyList()
        }
        out
    }

    override suspend fun loadPhones(ids: Set<Long>): Map<Long, List<String>> = queryCancellable { signal ->
        if (ids.isEmpty() || !hasPermission()) return@queryCancellable emptyMap()
        // CP: MỘT truy vấn toàn bảng Phone (cùng cột với truy vấn G3a đã chạy trên Live 4 + Oppo, chỉ bỏ điều kiện CONTACT_ID IN);
        // ghép theo contactId trong RAM, bỏ id ngoài nhóm hiển thị. ColorOS bỏ ORDER BY: thứ tự cuối quyết định trong code.
        var capped = false
        val byContact = HashMap<Long, ArrayList<PhoneChoice>>()
        try {
            context.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.CONTACT_ID, Phone.NUMBER, Phone.IS_SUPER_PRIMARY, Phone.IS_PRIMARY, Phone._ID),
                null,
                null,
                "${Phone.IS_SUPER_PRIMARY} DESC, ${Phone.IS_PRIMARY} DESC, ${Phone._ID} ASC",
                signal
            )?.use { c ->
                var rows = 0
                while (c.moveToNext()) {
                    if (++rows > MAX_PHONE_ROWS) {
                        capped = true
                        break
                    }
                    val contactId = c.getLong(0)
                    if (contactId !in ids) continue
                    val number = c.getString(1)?.trim().orEmpty()
                    if (number.isEmpty()) continue
                    byContact.getOrPut(contactId) { ArrayList(2) }
                        .add(PhoneChoice(number, c.getInt(2) != 0, c.getInt(3) != 0, c.getLong(4)))
                }
            }
        } catch (e: OperationCanceledException) { // hủy chủ ý: không log, không coi là lỗi
            return@queryCancellable emptyMap()
        } catch (e: SecurityException) {
            Log.w(TAG, "contacts permission lost while loading numbers")
            return@queryCancellable emptyMap()
        } catch (e: RuntimeException) {
            Log.w(TAG, "loadPhones failed: ${e.javaClass.simpleName}")
            return@queryCancellable emptyMap()
        }
        if (capped) Log.w(TAG, "loadPhones hit the row cap ($MAX_PHONE_ROWS)")
        val out = HashMap<Long, List<String>>(byContact.size * 2)
        for ((id, choices) in byContact) {
            choices.sortWith { a, b -> if (a.beats(b)) -1 else if (b.beats(a)) 1 else 0 }
            val seen = HashSet<String>(choices.size * 2)
            val numbers = ArrayList<String>(minOf(choices.size, MAX_PHONES_PER_CONTACT))
            for (ch in choices) {
                if (numbers.size >= MAX_PHONES_PER_CONTACT) break
                if (seen.add(PhoneMatcher.canonical(ch.number))) numbers.add(ch.number) // bỏ số trùng sau khi chuẩn hóa
            }
            if (numbers.isNotEmpty()) out[id] = numbers
        }
        out
    }

    override fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                observer
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "observer not registered: ${e.javaClass.simpleName}")
        }
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    override suspend fun avatar(entry: ContactEntry, sizePx: Int): Bitmap = withContext(io) {
        val photo = entry.photoUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(Uri.parse(uri))
                    ?.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) { // SecurityException, FileNotFoundException, ảnh hỏng: về avatar chữ cái đầu
                null
            }
        }
        if (photo != null) ContactAvatar.circleCrop(
            photo,
            sizePx
        ) else ContactAvatar.initialsCircle(entry.name, sizePx)
    }

    override fun openContact(entry: ContactEntry): Boolean {
        val uri =
            ContactsContract.Contacts.getLookupUri(entry.contactId, entry.lookupKey) ?: return false
        return start(Intent(Intent.ACTION_VIEW, uri))
    }

    override fun dial(number: String): Boolean =
        start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

    override fun message(number: String): Boolean =
        start(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))

    override fun canCallAndMessage(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: RuntimeException) { // ActivityNotFoundException, SecurityException
        Log.w(TAG, "start failed: ${e.javaClass.simpleName}")
        false
    }

    /** Số của một người: số mặc định (super primary) thắng số chính (primary), rồi _ID nhỏ nhất. */
    private class PhoneChoice(
        val number: String,
        val superPrimary: Boolean,
        val primary: Boolean,
        val id: Long
    ) {
        fun beats(other: PhoneChoice): Boolean = when {
            superPrimary != other.superPrimary -> superPrimary
            primary != other.primary -> primary
            else -> id < other.id
        }
    }

    private companion object {
        const val TAG = "G3A"
        const val MAX_PHONES_PER_CONTACT = 10
        const val MAX_PHONE_ROWS = 50_000
    }
}
