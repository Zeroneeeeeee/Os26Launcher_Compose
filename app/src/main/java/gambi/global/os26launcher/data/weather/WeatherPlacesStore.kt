package gambi.global.os26launcher.data.weather

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.LocationMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Nội dung `weather_places.json`. [widgets]: `itemId` của hàng widget -> mã địa điểm (chỉ lưu các widget KHÔNG dùng "Vị trí hiện tại"). */
data class StoredPlaces(
    val mode: LocationMode,
    val autoPlace: GeoPlace?,
    val manualPlace: GeoPlace?,
    val saved: List<GeoPlace>,
    val widgets: Map<Long, String>,
    /** Xem [gambi.global.os26launcher.domain.model.weather.WeatherPlaces.autoFixAt]. */
    val autoFixAt: Long = 0L
)

/**
 * WX (D1/D3) — danh sách địa điểm người dùng theo dõi và widget nào hiện địa điểm nào. Một file JSON trong `noBackupFilesDir` (không vào sao lưu
 * đám mây: tọa độ là dữ liệu cá nhân), ghi nguyên tử bằng [AtomicFile], cùng kiểu [WeatherStore]. Tách khỏi `weather.json` (bộ nhớ đệm):
 * xóa bộ nhớ đệm không làm mất lựa chọn của người dùng. File hỏng/sai version -> coi như không có, không crash. TUYỆT ĐỐI không log nội dung.
 */
@Singleton
class WeatherPlacesStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    /** Chặn I/O: gọi từ luồng nền. null = chưa có file, hỏng, hoặc version lạ. */
    fun load(): StoredPlaces? = try {
        if (!file.baseFile.exists()) {
            null
        } else {
            val root = JSONObject(String(file.readFully(), Charsets.UTF_8))
            if (root.getInt("v") != VERSION) {
                null
            } else {
                StoredPlaces(
                    mode = LocationMode.fromName(root.optString("mode", "")),
                    autoPlace = root.optJSONObject("auto")?.let(GeoPlaceJson::read),
                    manualPlace = root.optJSONObject("manual")?.let(GeoPlaceJson::read),
                    saved = root.optJSONArray("saved")?.let { arr ->
                        (0 until arr.length()).map { GeoPlaceJson.read(arr.getJSONObject(it)) }
                    }.orEmpty(),
                    widgets = readWidgets(root.optJSONObject("widgets")),
                    autoFixAt = root.optLong("fixAt", 0L)
                )
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "load failed: ${e.javaClass.simpleName}")
        null
    }

    /** Chặn I/O: gọi từ luồng nền. Lỗi ghi chỉ log loại ngoại lệ. */
    fun save(data: StoredPlaces) {
        var out: FileOutputStream? = null
        try {
            val root = JSONObject().apply {
                put("v", VERSION)
                put("mode", data.mode.name)
                data.autoPlace?.let { put("auto", GeoPlaceJson.write(it)) }
                data.manualPlace?.let { put("manual", GeoPlaceJson.write(it)) }
                put("fixAt", data.autoFixAt)
                put("saved", JSONArray().apply { data.saved.forEach { put(GeoPlaceJson.write(it)) } })
                put("widgets", JSONObject().apply { data.widgets.forEach { (id, placeId) -> put(id.toString(), placeId) } })
            }
            out = file.startWrite()
            out.write(root.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            Log.w(TAG, "save failed: ${e.javaClass.simpleName}")
            if (out != null) file.failWrite(out)
        }
    }

    /** Khóa không phải số hoặc giá trị không phải chuỗi thì bỏ qua mục đó, không bỏ cả file. */
    private fun readWidgets(o: JSONObject?): Map<Long, String> {
        if (o == null) return emptyMap()
        val map = LinkedHashMap<Long, String>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val id = key.toLongOrNull() ?: continue
            val placeId = o.optString(key, "")
            if (placeId.isNotBlank()) map[id] = placeId
        }
        return map
    }

    private companion object {
        const val FILE_NAME = "weather_places.json"
        const val VERSION = 1
        const val TAG = "WeatherPlaces"
    }
}
