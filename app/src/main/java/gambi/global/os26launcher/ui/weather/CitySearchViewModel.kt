package gambi.global.os26launcher.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.WeatherError
import gambi.global.os26launcher.domain.model.weather.WeatherException
import gambi.global.os26launcher.domain.weather.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Trạng thái tạm của hộp tìm thành phố (chuỗi hiển thị do [CitySearchDialog] ánh xạ sang `strings.xml`). */
sealed interface CitySearchStatus {
    data object Idle : CitySearchStatus
    data object Searching : CitySearchStatus
    data object NotFound : CitySearchStatus
    data class Failed(val error: WeatherError) : CitySearchStatus
}

data class CitySearchUi(
    val results: List<GeoPlace> = emptyList(),
    val status: CitySearchStatus = CitySearchStatus.Idle
)

/**
 * WX Task 3 — logic tìm thành phố bằng OWM Geocoding, tách khỏi bảng Thời tiết để Cài đặt dùng chung (thêm địa điểm, chọn thành phố thủ công).
 * Chỉ tìm khi người dùng bấm Tìm / phím Search (không tìm theo từng phím): mỗi lần 1 lệnh (2 nếu rơi vào đường dự phòng bỏ dấu). Không log truy vấn
 * hay kết quả (có tên và tọa độ). Việc chặn tần suất làm mới không áp cho tìm kiếm.
 */
@HiltViewModel
class CitySearchViewModel @Inject constructor(
    private val repository: WeatherRepository
) : ViewModel() {

    val hasKey: Boolean = repository.hasKey

    private val _ui = MutableStateFlow(CitySearchUi())
    val ui: StateFlow<CitySearchUi> = _ui

    private var job: Job? = null

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty() || !hasKey) return
        job?.cancel()
        job = viewModelScope.launch {
            _ui.value = CitySearchUi(status = CitySearchStatus.Searching)
            _ui.value = try {
                // OWM đôi khi trả hai nơi cùng tên/tỉnh/nước (vd hai "Đà Nẵng, VN"): người dùng không phân biệt được nên bỏ bản trùng.
                val found = repository.search(q).distinctBy { Triple(it.displayName, it.admin, it.countryCode) }
                if (found.isEmpty()) CitySearchUi(status = CitySearchStatus.NotFound) else CitySearchUi(results = found)
            } catch (e: WeatherException) {
                CitySearchUi(status = CitySearchStatus.Failed(e.error))
            }
        }
    }

    /** Mở hộp mới / đóng hộp: hủy việc đang tìm và xóa kết quả cũ. */
    fun reset() {
        job?.cancel()
        job = null
        _ui.value = CitySearchUi()
    }
}
