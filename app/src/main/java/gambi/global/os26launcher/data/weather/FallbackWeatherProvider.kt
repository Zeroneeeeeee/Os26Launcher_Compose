package gambi.global.os26launcher.data.weather

import android.util.Log
import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.WeatherError
import gambi.global.os26launcher.domain.model.weather.WeatherException
import gambi.global.os26launcher.domain.model.weather.WeatherSnapshot
import gambi.global.os26launcher.domain.weather.WeatherProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WM (anh lead 2026-11-27) — OpenWeatherMap là nguồn chính, Open-Meteo là nguồn dự phòng (plan `WM-weather-fallback.md`):
 *
 * - [fetch] thử OWM; lỗi phía OWM ([SWITCH_ERRORS]: 429, 401, 5xx, parse — P1) thì lấy từ Open-Meteo. Mất mạng / không tìm thấy thì KHÔNG chuyển.
 * - Bám Open-Meteo (gọi thẳng, không đốt thêm lượt OWM — P2): sau 429 trong [RATE_LIMIT_HOLD_MS]; sau 401 tới khi tiến trình khởi động lại.
 *   Chỉ trong RAM.
 * - Cả hai cùng lỗi: ném lỗi của OWM, TRỪ khi OWM là 401 (ném lỗi của Open-Meteo): repository gặp 401 thì dừng hẳn mọi lần làm mới
 *   (`keyDead`), mà nguồn dự phòng vẫn có thể sống lại.
 * - [search] / [reverse] chỉ OWM (Open-Meteo không có tra tên từ tọa độ — P3).
 *
 * Repository không biết nguồn nào trả (P5): dữ liệu Open-Meteo là một lần thành công như thường.
 */
@Singleton
class FallbackWeatherProvider @Inject constructor(
    private val primary: OpenWeatherMapProvider,
    private val backup: OpenMeteoProvider
) : WeatherProvider {

    /** Tới mốc này (epoch ms) gọi thẳng Open-Meteo vì OWM vừa trả 429. */
    @Volatile
    private var backupUntil = 0L

    /** OWM trả 401 (khóa hỏng / bị khóa): khóa chỉ đổi khi cài bản mới nên bám tới khi tiến trình khởi động lại. */
    @Volatile
    private var primaryDead = false

    override suspend fun search(query: String, limit: Int): List<GeoPlace> = primary.search(query, limit)

    override suspend fun reverse(lat: Double, lon: Double): GeoPlace? = primary.reverse(lat, lon)

    override suspend fun fetch(place: GeoPlace): WeatherSnapshot {
        if (primaryDead || System.currentTimeMillis() < backupUntil) return backup.fetch(place)
        val failure = try {
            return primary.fetch(place)
        } catch (e: WeatherException) {
            e
        }
        if (failure.error !in SWITCH_ERRORS) throw failure
        when (failure.error) {
            WeatherError.RATE_LIMITED -> backupUntil = System.currentTimeMillis() + RATE_LIMIT_HOLD_MS
            WeatherError.INVALID_KEY -> primaryDead = true
            else -> Unit
        }
        Log.i(TAG, "OWM ${failure.error.name} -> Open-Meteo") // P6: chỉ tên lỗi, không URL / tọa độ / khóa
        return try {
            backup.fetch(place)
        } catch (e: WeatherException) {
            throw if (failure.error == WeatherError.INVALID_KEY) e else failure
        }
    }

    private companion object {
        const val TAG = "WeatherFallback"

        /** P1: lỗi nằm ở phía OWM. */
        val SWITCH_ERRORS = setOf(WeatherError.RATE_LIMITED, WeatherError.INVALID_KEY, WeatherError.SERVER, WeatherError.PARSE)

        /** P2: 429 thì 30 phút gọi thẳng Open-Meteo. */
        const val RATE_LIMIT_HOLD_MS = 30 * 60 * 1000L
    }
}
