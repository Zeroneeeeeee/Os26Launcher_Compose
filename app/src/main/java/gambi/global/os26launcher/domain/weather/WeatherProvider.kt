package gambi.global.os26launcher.domain.weather

import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.WeatherSnapshot

/**
 * G3e — nguồn dữ liệu thời tiết. Bản hiện thực duy nhất ở nhánh này: OpenWeatherMap ([gambi.global.os26launcher.data.weather.OpenWeatherMapProvider]).
 * Muốn đổi sang proxy/backend: viết bản hiện thực mới và đổi một dòng ở `WeatherModule`; widget và repository không đổi.
 *
 * Mọi hàm là suspend, chạy được trên luồng bất kỳ (bản hiện thực tự chuyển sang IO), và báo lỗi bằng
 * [gambi.global.os26launcher.domain.model.weather.WeatherException]. Khóa rỗng phải ném `NO_KEY` TRƯỚC KHI gọi mạng.
 */
interface WeatherProvider {
    /** Tên thành phố -> tối đa [limit] địa điểm. Rỗng = không tìm thấy (không ném). */
    suspend fun search(query: String, limit: Int = 5): List<GeoPlace>

    /** Tọa độ -> tên địa điểm; null nếu không có. */
    suspend fun reverse(lat: Double, lon: Double): GeoPlace?

    /** Thời tiết hiện tại + dự báo cho [place]. */
    suspend fun fetch(place: GeoPlace): WeatherSnapshot
}
