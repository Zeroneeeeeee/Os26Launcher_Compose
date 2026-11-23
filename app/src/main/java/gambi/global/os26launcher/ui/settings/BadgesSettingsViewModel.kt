package gambi.global.os26launcher.ui.settings

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.config.BadgeStyle
import gambi.global.os26launcher.domain.config.HomeScreenConfig
import gambi.global.os26launcher.domain.config.HomeScreenConfigRepository
import gambi.global.os26launcher.domain.controlcenter.ControlPermissionsPort
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.controlcenter.ControlPermission
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.usecase.ObserveVisibleAppsUseCase
import gambi.global.os26launcher.domain.repository.BadgeTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

/**
 * BG — trạng thái màn "Huy hiệu thông báo" (3.4): công tắc chung, kiểu huy hiệu, danh sách app tắt huy hiệu (`HomeScreenConfig`),
 * và quyền "Truy cập thông báo" (listener là `ControlCenterService`). Màn lấy bằng `ViewModelProvider(activity)`.
 */
@HiltViewModel
class BadgesSettingsViewModel @Inject constructor(
    private val repository: HomeScreenConfigRepository,
    private val appRepository: AppRepository, // chỉ để nạp icon
    observeVisibleApps: ObserveVisibleAppsUseCase, // P3-7: app ẩn không lộ ở đây
    private val permissions: ControlPermissionsPort
) : ViewModel() {

    val config: StateFlow<HomeScreenConfig> =
        repository.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeScreenConfig())

    /**
     * Mỗi app (gói + hồ sơ) một hàng, sắp theo tên. Huy hiệu tính theo gói nên app nhiều activity chỉ lấy activity đầu.
     * null = chưa có dữ liệu (`apps` phát rỗng trước giá trị thật).
     */
    val apps: StateFlow<List<AppItem>?> = observeVisibleApps()
        .filter { it.isNotEmpty() }
        .map { list ->
            val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY } // không thread-safe
            list.sortedWith { a, b -> collator.compare(a.label, b.label) }.distinctBy { targetOf(it) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _failures = Channel<Int>(Channel.BUFFERED)
    val failures: Flow<Int> = _failures.receiveAsFlow()

    fun isListenerGranted(): Boolean = permissions.isGranted(ControlPermission.NOTIFICATION_LISTENER)

    fun setEnabled(enabled: Boolean) = save { repository.update { it.copy(badgesEnabled = enabled) } }

    fun setStyle(style: BadgeStyle) = save { repository.update { it.copy(badgeStyle = style) } }

    fun setAppEnabled(app: AppItem, enabled: Boolean) = save {
        val key = targetOf(app).storageKey
        repository.update { it.copy(badgeDisabledApps = if (enabled) it.badgeDisabledApps - key else it.badgeDisabledApps + key) }
    }

    suspend fun loadIcon(key: ComponentKey, sizePx: Int): Bitmap? = appRepository.icon(key, sizePx)

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

    companion object {
        private const val TAG = "BadgesSettingsVM"

        fun targetOf(app: AppItem) = BadgeTarget(app.key.packageName, app.key.userSerial)
    }
}
