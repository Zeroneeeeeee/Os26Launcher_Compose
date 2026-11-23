package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.photo.PhotoWidgetRepository
import gambi.global.os26launcher.domain.repository.LayoutRepository
import gambi.global.os26launcher.domain.weather.WeatherPlacesRepository
import javax.inject.Inject

/**
 * G2c — xóa một widget khỏi bố cục (xóa hàng; không dồn ô, không xóa trang trống, không ghi `hidden_apps`).
 * WX Task 5: dọn luôn địa điểm riêng của widget đó (nếu có) ở danh sách thời tiết; widget khác loại không có mục nào nên gọi này là no-op.
 * G3b Task 1: dọn luôn thư mục ảnh của widget Ảnh (cũng no-op với loại khác).
 */
class RemoveWidgetUseCase @Inject constructor(
    private val layoutRepository: LayoutRepository,
    private val weatherPlaces: WeatherPlacesRepository,
    private val photoWidgets: PhotoWidgetRepository
) {
    suspend operator fun invoke(itemId: Long) {
        layoutRepository.removeItem(itemId)
        weatherPlaces.forgetWidget(itemId)
        photoWidgets.forget(itemId)
    }
}
