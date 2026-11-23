package gambi.global.os26launcher.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.LocationMode
import gambi.global.os26launcher.domain.model.weather.WeatherException
import gambi.global.os26launcher.domain.model.weather.WeatherPlaces
import gambi.global.os26launcher.domain.weather.CoarseLocationSource
import gambi.global.os26launcher.domain.weather.WeatherPlacesRepository
import gambi.global.os26launcher.domain.weather.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * WX Task 4 — trạng thái màn Cài đặt → Vị trí (1.5): chế độ Tự động (vị trí gần đúng, một lần, cần quyền) / Chọn thủ công (thành phố, không quyền)
 * và việc "Cập nhật vị trí". Kết quả ghi vào [WeatherPlacesRepository]; mục "Vị trí hiện tại" của danh sách địa điểm lấy từ đó.
 *
 * - Lấy vị trí MỘT lần mỗi lần bấm (tối đa 10 giây, `CoarseLocationSource`), không `LocationListener` sống lâu; việc bị hủy (rời màn) thì listener được gỡ.
 * - Tra tên bằng `reverse` (1 lệnh OWM, người dùng chủ động bấm); không tra được thì đặt tên "Vị trí hiện tại". Không log tọa độ hay tên.
 */
@HiltViewModel
class LocationSettingsViewModel @Inject constructor(
    private val placesRepository: WeatherPlacesRepository,
    private val weatherRepository: WeatherRepository,
    private val location: CoarseLocationSource
) : ViewModel() {

    val places: StateFlow<WeatherPlaces> = placesRepository.places

    private val _locating = MutableStateFlow(false)

    /** Đang lấy vị trí (hàng "Cập nhật vị trí" hiện "Đang lấy vị trí…"). */
    val locating: StateFlow<Boolean> = _locating

    /** Chuỗi báo một lần (Toast): không lấy được vị trí, lỗi lưu. */
    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = _messages.receiveAsFlow()

    private var job: Job? = null

    fun hasPermission(): Boolean = location.hasPermission()

    /** Đổi chế độ không làm mất giá trị của chế độ kia. Sang Tự động, đã có quyền mà chưa có vị trí thì lấy luôn một lần. */
    fun setMode(mode: LocationMode, fallbackName: String) {
        viewModelScope.launch {
            try {
                placesRepository.setMode(mode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e, "setMode")
                return@launch
            }
            if (mode == LocationMode.AUTO && placesRepository.places.value.autoPlace == null && location.hasPermission()) {
                updateAutoPlace(fallbackName)
            }
        }
    }

    fun setManualPlace(place: GeoPlace) {
        viewModelScope.launch {
            try {
                placesRepository.setManualPlace(place)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e, "setManual")
            }
        }
    }

    /** Lấy vị trí một lần và ghi làm vị trí tự động. Chỉ một lần chạy mỗi lúc. Gọi khi đã có quyền ([hasPermission]); không có thì báo không lấy được. */
    fun updateAutoPlace(fallbackName: String) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _locating.value = true
            try {
                val fix = location.getOnce()
                if (fix == null) {
                    _messages.trySend(R.string.wx_loc_unavailable)
                    return@launch
                }
                val named = try {
                    weatherRepository.reverse(fix.lat, fix.lon)
                } catch (e: WeatherException) {
                    null
                }
                val place = (named ?: GeoPlace(fallbackName, fix.lat, fix.lon)).copy(lat = fix.lat, lon = fix.lon)
                placesRepository.setAutoPlace(place)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e, "update")
            } finally {
                _locating.value = false
            }
        }
    }

    private fun fail(e: Exception, what: String) {
        Log.e(TAG, "$what failed: ${e.javaClass.simpleName}") // chỉ tên lớp: không log vị trí
        _messages.trySend(R.string.settings_save_failed)
    }

    private companion object {
        const val TAG = "LocationSettingsVM"
    }
}
