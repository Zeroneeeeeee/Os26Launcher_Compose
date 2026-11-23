package gambi.global.os26launcher.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.config.HomeScreenConfig
import gambi.global.os26launcher.domain.config.HomeScreenConfigRepository
import gambi.global.os26launcher.domain.config.LibraryStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** HT — trạng thái và ghi công tắc Màn hình chính cho các màn Cài đặt (Lướt màn hình, Cài đặt tìm kiếm; LO: Thư viện ứng dụng). */
@HiltViewModel
class HomeTogglesViewModel @Inject constructor(
    private val repository: HomeScreenConfigRepository
) : ViewModel() {

    val uiState: StateFlow<HomeScreenConfig> =
        repository.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeScreenConfig())

    /** Chuỗi báo lỗi lưu; màn hiện Toast và dựng lại hàng từ trạng thái thật. */
    private val _failures = Channel<Int>(Channel.BUFFERED)
    val failures: Flow<Int> = _failures.receiveAsFlow()

    fun setSwipeDownSearch(enabled: Boolean) = save { repository.update { it.copy(swipeDownSearch = enabled) } }

    fun setSearchPill(enabled: Boolean) = save { repository.update { it.copy(searchPill = enabled) } }

    fun setPageDots(enabled: Boolean) = save { repository.update { it.copy(pageDots = enabled) } }

    fun setTodayEnabled(enabled: Boolean) = save { repository.update { it.copy(todayEnabled = enabled) } } // TV (Q6)

    // LO: màn 3.2 Thư viện ứng dụng
    fun setLibraryEnabled(enabled: Boolean) = save { repository.update { it.copy(libraryEnabled = enabled) } }

    fun setLibraryStyle(style: LibraryStyle) = save { repository.update { it.copy(libraryStyle = style) } }

    fun setLibraryAutoCategorize(enabled: Boolean) = save { repository.update { it.copy(libraryAutoCategorize = enabled) } }

    fun setLibrarySuggestions(enabled: Boolean) = save { repository.update { it.copy(librarySuggestions = enabled) } }

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
        const val TAG = "HomeTogglesVM"
    }
}
