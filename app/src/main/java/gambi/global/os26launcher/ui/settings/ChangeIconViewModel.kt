package gambi.global.os26launcher.ui.settings

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.repository.CustomIconRepository
import gambi.global.os26launcher.domain.usecase.ObserveVisibleAppsUseCase
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
 * P3-4 (CI) — trạng thái màn "Thay đổi biểu tượng" (5.2). Danh sách = app ĐƯỢC HIỆN (P3-7: app đã ẩn không có ở đây), sắp theo tên hiển thị.
 * Màn lấy ViewModel bằng `ViewModelProvider(activity)` như `RenameAppsViewModel`.
 */
@HiltViewModel
class ChangeIconViewModel @Inject constructor(
    observeVisibleApps: ObserveVisibleAppsUseCase,
    private val appRepository: AppRepository,
    private val customIcons: CustomIconRepository
) : ViewModel() {

    /** null = chưa có dữ liệu (danh sách app phát rỗng trước giá trị thật). */
    val apps: StateFlow<List<AppItem>?> = observeVisibleApps()
        .filter { it.isNotEmpty() }
        .map { list ->
            val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY } // không thread-safe
            list.sortedWith { a, b -> collator.compare(a.label, b.label) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** App đang có icon riêng (chữ "Đã đổi"). */
    val customized: StateFlow<Set<ComponentKey>> =
        customIcons.customized.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _failures = Channel<Int>(Channel.BUFFERED)
    val failures: Flow<Int> = _failures.receiveAsFlow()

    /** Icon đang hiện ngoài Home (có thể là icon riêng). */
    suspend fun loadIcon(key: ComponentKey, sizePx: Int): Bitmap? = appRepository.icon(key, sizePx)

    /** Icon hệ thống (bỏ qua icon riêng): ô "icon cũ" của sheet. */
    suspend fun systemIcon(key: ComponentKey, sizePx: Int): Bitmap? = appRepository.systemIcon(key, sizePx)

    /** 10 icon mẫu (ô vuông chưa cắt, theo [VARIANT_COLORS]); app không còn thì rỗng. */
    suspend fun variants(key: ComponentKey, sizePx: Int): List<Bitmap> =
        VARIANT_COLORS.mapNotNull { appRepository.iconOnColor(key, sizePx, it) }

    /** Cắt hình icon cho ô hiển thị. */
    suspend fun shape(square: Bitmap, sizePx: Int): Bitmap = appRepository.shapeIcon(square, sizePx)

    fun save(key: ComponentKey, square: Bitmap) {
        viewModelScope.launch {
            if (!customIcons.setIcon(key, square)) _failures.trySend(R.string.ci_save_failed)
        }
    }

    fun restore(key: ComponentKey) {
        viewModelScope.launch { customIcons.clearIcon(key) }
    }

    companion object {
        /** Cỡ ô vuông lưu làm icon riêng (px): đủ nét cho icon Home lớn nhất, file nhỏ. */
        const val SAVE_SIZE_PX = 432

        /** CHỈNH TAY: 10 nền màu của lưới icon mẫu, theo thứ tự Figma 5.2 (`302:2697`): xám, xanh lá, xanh dương, tím, hồng, ngọc, cam, xám xanh, xanh trời, đỏ. */
        val VARIANT_COLORS = listOf(
            0xFF3A3A48.toInt(), 0xFF34C759.toInt(), 0xFF3478F6.toInt(), 0xFF8E5CF7.toInt(), 0xFFF06BA8.toInt(),
            0xFF20B2A6.toInt(), 0xFFF5923E.toInt(), 0xFF7D8696.toInt(), 0xFF3FB3E8.toInt(), 0xFFEF5350.toInt()
        )
    }
}
