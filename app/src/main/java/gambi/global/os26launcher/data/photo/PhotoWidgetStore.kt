package gambi.global.os26launcher.data.photo

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.domain.model.photo.CropRect
import gambi.global.os26launcher.domain.model.photo.PhotoRef
import gambi.global.os26launcher.domain.model.photo.PhotoWidgetConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * G3b (D1) — cấu hình ảnh của từng widget Ảnh: một file JSON trong `noBackupFilesDir` (không vào sao lưu), ghi nguyên tử bằng [AtomicFile], cùng
 * kiểu `WeatherPlacesStore`. File hỏng/sai version -> coi như không có, không crash. TUYỆT ĐỐI không log nội dung.
 */
@Singleton
class PhotoWidgetStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    /** Chặn I/O: gọi từ luồng nền. null = chưa có file, hỏng, hoặc version lạ. */
    fun load(): Map<Long, PhotoWidgetConfig>? = try {
        if (!file.baseFile.exists()) {
            null
        } else {
            val root = JSONObject(String(file.readFully(), Charsets.UTF_8))
            if (root.getInt("v") != VERSION) null else readWidgets(root.optJSONObject("widgets"))
        }
    } catch (e: Exception) {
        Log.w(TAG, "load failed: ${e.javaClass.simpleName}")
        null
    }

    /** Chặn I/O: gọi từ luồng nền. Trả false nếu ghi lỗi (chỉ log loại ngoại lệ). */
    fun save(data: Map<Long, PhotoWidgetConfig>): Boolean {
        var out: FileOutputStream? = null
        return try {
            val widgets = JSONObject()
            for ((id, config) in data) {
                if (config.photos.isEmpty()) continue
                val photos = JSONArray()
                config.photos.forEach { p ->
                    photos.put(
                        JSONObject()
                            .put("id", p.id)
                            .put("l", p.crop.left.toDouble()).put("t", p.crop.top.toDouble())
                            .put("r", p.crop.right.toDouble()).put("b", p.crop.bottom.toDouble())
                            .put("aspect", p.aspect.toDouble())
                            .put("ver", p.version)
                    )
                }
                widgets.put(id.toString(), JSONObject().put("photos", photos))
            }
            out = file.startWrite()
            out.write(JSONObject().put("v", VERSION).put("widgets", widgets).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
            true
        } catch (e: Exception) {
            Log.w(TAG, "save failed: ${e.javaClass.simpleName}")
            if (out != null) file.failWrite(out)
            false
        }
    }

    /** Mục hỏng (khóa không phải số, id ảnh sai dạng, vùng cắt vô nghĩa) bị bỏ từng mục, không bỏ cả file. */
    private fun readWidgets(o: JSONObject?): Map<Long, PhotoWidgetConfig> {
        if (o == null) return emptyMap()
        val map = LinkedHashMap<Long, PhotoWidgetConfig>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val id = key.toLongOrNull()?.takeIf { it > 0L } ?: continue
            val arr = o.optJSONObject(key)?.optJSONArray("photos") ?: continue
            val photos = ArrayList<PhotoRef>()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val photoId = p.optString("id", "")
                if (!PhotoFileStore.isValidId(photoId)) continue
                val crop = CropRect(
                    p.optDouble("l", Double.NaN).toFloat(), p.optDouble("t", Double.NaN).toFloat(),
                    p.optDouble("r", Double.NaN).toFloat(), p.optDouble("b", Double.NaN).toFloat()
                )
                val aspect = p.optDouble("aspect", Double.NaN).toFloat()
                if (!crop.isValid || !aspect.isFinite() || aspect <= 0f) continue
                photos += PhotoRef(photoId, crop, aspect, p.optInt("ver", 0))
                if (photos.size >= PhotoWidgetConfig.MAX_PHOTOS) break
            }
            if (photos.isNotEmpty()) map[id] = PhotoWidgetConfig(photos)
        }
        return map
    }

    private companion object {
        const val FILE_NAME = "photo_widgets.json"
        const val VERSION = 1
        const val TAG = "PhotoWidgetStore"
    }
}
