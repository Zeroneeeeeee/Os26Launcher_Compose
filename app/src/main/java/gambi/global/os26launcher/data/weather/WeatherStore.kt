package gambi.global.os26launcher.data.weather

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.HourlyPoint
import gambi.global.os26launcher.domain.model.weather.WeatherCondition
import gambi.global.os26launcher.domain.model.weather.WeatherPlaces
import gambi.global.os26launcher.domain.model.weather.WeatherSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bộ nhớ đệm của MỘT địa điểm. [place] là nơi mà [lastAttemptAt]/[lastResult]/[snapshot] nói về: mục chỉ hợp lệ khi địa điểm đang tra
 * còn đúng tọa độ này (đổi vị trí tự động / chọn thành phố khác thì mục cũ bị bỏ). [lastResult] = "ok" hoặc tên `WeatherError`.
 */
data class StoredWeatherEntry(
    val place: GeoPlace,
    val snapshot: WeatherSnapshot?,
    val lastAttemptAt: Long,
    val lastResult: String
)

/**
 * Nội dung file `weather.json`. [entries]: mã địa điểm ([WeatherPlaces.CURRENT] hoặc mã thành phố) -> bộ nhớ đệm.
 * [legacyPlace]: địa điểm của file G3e version 1 (chỉ có khi vừa đọc file version 1), để [WeatherPlacesRepositoryImpl] di trú.
 */
data class StoredWeather(
    val entries: Map<String, StoredWeatherEntry>,
    val legacyPlace: GeoPlace? = null
)

/**
 * G3e/WX — bộ nhớ đệm thời tiết cho từng địa điểm + dấu thời gian (D3). Một file JSON trong `noBackupFilesDir` (không vào sao lưu đám mây:
 * tọa độ là dữ liệu cá nhân), ghi nguyên tử bằng [AtomicFile], cùng kiểu `LabelCache`. File hỏng/sai version -> coi như không có,
 * không crash. TUYỆT ĐỐI không log nội dung (có tọa độ).
 *
 * Version 2 (WX): `{"v":2,"entries":{"<placeId>":{"place","snapshot","attempt","result"}}}`. Version 1 (G3e, một địa điểm) đọc được:
 * thành `entries["current"]`; luôn ghi version 2.
 */
@Singleton
class WeatherStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    /** Chặn I/O: gọi từ luồng nền. */
    fun load(): StoredWeather? = try {
        if (!file.baseFile.exists()) {
            null
        } else {
            val root = JSONObject(String(file.readFully(), Charsets.UTF_8))
            when (root.getInt("v")) {
                VERSION -> StoredWeather(readEntries(root.optJSONObject("entries")))
                VERSION_1 -> readVersion1(root)
                else -> null
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "load failed: ${e.javaClass.simpleName}")
        null
    }

    /** Chặn I/O: gọi từ luồng nền. Lỗi ghi chỉ log loại ngoại lệ. */
    fun save(data: StoredWeather) {
        var out: FileOutputStream? = null
        try {
            val root = JSONObject().apply {
                put("v", VERSION)
                put("entries", JSONObject().apply {
                    for ((id, e) in data.entries) {
                        put(id, JSONObject().apply {
                            put("place", writePlace(e.place))
                            e.snapshot?.let { put("snapshot", writeSnapshot(it)) }
                            put("attempt", e.lastAttemptAt)
                            put("result", e.lastResult)
                        })
                    }
                })
            }
            out = file.startWrite()
            out.write(root.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            Log.w(TAG, "save failed: ${e.javaClass.simpleName}")
            if (out != null) file.failWrite(out)
        }
    }

    /** Một mục hỏng chỉ làm mất mục đó (địa điểm tải lại), không bỏ cả file. */
    private fun readEntries(o: JSONObject?): Map<String, StoredWeatherEntry> {
        if (o == null) return emptyMap()
        val map = LinkedHashMap<String, StoredWeatherEntry>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            try {
                val e = o.getJSONObject(id)
                map[id] = StoredWeatherEntry(
                    place = readPlace(e.getJSONObject("place")),
                    snapshot = e.optJSONObject("snapshot")?.let(::readSnapshot),
                    lastAttemptAt = e.optLong("attempt", 0L),
                    lastResult = e.optString("result", "ok")
                )
            } catch (ex: Exception) {
                Log.w(TAG, "entry skipped: ${ex.javaClass.simpleName}")
            }
        }
        return map
    }

    /** G3e: `place`, `snapshot`, `attempt`, `result` ở cấp gốc. Không có địa điểm thì không có mục nào. */
    private fun readVersion1(root: JSONObject): StoredWeather {
        val place = root.optJSONObject("place")?.let(::readPlace)
        val snapshot = root.optJSONObject("snapshot")?.let(::readSnapshot)
        val spot = place ?: snapshot?.place ?: return StoredWeather(emptyMap(), null)
        val entry = StoredWeatherEntry(
            place = spot,
            snapshot = snapshot,
            lastAttemptAt = root.optLong("attempt", 0L),
            lastResult = root.optString("result", "ok")
        )
        return StoredWeather(mapOf(WeatherPlaces.CURRENT to entry), legacyPlace = place)
    }

    private fun writePlace(p: GeoPlace) = GeoPlaceJson.write(p)

    private fun readPlace(o: JSONObject) = GeoPlaceJson.read(o)

    private fun writeSnapshot(s: WeatherSnapshot) = JSONObject().apply {
        put("p", writePlace(s.place))
        put("t", s.tempC)
        put("c", s.condition.name)
        put("night", s.isNight)
        put("hi", s.highC)
        put("lo", s.lowC)
        put("tz", s.utcOffsetSeconds)
        put("at", s.fetchedAtMillis)
        put(
            "h",
            JSONArray().apply {
                for (h in s.hourly) {
                    put(JSONObject().apply {
                        put("e", h.epochSeconds)
                        put("t", h.tempC)
                        put("c", h.condition.name)
                        put("night", h.isNight)
                    })
                }
            }
        )
        put("f", writePoints(s.forecast)) // WN: mọi mốc dự báo (tùy chọn khi đọc: cache cũ không có)
    }

    private fun writePoints(points: List<HourlyPoint>) = JSONArray().apply {
        for (h in points) {
            put(JSONObject().apply {
                put("e", h.epochSeconds)
                put("t", h.tempC)
                put("c", h.condition.name)
                put("night", h.isNight)
            })
        }
    }

    private fun readPoints(arr: JSONArray?): List<HourlyPoint> =
        if (arr == null) emptyList() else (0 until arr.length()).map {
            val h = arr.getJSONObject(it)
            HourlyPoint(h.getLong("e"), h.getDouble("t"), condition(h.getString("c")), h.getBoolean("night"))
        }

    private fun readSnapshot(o: JSONObject): WeatherSnapshot {
        val arr = o.getJSONArray("h")
        return WeatherSnapshot(
            place = readPlace(o.getJSONObject("p")),
            tempC = o.getDouble("t"),
            condition = condition(o.getString("c")),
            isNight = o.getBoolean("night"),
            highC = o.getDouble("hi"),
            lowC = o.getDouble("lo"),
            hourly = (0 until arr.length()).map {
                val h = arr.getJSONObject(it)
                HourlyPoint(
                    h.getLong("e"),
                    h.getDouble("t"),
                    condition(h.getString("c")),
                    h.getBoolean("night")
                )
            },
            utcOffsetSeconds = o.getInt("tz"),
            fetchedAtMillis = o.getLong("at"),
            forecast = readPoints(o.optJSONArray("f"))
        )
    }

    private fun condition(name: String): WeatherCondition =
        runCatching { WeatherCondition.valueOf(name) }.getOrDefault(WeatherCondition.UNKNOWN)

    private companion object {
        const val FILE_NAME = "weather.json"
        const val VERSION = 2
        const val VERSION_1 = 1
        const val TAG = "G3E"
    }
}
