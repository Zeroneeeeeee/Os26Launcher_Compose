package gambi.global.os26launcher.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.config.MotionConfig
import gambi.global.os26launcher.domain.config.MotionConfigRepository
import gambi.global.os26launcher.domain.config.MotionSpeed
import gambi.global.os26launcher.domain.config.TransitionStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** TR — trạng thái và ghi của màn "Hiệu ứng chuyển cảnh" (4.1). Home/Cài đặt nhanh đọc lại qua `TransitionMotion`. */
@HiltViewModel
class TransitionsSettingsViewModel @Inject constructor(
    private val repository: MotionConfigRepository
) : ViewModel() {

    val config: StateFlow<MotionConfig> =
        repository.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MotionConfig())

    /** Chuỗi báo lỗi lưu; màn hiện Toast và dựng lại hàng từ trạng thái thật. */
    private val _failures = Channel<Int>(Channel.BUFFERED)
    val failures: Flow<Int> = _failures.receiveAsFlow()

    fun setStyle(style: TransitionStyle) = save { repository.update { it.copy(transitionStyle = style) } }

    fun setSpeed(speed: MotionSpeed) = save { repository.update { it.copy(transitionSpeed = speed) } }

    fun setReduceMotion(enabled: Boolean) = save { repository.update { it.copy(reduceMotion = enabled) } }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "save failed", e)
                _failures.trySend(R.string.settings_save_failed)
            }
        }
    }

    private companion object {
        const val TAG = "TransitionsVM"
    }
}
