package gambi.global.os26launcher.data.weather

import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.WeatherError
import gambi.global.os26launcher.domain.model.weather.WeatherException
import gambi.global.os26launcher.domain.model.weather.WeatherSnapshot
import gambi.global.os26launcher.domain.weather.WeatherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.Normalizer
import javax.inject.Inject

/** Cấu hình nguồn: đổi sang proxy = đổi [baseUrl] (và bỏ [apiKey]) ở `WeatherModule`. */
data class WeatherProviderConfig(val baseUrl: String, val apiKey: String)

/**
 * G3e — OpenWeatherMap, chỉ endpoint gói Free: `geo/1.0/direct`, `geo/1.0/reverse`, `data/2.5/weather`, `data/2.5/forecast`
 * (https://openweathermap.org/api, đọc 2026-11-09). Không dùng `weather?q=` (deprecated) và không dùng One Call 3.0 (cần gói riêng).
 * Khóa rỗng -> NO_KEY TRƯỚC KHI gọi mạng. Mọi chuỗi URL chứa khóa chỉ tồn tại cục bộ trong hàm, không log.
 */
class OpenWeatherMapProvider @Inject constructor(
    private val config: WeatherProviderConfig,
    @IoDispatcher private val io: CoroutineDispatcher
) : WeatherProvider {

    override suspend fun search(query: String, limit: Int): List<GeoPlace> = withContext(io) {
        val nfc = Normalizer.normalize(query, Normalizer.Form.NFC).trim()
        if (nfc.isEmpty()) return@withContext emptyList()
        val first = OwmParser.places(
            WeatherHttp.get(
                url(
                    "geo/1.0/direct",
                    "q" to nfc,
                    "limit" to limit.toString()
                )
            )
        )
        if (first.isNotEmpty()) return@withContext first
        // Đường dự phòng (D6): chưa kiểm geocoding có hiểu dấu tiếng Việt không -> thử lại một lần với chuỗi bỏ dấu.
        val plain = stripDiacritics(nfc)
        if (plain == nfc) first else OwmParser.places(
            WeatherHttp.get(
                url(
                    "geo/1.0/direct",
                    "q" to plain,
                    "limit" to limit.toString()
                )
            )
        )
    }

    override suspend fun reverse(lat: Double, lon: Double): GeoPlace? = withContext(io) {
        OwmParser.places(
            WeatherHttp.get(
                url(
                    "geo/1.0/reverse",
                    "lat" to lat.toString(),
                    "lon" to lon.toString(),
                    "limit" to "1"
                )
            )
        ).firstOrNull()
    }

    override suspend fun fetch(place: GeoPlace): WeatherSnapshot = withContext(io) {
        val lat = place.lat.toString()
        val lon = place.lon.toString()
        val current = WeatherHttp.get(
            url(
                "data/2.5/weather",
                "lat" to lat,
                "lon" to lon,
                "units" to "metric"
            )
        )
        val forecast = WeatherHttp.get(
            url(
                "data/2.5/forecast",
                "lat" to lat,
                "lon" to lon,
                "cnt" to OwmParser.FORECAST_COUNT.toString(),
                "units" to "metric"
            )
        )
        OwmParser.snapshot(place, current, forecast, System.currentTimeMillis())
    }

    private fun url(path: String, vararg params: Pair<String, String>): String {
        if (config.apiKey.isBlank()) throw WeatherException(WeatherError.NO_KEY)
        val sb = StringBuilder(config.baseUrl.trimEnd('/')).append('/').append(path).append('?')
        for ((k, v) in params) sb.append(k).append('=').append(URLEncoder.encode(v, "UTF-8"))
            .append('&')
        sb.append("appid=").append(URLEncoder.encode(config.apiKey, "UTF-8"))
        return sb.toString() // WeatherHttp dựng URL trong try (B1)
    }

    private fun stripDiacritics(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd').replace('Đ', 'D')
}
