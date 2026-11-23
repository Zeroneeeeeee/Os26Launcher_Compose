package gambi.global.os26launcher.domain.model.weather

/**
 * G3e — mô hình thời tiết ở tầng domain (Kotlin thuần, không `android.*`/`org.json`). Nguồn dữ liệu là OpenWeatherMap nhưng mô hình
 * không phụ thuộc nó: đổi nguồn (proxy, nhà cung cấp khác) chỉ cần viết [gambi.global.os26launcher.domain.weather.WeatherProvider] mới.
 */

/** Một địa điểm đã chọn. [lat]/[lon] làm tròn 2 số thập phân (~1 km) trước khi lưu: tọa độ là dữ liệu cá nhân. */
data class GeoPlace(
    val displayName: String,
    val lat: Double,
    val lon: Double,
    val countryCode: String? = null,
    val admin: String? = null
)

/** Nhóm tình trạng dùng cho biểu tượng và chuỗi mô tả (không có độ mịn "nhẹ/nặng", xem D7). */
enum class WeatherCondition {
    CLEAR, PARTLY_CLOUDY, CLOUDY, RAIN, THUNDERSTORM, SNOW, FOG, UNKNOWN;

    companion object {
        /**
         * Ánh xạ mã `weather[0].id` của OpenWeatherMap (https://openweathermap.org/weather-conditions):
         * 2xx giông, 3xx và 5xx mưa (kể cả 511 mưa đóng băng), 6xx tuyết, 701-762 sương/khói/bụi, 771 và 781 (gió giật, lốc) dùng chung
         * biểu tượng giông, 800 quang, 801-802 ít mây, 803-804 nhiều mây. Mã lạ -> [UNKNOWN].
         */
        fun fromOwmId(id: Int): WeatherCondition = when (id) {
            in 200..232 -> THUNDERSTORM
            in 300..321, in 500..531 -> RAIN
            in 600..622 -> SNOW
            in 701..762 -> FOG
            771, 781 -> THUNDERSTORM
            800 -> CLEAR
            801, 802 -> PARTLY_CLOUDY
            803, 804 -> CLOUDY
            else -> UNKNOWN
        }
    }
}

/** Một mốc dự báo (bước 3 giờ của gói Free). [epochSeconds] là UTC; giờ hiển thị đổi theo múi giờ của địa điểm ([WeatherSnapshot.utcOffsetSeconds]). */
data class HourlyPoint(
    val epochSeconds: Long,
    val tempC: Double,
    val condition: WeatherCondition,
    val isNight: Boolean
)

/**
 * WU: mọi nhiệt độ là °C THÔ (Double, chưa làm tròn); làm tròn và đổi °F ở [TemperatureUnit.display].
 * Kết quả một lần làm mới. [highC]/[lowC] là cực trị của nhiệt độ hiện tại và 8 mốc tiếp theo (≈ 24 GIỜ TỚI), KHÔNG phải cao/thấp
 * của ngày dương lịch (gói Free không có trường đó).
 */
data class WeatherSnapshot(
    val place: GeoPlace,
    val tempC: Double,
    val condition: WeatherCondition,
    val isNight: Boolean,
    val highC: Double,
    val lowC: Double,
    val hourly: List<HourlyPoint>,
    val utcOffsetSeconds: Int,
    val fetchedAtMillis: Long,
    /** WN: mọi mốc dự báo đã tải (3 giờ/mốc, tối đa ~36 giờ) cho tóm tắt thông báo; rỗng với cache cũ. Widget không dùng. */
    val forecast: List<HourlyPoint> = emptyList()
)

/** Loại lỗi, đủ để UI chọn câu chữ. Không mang thông điệp thô của máy chủ (có thể chứa khóa). */
enum class WeatherError { NO_KEY, NO_LOCATION, INVALID_KEY, RATE_LIMITED, NOT_FOUND, NETWORK, SERVER, PARSE }

class WeatherException(val error: WeatherError, cause: Throwable? = null) :
    Exception(error.name, cause)

/** Trạng thái hiển thị của widget (D8: sáu trạng thái). */
sealed interface WeatherState {
    /** Build không có khóa API. */
    data object NoKey : WeatherState

    /** Có khóa nhưng chưa chọn thành phố. */
    data object NoLocation : WeatherState

    /** Có vị trí, chưa có dữ liệu nào (không cache). */
    data object Loading : WeatherState

    /** Có dữ liệu. [stale] = cache cũ hơn [WeatherPolicy.STALE_AFTER_MS] hoặc lần thử gần nhất lỗi ([lastError] != null). */
    data class Ready(
        val snapshot: WeatherSnapshot,
        val stale: Boolean,
        val lastError: WeatherError?
    ) : WeatherState

    /** Không có dữ liệu và lần thử gần nhất lỗi. */
    data class Failed(val error: WeatherError) : WeatherState
}

/**
 * Hằng chính sách làm mới (D2 của G3e, đổi ở WX theo chủ dự án 2026-11-13). CHỈNH TAY. Áp cho TỪNG địa điểm.
 * Không còn làm mới thủ công (hệ quả của "tối đa 2 giờ một lần"): không có `MANUAL_REFRESH_MIN_MS`.
 */
object WeatherPolicy {
    /** Sau một lần thành công (và sau 429): lần kế sớm nhất. */
    const val REFRESH_INTERVAL_MS = 2 * 60 * 60_000L

    /** Sau lỗi mạng / máy chủ / dữ liệu: lần thử lại sớm nhất (chủ dự án chốt 15 phút; đặt bằng [REFRESH_INTERVAL_MS] nếu muốn tuyệt đối 2 giờ). */
    const val RETRY_AFTER_ERROR_MS = 15 * 60_000L

    /** Dữ liệu cũ hơn mức này mới bị đánh dấu "cũ"; phải lớn hơn [REFRESH_INTERVAL_MS] kẻo dữ liệu bình thường cũng bị đánh dấu đúng lúc đến hạn làm mới. */
    const val STALE_AFTER_MS = 4 * 60 * 60_000L
}
