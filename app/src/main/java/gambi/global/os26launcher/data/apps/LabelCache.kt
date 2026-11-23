package gambi.global.os26launcher.data.apps

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.domain.model.ComponentKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LC: cache nhãn app trên đĩa (file JSON trong noBackupFilesDir, ghi nguyên tử bằng [AtomicFile]).
 *
 * Cache CHỈ giữ chuỗi nhãn. Danh sách khóa luôn do `getActivityList` quyết định; cache chỉ trả lời "nhãn của khóa
 * này với [Entry.stamp] này là gì". Header ghi locale và Build.FINGERPRINT: khác một trong hai thì bỏ cả cache.
 * Mọi lỗi đọc/ghi chỉ ghi log và rơi về đường nạp cũ, không ném ra ngoài.
 */
@Singleton
class LabelCache @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope
) {
    /** [stamp] = dấu vân tay của APK (sourceDir); đổi khi app được cập nhật. */
    data class Entry(val label: String, val stamp: String)

    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))
    private val lock = Any()
    private var pending: Map<ComponentKey, Entry>? = null
    private var writing = false

    // Nội dung cache đã biết đang nằm trên đĩa (đọc được hoặc vừa ghi), để bỏ qua lần ghi không đổi.
    @Volatile
    private var onDisk: Map<ComponentKey, Entry>? = null

    /** Chặn, chỉ gọi từ luồng nền. null = thiếu, hỏng, sai version, khác locale hoặc khác fingerprint. */
    fun load(): Map<ComponentKey, Entry>? {
        return try {
            if (!file.baseFile.exists()) return null
            val root = JSONObject(String(file.readFully(), Charsets.UTF_8))
            // B9: mọi đường trả null phải bỏ luôn `onDisk`: nếu giữ bản cũ, nhãn mới trùng bản cũ (vd en-US -> en-GB) làm scheduleSave
            // bỏ qua lần ghi và header kẹt ở locale cũ, mọi lần nạp sau lại đọc đủ nhãn.
            if (root.getInt("v") != VERSION) {
                onDisk = null
                return null
            }
            if (root.getString("locale") != localeTag() || root.getString("fp") != Build.FINGERPRINT) {
                onDisk = null
                return null
            }
            val arr = root.getJSONArray("e")
            if (arr.length() != root.getInt("n")) error("count mismatch")
            val map = HashMap<ComponentKey, Entry>(arr.length() * 2)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                map[ComponentKey(o.getString("p"), o.getString("c"), o.getLong("u"))] =
                    Entry(o.getString("l"), o.getString("s"))
            }
            onDisk = map
            map
        } catch (e: Exception) {
            Log.w(TAG, "label cache unreadable, falling back to full load", e)
            onDisk = null // B9
            try {
                file.delete()
            } catch (e2: Exception) {
                Log.w(TAG, "label cache delete failed", e2)
            }
            null
        }
    }

    /** Ghi nền, gộp: đang ghi dở thì chỉ giữ bản mới nhất. Không ghi nếu giống bản đã có trên đĩa. */
    fun scheduleSave(entries: Map<ComponentKey, Entry>) {
        if (entries == onDisk) return
        synchronized(lock) {
            pending = entries
            if (writing) return
            writing = true
        }
        scope.launch(io) {
            while (true) {
                val next = synchronized(lock) {
                    val p = pending
                    pending = null
                    if (p == null) writing = false
                    p
                } ?: break
                if (next != onDisk) write(next)
            }
        }
    }

    private fun write(entries: Map<ComponentKey, Entry>) {
        val arr = JSONArray()
        for ((k, v) in entries) {
            arr.put(
                JSONObject().put("p", k.packageName).put("c", k.className).put("u", k.userSerial)
                    .put("s", v.stamp).put("l", v.label)
            )
        }
        val root =
            JSONObject().put("v", VERSION).put("locale", localeTag()).put("fp", Build.FINGERPRINT)
                .put("n", entries.size).put("e", arr)
        var out: FileOutputStream? = null
        try {
            out = file.startWrite()
            out.write(root.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
            onDisk = entries
        } catch (e: Exception) {
            Log.w(TAG, "label cache write failed", e)
            if (out != null) file.failWrite(out)
        }
    }

    private fun localeTag(): String = Locale.getDefault().toLanguageTag()

    private companion object {
        const val TAG = "LabelCache"
        const val FILE_NAME = "app_label_cache.json"
        const val VERSION = 1
    }
}
