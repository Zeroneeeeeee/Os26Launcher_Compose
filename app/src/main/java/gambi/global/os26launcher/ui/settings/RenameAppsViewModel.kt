package gambi.global.os26launcher.ui.settings

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.usecase.RenameAppUseCase
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.usecase.ObserveVisibleAppsUseCase
import gambi.global.os26launcher.domain.usecase.RestoreAppLabelUseCase
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
 * RN — trạng thái màn "Thay đổi tên ứng dụng" (5.3). Tách khỏi `SettingsViewModel` (mẫu `SearchSettingsViewModel`): màn lấy ViewModel này
 * bằng `ViewModelProvider(activity)`. Danh sách là app ĐƯỢC HIỆN (P3-7: trừ app đã ẩn, qua `ObserveVisibleAppsUseCase`), sắp theo TÊN GỐC để một hàng
 * không nhảy chỗ sau khi lưu tên mới; `AppItem.label` đã là tên hiển thị (lớp phủ ở `AppRepositoryImpl`).
 */
@HiltViewModel
class RenameAppsViewModel @Inject constructor(
    private val appRepository: AppRepository, // chỉ để nạp icon
    observeVisibleApps: ObserveVisibleAppsUseCase, // P3-7: app ẩn không lộ ở đây
    private val renameApp: RenameAppUseCase,
    private val restoreLabel: RestoreAppLabelUseCase
) : ViewModel() {

    /** null = chưa có dữ liệu lần nào. `apps` luôn phát danh sách rỗng trước giá trị thật nên bỏ rỗng (như `AppLibraryViewModel`). */
    val apps: StateFlow<List<AppItem>?> = observeVisibleApps()
        .filter { it.isNotEmpty() }
        .map { list ->
            // Collator KHÔNG thread-safe → tạo mới mỗi lần sắp.
            val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
            list.sortedWith { a, b -> collator.compare(a.originalLabel, b.originalLabel) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Lưu thất bại: id chuỗi báo lỗi; màn hiện Toast và dựng lại hàng từ trạng thái thật. */
    private val _failures = Channel<Int>(Channel.BUFFERED)
    val failures: Flow<Int> = _failures.receiveAsFlow()

    fun rename(app: AppItem, input: String) = save { renameApp(app.key, app.originalLabel, input) }

    fun restore(app: AppItem) = save { restoreLabel(app.key) }

    /** Icon cho hàng danh sách (qua `IconCache` chung, đã có huy hiệu hồ sơ công việc). */
    suspend fun loadIcon(key: ComponentKey, sizePx: Int): Bitmap? = appRepository.icon(key, sizePx)

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "save custom label failed", e) // không ghi tên
                _failures.trySend(R.string.settings_save_failed)
            }
        }
    }

    private companion object {
        const val TAG = "RenameAppsVM"
    }
}
