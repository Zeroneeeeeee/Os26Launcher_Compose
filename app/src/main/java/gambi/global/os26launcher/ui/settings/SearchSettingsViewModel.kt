package gambi.global.os26launcher.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.config.SearchConfig
import gambi.global.os26launcher.domain.repository.RecentAppsRepository
import gambi.global.os26launcher.domain.config.SearchConfigRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** M8d Task 6 — trạng thái khối "Tìm kiếm" của màn Cài đặt. Tách khỏi `SettingsViewModel` để khối này dời đi được (G2d). */
@HiltViewModel
class SearchSettingsViewModel @Inject constructor(
    private val preferences: SearchConfigRepository,
    private val recentApps: RecentAppsRepository
) : ViewModel() {

    val uiState: StateFlow<SearchConfig> =
        preferences.config.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SearchConfig()
        )

    /** Chuỗi báo lỗi lưu; Activity hiện Toast và dựng lại hàng từ trạng thái thật (như các hàng khác của Cài đặt). */
    private val _failures = Channel<Int>(Channel.BUFFERED)
    val failures: Flow<Int> = _failures.receiveAsFlow()

    fun setSystemSettings(enabled: Boolean) = save { preferences.update { it.copy(systemSettings = enabled) } }

    fun setShortcuts(enabled: Boolean) = save { preferences.update { it.copy(shortcuts = enabled) } }

    /**
     * G3a: tắt chỉ ngừng đọc danh bạ, KHÔNG thu hồi quyền hệ thống. PB: [asked] = màn sắp mở hộp quyền, ghi cờ `contactsAsked` (lớp tìm kiếm
     * dùng cờ này để biết hộp quyền đã từng hiện, phân biệt "chưa hỏi" với "đã chặn").
     */
    fun setContacts(enabled: Boolean, asked: Boolean = false) = save {
        preferences.update { it.copy(contacts = enabled, contactsAsked = it.contactsAsked || asked) }
    }

    /** P3-5: "Gợi ý tìm kiếm" chỉ là tìm kiếm thịnh hành, không đụng lịch sử app. */
    fun setSuggestions(enabled: Boolean) = save { preferences.update { it.copy(suggestions = enabled) } }

    /** P3-5: tắt = ẩn mục Gần đây + ngừng ghi, GIỮ lịch sử đã lưu (xóa bằng "Xóa lịch sử"). */
    fun setRecentApps(enabled: Boolean) = save { preferences.update { it.copy(recentApps = enabled) } }

    fun clearHistory() = save { recentApps.clear() }

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
        const val TAG = "SearchSettingsVM"
    }
}
