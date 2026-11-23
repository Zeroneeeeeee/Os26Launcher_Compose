package gambi.global.os26launcher.ui.settings

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.config.GeneralConfig
import gambi.global.os26launcher.domain.config.GeneralConfigRepository
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.usecase.GetHiddenAppsUseCase
import gambi.global.os26launcher.domain.usecase.HideAppUseCase
import gambi.global.os26launcher.domain.usecase.UnhideAppUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
 * HL-1 — trạng thái màn "Ứng dụng ẩn" (5.1, Figma `302:2648`): MỌI app của máy kèm cờ đang ẩn, sắp theo tên hiển thị.
 *
 * NGOẠI LỆ CÓ CHỦ Ý của quy tắc P3-7: đây là nơi DUY NHẤT được thấy app ẩn, nên đọc thẳng `AppRepository.apps` (không qua
 * `ObserveVisibleAppsUseCase`). Ẩn/bỏ ẩn đi đúng đường của menu nhấn giữ icon ([HideAppUseCase] / [UnhideAppUseCase]).
 */
@HiltViewModel
class HiddenAppsViewModel @Inject constructor(
    private val appRepository: AppRepository,
    getHiddenApps: GetHiddenAppsUseCase,
    private val hideApp: HideAppUseCase,
    private val unhideApp: UnhideAppUseCase,
    private val generalConfig: GeneralConfigRepository // HL-2: công tắc khóa (`hiddenAppsLock`)
) : ViewModel() {

    /** HL-2: người dùng đã bật khóa. null = chưa đọc xong (màn chưa được vẽ danh sách khi chưa biết). */
    val lockEnabled: StateFlow<Boolean?> = generalConfig.config.map<GeneralConfig, Boolean?> { it.hiddenAppsLock }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** HL-2: ghi công tắc khóa (màn đã xác thực trước khi gọi). */
    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                generalConfig.update { it.copy(hiddenAppsLock = enabled) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "setLockEnabled($enabled) failed", e)
                _failures.trySend(R.string.settings_save_failed)
            }
        }
    }

    /** Một hàng: app và cờ đang ẩn. */
    data class Entry(val app: AppItem, val hidden: Boolean)

    /** null = chưa có dữ liệu (danh sách app phát rỗng trước giá trị thật). */
    val entries: StateFlow<List<Entry>?> = combine(
        appRepository.apps.filter { it.isNotEmpty() },
        getHiddenApps()
    ) { all, hidden ->
        val hiddenKeys = hidden.mapTo(HashSet()) { it.key }
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY } // không thread-safe
        all.sortedWith { a, b -> collator.compare(a.label, b.label) }.map { Entry(it, it.key in hiddenKeys) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _failures = Channel<Int>(Channel.BUFFERED)
    val failures: Flow<Int> = _failures.receiveAsFlow()

    suspend fun loadIcon(key: ComponentKey, sizePx: Int): Bitmap? = appRepository.icon(key, sizePx)

    /** Công tắc của một hàng: bật = ẩn. Lỗi ghi: báo màn (Toast) để dựng lại hàng từ trạng thái thật. */
    fun setHidden(app: AppItem, hidden: Boolean) {
        viewModelScope.launch {
            try {
                if (hidden) hideApp(app.key, app.key.userSerial) else unhideApp(app.key, app.key.userSerial)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "setHidden(${app.key.packageName}, $hidden) failed", e)
                _failures.trySend(R.string.settings_save_failed)
            }
        }
    }

    private companion object {
        const val TAG = "HiddenAppsVM"
    }
}
