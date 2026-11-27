package gambi.global.os26launcher.data.weather

import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.WeatherSnapshot
import gambi.global.os26launcher.domain.weather.WeatherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import javax.inject.Inject

/**
 * WM — cấu hình Open-Meteo từ `buildTypes` (`OPEN_METEO_BASE_URL`, `OPEN_METEO_API_KEY`). [apiKey] rỗng = gói miễn phí (`api.open-meteo.com`, không gửi
 * `apikey`); gói thương mại = `https://customer-api.open-meteo.com` + khóa.
 */
data class OpenMeteoConfig(val baseUrl: String, val apiKey: String)

/**
 * WM — Open-Meteo làm nguồn DỰ PHÒNG cho [FallbackWeatherProvider]: chỉ [fetch] (một lệnh `v1/forecast`, OWM cần hai). Không có tìm thành phố /
 * tra tên nơi (plan WM P3): [search] trả rỗng, [reverse] trả null. Không cần khóa ở gói miễn phí nên không ném `NO_KEY`. URL có thể chứa khóa:
 * như [WeatherHttp], không log URL.
 */
class OpenMeteoProvider @Inject constructor(
    private val config: OpenMeteoConfig,
    @IoDispatcher private val io: CoroutineDispatcher
) : WeatherProvider {

    override suspend fun search(query: String, limit: Int): List<GeoPlace> = emptyList()

    override suspend fun reverse(lat: Double, lon: Double): GeoPlace? = null

    override suspend fun fetch(place: GeoPlace): WeatherSnapshot = withContext(io) {
        val json = WeatherHttp.get(
            url(
                "v1/forecast",
                "latitude" to place.lat.toString(),
                "longitude" to place.lon.toString(),
                "current" to VARIABLES,
                "hourly" to VARIABLES,
                "forecast_hours" to OpenMeteoParser.FORECAST_HOURS.toString(),
                "timezone" to "auto",
                "timeformat" to "unixtime"
            )
        )
        OpenMeteoParser.snapshot(place, json, System.currentTimeMillis())
    }

    private fun url(path: String, vararg params: Pair<String, String>): String {
        val sb = StringBuilder(config.baseUrl.trimEnd('/')).append('/').append(path).append('?')
        for ((k, v) in params) sb.append(k).append('=').append(URLEncoder.encode(v, "UTF-8")).append('&')
        if (config.apiKey.isNotBlank()) sb.append("apikey=").append(URLEncoder.encode(config.apiKey, "UTF-8")).append('&')
        return sb.trimEnd('&').toString()
    }

    private companion object {
        /** Cùng bộ biến cho `current` và `hourly`. */
        const val VARIABLES = "temperature_2m,weather_code,is_day"
    }
}
