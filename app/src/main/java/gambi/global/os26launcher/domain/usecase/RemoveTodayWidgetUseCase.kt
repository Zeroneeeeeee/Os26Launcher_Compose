package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.photo.PhotoWidgetRepository
import gambi.global.os26launcher.domain.repository.AppWidgetRepository
import gambi.global.os26launcher.domain.today.TodayWidgetsRepository
import gambi.global.os26launcher.domain.weather.WeatherPlacesRepository
import javax.inject.Inject

/**
 * TV-3 — xóa một widget khỏi Today View, dọn luôn địa điểm thời tiết và ảnh riêng của nó (như [RemoveWidgetUseCase] trên Home).
 * TA: widget app khác thì giải phóng id host SAU khi đã gỡ khỏi kho (như `LayoutReconciler`: xóa hàng trước, giải phóng sau).
 */
class RemoveTodayWidgetUseCase @Inject constructor(
    private val today: TodayWidgetsRepository,
    private val weatherPlaces: WeatherPlacesRepository,
    private val photoWidgets: PhotoWidgetRepository,
    private val appWidgets: AppWidgetRepository
) {
    suspend operator fun invoke(id: Long) {
        val removed = today.remove(id)
        removed?.appWidgetId?.let { appWidgets.release(listOf(it)) }
        weatherPlaces.forgetWidget(id)
        photoWidgets.forget(id)
    }
}
