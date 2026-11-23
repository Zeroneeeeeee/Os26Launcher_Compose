package gambi.global.os26launcher.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.WeatherPlaces
import gambi.global.os26launcher.domain.model.weather.WeatherState
import gambi.global.os26launcher.domain.weather.WeatherPlacesRepository
import gambi.global.os26launcher.domain.weather.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Một dòng chọn trong bảng: [place] null = "Vị trí hiện tại" chưa xác định. [isCurrent] = mục "Vị trí hiện tại". */
data class SheetOption(val placeId: String, val place: GeoPlace?, val isCurrent: Boolean)

/** [weather] là trạng thái của địa điểm đang được chọn cho widget ([selectedId]). */
data class SheetUi(
    val hasKey: Boolean,
    val options: List<SheetOption>,
    val selectedId: String,
    val weather: WeatherState
)

/**
 * G3e Task 6b + WX Task 5 — bảng Thời tiết là BỘ CHỌN ĐỊA ĐIỂM cho đúng widget vừa được bấm ([bindWidget]): các lựa chọn là "Vị trí hiện tại" và các
 * thành phố đã thêm (tối đa 3), chọn xong ghi vào [WeatherPlacesRepository] (widget đổi ngay, không gọi mạng ở đây). Thêm/xóa địa điểm và vị trí nằm ở Cài đặt.
 * Không còn tìm thành phố, lấy vị trí, làm mới thủ công ở bảng này.
 */
@HiltViewModel
class WeatherSheetViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val places: WeatherPlacesRepository
) : ViewModel() {

    private val widgetId = MutableStateFlow(0L)

    val ui: StateFlow<SheetUi> =
        combine(places.places, places.widgetPlaces, repository.states, widgetId) { list, _, states, id ->
            build(list, states, id)
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            build(places.places.value, repository.states.value, widgetId.value)
        )

    /** Gọi mỗi lần mở bảng: [itemId] = widget vừa bấm. */
    fun bindWidget(itemId: Long) {
        widgetId.value = itemId
    }

    /** Chọn địa điểm cho widget đang bind. Không gọi mạng: widget đang hiện sẽ tải nơi mới ở nhịp kế nếu chưa có dữ liệu (hợp đồng của [WeatherRepository]). */
    fun select(placeId: String) {
        val id = widgetId.value
        if (id == 0L) return
        viewModelScope.launch { places.setWidgetPlace(id, placeId) }
    }

    private fun build(list: WeatherPlaces, states: Map<String, WeatherState>, id: Long): SheetUi {
        val options = ArrayList<SheetOption>(list.count)
        options.add(SheetOption(WeatherPlaces.CURRENT, list.current, isCurrent = true))
        list.saved.forEach { options.add(SheetOption(WeatherPlaces.placeIdOf(it), it, isCurrent = false)) }
        val selected = places.widgetPlaceId(id)
        return SheetUi(
            repository.hasKey,
            options,
            selected,
            states[selected] ?: WeatherState.Loading
        )
    }
}
