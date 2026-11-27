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

    /** Có dữ liệu. [stale] = cache cũ hơn chu kỳ làm mới + [WeatherPolicy.STALE_GRACE_MS] hoặc lần thử gần nhất lỗi ([lastError] != null). */
    data class Ready(
        val snapshot: WeatherSnapshot,
        val stale: Boolean,
        val lastError: WeatherError?
    ) : WeatherState

    /** Không có dữ liệu và lần thử gần nhất lỗi. */
    data class Failed(val error: WeatherError) : WeatherState
}

/**
 * WR (chủ dự án 2026-11-27) — chu kỳ "Làm mới sau" ở Cài đặt → Thời tiết: sau một lần thành công, lần tự làm mới kế sớm nhất của TỪNG địa điểm, và
 * khoảng tối thiểu giữa hai lần cập nhật vị trí im lặng. Lưu bằng `name` (thêm giá trị mới ở CUỐI). Thay hằng 2 giờ của WX.
 */
enum class WeatherRefreshInterval(val millis: Long) {
    H1(1 * HOUR_MS), H3(3 * HOUR_MS), H6(6 * HOUR_MS), H9(9 * HOUR_MS), H12(12 * HOUR_MS), D1(24 * HOUR_MS);

    companion object {
        /** Chủ dự án chốt 2026-11-27: mặc định 1 ngày. */
        val DEFAULT = D1

        /** Tên lạ hoặc null → [DEFAULT], không crash Flow. */
        fun fromName(name: String?): WeatherRefreshInterval = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

private const val HOUR_MS = 60 * 60_000L

/**
 * Hằng chính sách làm mới (D2 của G3e, đổi ở WX 2026-11-13, WR 2026-11-27). CHỈNH TAY. Áp cho TỪNG địa điểm. Chu kỳ sau một lần thành công là
 * lựa chọn của người dùng ([WeatherRefreshInterval]); các hằng dưới đây là phần cố định.
 */
object WeatherPolicy {
    /** WR R1: sau 429 (vượt hạn mức OWM) thử lại sớm nhất sau 2 giờ, không theo chu kỳ (chu kỳ 1 ngày thì chờ quá lâu). */
    const val RATE_LIMIT_RETRY_MS = 2 * HOUR_MS

    /** Sau lỗi mạng / máy chủ / dữ liệu: lần thử lại sớm nhất (chủ dự án chốt 15 phút). */
    const val RETRY_AFTER_ERROR_MS = 15 * 60_000L

    /** WR: làm mới tay (nút trên widget) tối đa một lần mỗi chừng này cho một địa điểm (mỗi lần 2 lệnh OWM; gói Free 60 lệnh/phút). */
    const val MANUAL_COOLDOWN_MS = 60_000L

    /** WR: dữ liệu bị đánh dấu "cũ" khi quá chu kỳ + chừng này (1 ngày → 25 giờ), để không bị đánh dấu đúng lúc vừa đến hạn làm mới. */
    const val STALE_GRACE_MS = 1 * HOUR_MS
}
