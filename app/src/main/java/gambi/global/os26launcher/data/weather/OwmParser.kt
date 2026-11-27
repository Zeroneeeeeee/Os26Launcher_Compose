package gambi.global.os26launcher.data.weather

import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.HourlyPoint
import gambi.global.os26launcher.domain.model.weather.WeatherCondition
import gambi.global.os26launcher.domain.model.weather.WeatherError
import gambi.global.os26launcher.domain.model.weather.WeatherException
import gambi.global.os26launcher.domain.model.weather.WeatherSnapshot
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** G3e — parse JSON của OpenWeatherMap thành mô hình domain. Trường thiếu/sai kiểu -> [WeatherError.PARSE], không NPE. */
internal object OwmParser {
    /**
     * Số mốc dự báo xin về. 8 mốc x 3 giờ = 24 giờ tới (cao/thấp của widget). WN: nâng lên 12 (36 giờ, vẫn MỘT lệnh `forecast`) để tóm tắt
     * "cả ngày mai" khi cache có thể cũ tới 2 giờ; cao/thấp và dải giờ của widget không đổi (chỉ dùng 8 và 4 mốc đầu).
     */
    const val FORECAST_COUNT = 12
    const val HIGH_LOW_STEPS = 8 // WM: OpenMeteoParser dùng chung

    /** Số mốc dải 4x2. */
    const val HOURLY_STEPS = 4

    fun roundCoord(v: Double): Double = Math.round(v * 100.0) / 100.0

    fun places(json: String): List<GeoPlace> = guard {
        val arr = JSONArray(json)
        (0 until arr.length()).map { place(arr.getJSONObject(it)) }
    }

    private fun place(o: JSONObject): GeoPlace {
        val vi = o.optJSONObject("local_names")?.optString("vi", "")?.takeIf { it.isNotBlank() }
        return GeoPlace(
            displayName = vi ?: o.getString("name"),
            lat = roundCoord(finite(o.getDouble("lat"))),
            lon = roundCoord(finite(o.getDouble("lon"))),
            countryCode = o.optString("country", "").ifBlank { null },
            admin = o.optString("state", "").ifBlank { null }
        )
    }

    fun snapshot(
        place: GeoPlace,
        currentJson: String,
        forecastJson: String,
        nowMillis: Long
    ): WeatherSnapshot = guard {
        val cur = JSONObject(currentJson)
        val w0 = cur.getJSONArray("weather").getJSONObject(0)
        val temp = finite(cur.getJSONObject("main").getDouble("temp"))
        val list = JSONObject(forecastJson).getJSONArray("list")
        val points = (0 until minOf(list.length(), FORECAST_COUNT)).map { i ->
            val e = list.getJSONObject(i)
            val w = e.getJSONArray("weather").getJSONObject(0)
            HourlyPoint(
                epochSeconds = e.getLong("dt"),
                tempC = finite(e.getJSONObject("main").getDouble("temp")),
                condition = WeatherCondition.fromOwmId(w.getInt("id")),
                isNight = w.optString("icon", "").endsWith("n")
            )
        }
        val window = points.take(HIGH_LOW_STEPS).map { it.tempC } + temp
        WeatherSnapshot(
            place = place,
            tempC = temp,
            condition = WeatherCondition.fromOwmId(w0.getInt("id")),
            isNight = w0.optString("icon", "").endsWith("n"),
            highC = window.max(),
            lowC = window.min(),
            hourly = points.take(HOURLY_STEPS),
            utcOffsetSeconds = cur.optInt("timezone", 0),
            fetchedAtMillis = nowMillis,
            forecast = points
        )
    }

    /** NaN/Infinity (JSON có chuỗi "NaN") -> PARSE; `roundToInt` ném với NaN còn Infinity ra số vô nghĩa (B1). */
    private fun finite(v: Double): Double {
        if (!v.isFinite()) throw WeatherException(WeatherError.PARSE)
        return v
    }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw WeatherException(WeatherError.PARSE)
    } catch (e: IllegalArgumentException) {
        throw WeatherException(WeatherError.PARSE)
    }
}
