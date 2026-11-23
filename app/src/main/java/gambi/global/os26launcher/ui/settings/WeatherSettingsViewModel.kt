package gambi.global.os26launcher.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.weather.AddPlaceResult
import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.TemperatureUnit
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationKind
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationSettings
import gambi.global.os26launcher.domain.model.weather.WeatherPlaces
import gambi.global.os26launcher.domain.weather.WeatherNotificationScheduler
import gambi.global.os26launcher.domain.weather.WeatherPlacesRepository
import gambi.global.os26launcher.domain.weather.WeatherRepository
import gambi.global.os26launcher.ui.weather.notify.WeatherNotifier
import gambi.global.os26launcher.domain.weather.WeatherPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * WU Task 2 + WX Task 3 — trạng thái màn Cài đặt → Thời tiết (mẫu `SearchSettingsViewModel`): đơn vị nhiệt độ (WU) và danh sách địa điểm theo dõi (WX).
 * Không gọi mạng: thêm/xóa địa điểm chỉ ghi danh sách; địa điểm chỉ được tải khi một widget đang hiện nó (hợp đồng của `WeatherRepository`).
 */
@HiltViewModel
class WeatherSettingsViewModel @Inject constructor(
    private val preferences: WeatherPreferencesRepository,
    private val placesRepository: WeatherPlacesRepository,
    private val weatherRepository: WeatherRepository, // WN: chỉ để biết build có khóa không
    private val scheduler: WeatherNotificationScheduler,
    private val notifier: WeatherNotifier
) : ViewModel() {

    /** WN: hai công tắc thông báo (mặc định tắt tới khi đọc xong). */
    val notifications: StateFlow<WeatherNotificationSettings> = preferences.notifications.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WeatherNotificationSettings()
    )

    /** WN: build có khóa OWM không (không có thì không có thông báo nào). */
    val hasKey: Boolean get() = weatherRepository.hasKey

    /**
     * WN: bật/tắt một thông báo rồi đặt lại lịch. Bật thì tạo kênh (lười, lúc này mới tạo). Quyền thông báo do màn xin trước khi gọi hàm này.
     */
    fun setNotification(kind: WeatherNotificationKind, enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferences.setNotificationEnabled(kind, enabled)
                if (enabled) notifier.ensureChannels()
                scheduler.rearm()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "notification save failed: ${e.javaClass.simpleName}")
                _failures.trySend(R.string.settings_save_failed)
            }
        }
    }

    val unit: StateFlow<TemperatureUnit> = preferences.unit.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TemperatureUnit.DEFAULT
    )

    /** Danh sách địa điểm (mặc định rỗng tới khi đọc xong file). */
    val places: StateFlow<WeatherPlaces> = placesRepository.places

    /** Chuỗi báo lỗi lưu; màn hiện Toast và dựng lại hàng từ trạng thái thật. */
    private val _failures = Channel<Int>(Channel.BUFFERED)
    val failures: Flow<Int> = _failures.receiveAsFlow()

    /** Kết quả thêm địa điểm khác [AddPlaceResult.ADDED] (trùng, đã đủ): màn hiện Toast. */
    private val _addRejected = Channel<AddPlaceResult>(Channel.BUFFERED)
    val addRejected: Flow<AddPlaceResult> = _addRejected.receiveAsFlow()

    fun onUnitChosen(chosen: TemperatureUnit) {
        viewModelScope.launch {
            try {
                preferences.setUnit(chosen)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "save failed", e)
                _failures.trySend(R.string.settings_save_failed)
            }
        }
    }

    fun addPlace(place: GeoPlace) {
        viewModelScope.launch {
            try {
                val result = placesRepository.add(place)
                if (result != AddPlaceResult.ADDED) _addRejected.trySend(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Repository không ném (hợp đồng); ngoại lệ lạ không được làm sập coroutine. Chỉ ghi tên lớp: không log địa điểm.
                Log.e(TAG, "add failed: ${e.javaClass.simpleName}")
                _failures.trySend(R.string.settings_save_failed)
            }
        }
    }

    fun removePlace(placeId: String) {
        viewModelScope.launch {
            try {
                placesRepository.remove(placeId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "remove failed: ${e.javaClass.simpleName}")
                _failures.trySend(R.string.settings_save_failed)
            }
        }
    }

    private companion object {
        const val TAG = "WeatherSettingsVM"
    }
}
