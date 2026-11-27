package gambi.global.os26launcher.data.weather

import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.HourlyPoint
import gambi.global.os26launcher.domain.model.weather.WeatherCondition
import gambi.global.os26launcher.domain.model.weather.WeatherError
import gambi.global.os26launcher.domain.model.weather.WeatherException
import gambi.global.os26launcher.domain.model.weather.WeatherSnapshot
import org.json.JSONException
import org.json.JSONObject

/**
 * WM — parse JSON `v1/forecast` của Open-Meteo (`current` + `hourly`, `timeformat=unixtime`) thành [WeatherSnapshot] CÙNG NGHĨA với bản OWM
 * ([OwmParser]): dự báo theo giờ được lấy mỗi [STEP_HOURS] giờ một mốc, bắt đầu từ giờ KẾ TIẾP, tối đa [OwmParser.FORECAST_COUNT] mốc (≈ 36 giờ),
 * để cao/thấp (8 mốc đầu) và thông báo (khung 3 giờ, WN) giữ nguyên cách tính (plan WM P4). Trường thiếu / sai kiểu / null -> [WeatherError.PARSE].
 */
internal object OpenMeteoParser {
    /** Số giờ xin về: mốc cuối là chỉ số [FIRST_INDEX] + 3 × 11 = 34. */
    const val FORECAST_HOURS = 36

    private const val STEP_HOURS = 3

    /** `forecast_hours` tính từ giờ HIỆN TẠI (đã qua một phần); OWM trả mốc đầu ở tương lai nên bỏ chỉ số 0. */
    private const val FIRST_INDEX = 1

    fun snapshot(place: GeoPlace, json: String, nowMillis: Long): WeatherSnapshot = guard {
        val root = JSONObject(json)
        val cur = root.getJSONObject("current")
        val temp = finite(cur.getDouble("temperature_2m"))
        val hourly = root.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        val days = hourly.getJSONArray("is_day")
        val n = minOf(minOf(times.length(), temps.length()), minOf(codes.length(), days.length()))
        val points = (FIRST_INDEX until n step STEP_HOURS).take(OwmParser.FORECAST_COUNT).map { i ->
            HourlyPoint(
                epochSeconds = times.getLong(i),
                tempC = finite(temps.getDouble(i)),
                condition = WeatherCondition.fromWmoCode(codes.getInt(i)),
                isNight = days.getInt(i) == 0
            )
        }
        if (points.isEmpty()) throw WeatherException(WeatherError.PARSE)
        val window = points.take(OwmParser.HIGH_LOW_STEPS).map { it.tempC } + temp
        WeatherSnapshot(
            place = place,
            tempC = temp,
            condition = WeatherCondition.fromWmoCode(cur.getInt("weather_code")),
            isNight = cur.getInt("is_day") == 0,
            highC = window.max(),
            lowC = window.min(),
            hourly = points.take(OwmParser.HOURLY_STEPS),
            utcOffsetSeconds = root.optInt("utc_offset_seconds", 0),
            fetchedAtMillis = nowMillis,
            forecast = points
        )
    }

    /** NaN/Infinity -> PARSE (như [OwmParser]). */
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
