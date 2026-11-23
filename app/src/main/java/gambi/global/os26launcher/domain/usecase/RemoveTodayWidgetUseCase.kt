package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.photo.PhotoWidgetRepository
import gambi.global.os26launcher.domain.today.TodayWidgetsRepository
import gambi.global.os26launcher.domain.weather.WeatherPlacesRepository
import javax.inject.Inject

/** TV-3 — xóa một widget khỏi Today View, dọn luôn địa điểm thời tiết và ảnh riêng của nó (như [RemoveWidgetUseCase] trên Home). */
class RemoveTodayWidgetUseCase @Inject constructor(
    private val today: TodayWidgetsRepository,
    private val weatherPlaces: WeatherPlacesRepository,
    private val photoWidgets: PhotoWidgetRepository
) {
    suspend operator fun invoke(id: Long) {
        today.remove(id)
        weatherPlaces.forgetWidget(id)
        photoWidgets.forget(id)
    }
}
