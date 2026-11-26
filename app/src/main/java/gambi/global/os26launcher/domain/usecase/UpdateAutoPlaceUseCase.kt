package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.WeatherException
import gambi.global.os26launcher.domain.weather.CoarseLocationSource
import gambi.global.os26launcher.domain.weather.WeatherPlacesRepository
import gambi.global.os26launcher.domain.weather.WeatherRepository
import javax.inject.Inject

/**
 * WX Task 4 → PM (2026-11-26, tách khỏi `LocationSettingsViewModel` để widget thời tiết ở Home dùng chung): lấy vị trí gần đúng MỘT lần (tối đa
 * 10 giây, `CoarseLocationSource`), tra tên bằng `reverse` (1 lệnh OWM; không tra được thì tên [fallbackName]) rồi ghi làm vị trí tự động.
 * Trả false khi không lấy được vị trí (chưa có quyền, tắt định vị, hết giờ). Lỗi lưu ném ra cho nơi gọi. Không log tọa độ hay tên.
 */
class UpdateAutoPlaceUseCase @Inject constructor(
    private val placesRepository: WeatherPlacesRepository,
    private val weatherRepository: WeatherRepository,
    private val location: CoarseLocationSource
) {
    suspend operator fun invoke(fallbackName: String): Boolean {
        val fix = location.getOnce() ?: return false
        val named = try {
            weatherRepository.reverse(fix.lat, fix.lon)
        } catch (e: WeatherException) {
            null
        }
        val place = (named ?: GeoPlace(fallbackName, fix.lat, fix.lon)).copy(lat = fix.lat, lon = fix.lon)
        placesRepository.setAutoPlace(place)
        return true
    }
}
