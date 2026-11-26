package gambi.global.os26launcher.ui.onboarding

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.domain.config.AppearanceRepository
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.IconStyle
import gambi.global.os26launcher.domain.model.IconStyleKey
import gambi.global.os26launcher.domain.model.IconTint
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.repository.IconPackRepository
import gambi.global.os26launcher.domain.usecase.ObserveVisibleAppsUseCase
import gambi.global.os26launcher.ui.settings.IconStyleSamples
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** TR — các kiểu icon ở bước chọn kiểu icon của Onboarding, theo thứ tự thẻ (kiểu Trong suốt đã bỏ 2026-11-26). */
object IconStyleChoices {
    val list: List<IconStyle> = listOf(IconStyle.DEFAULT, IconStyle.GLASS, IconStyle.TINTED, IconStyle.THREE_D)
}

/**
 * TR Task 1 — icon mẫu cho từng thẻ của bước chọn kiểu icon (Onboarding): 4 app mẫu ([IconStyleSamples]) vẽ theo MỖI kiểu trong
 * [IconStyleChoices.list], cùng màu tô, gói icon và bo góc đang lưu (như Home sẽ vẽ sau khi chọn). Máy mới chưa nạp xong app thì
 * [previews] rỗng (thẻ chỉ có tên).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IconStyleChoicesViewModel @Inject constructor(
    private val appearance: AppearanceRepository,
    private val iconPacks: IconPackRepository,
    private val appRepository: AppRepository,
    private val observeVisibleApps: ObserveVisibleAppsUseCase,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    private val _previews = MutableStateFlow<Map<IconStyle, List<Bitmap>>>(emptyMap())
    val previews: StateFlow<Map<IconStyle, List<Bitmap>>> = _previews.asStateFlow()

    private var started = false

    private data class Parts(val tint: IconTint, val pack: String?, val corner: Int)
    private data class Selection(val parts: Parts, val samples: List<AppItem>)

    /** Bắt đầu vẽ (gọi một lần khi biết cỡ icon [iconPx]); gọi lại thì bỏ qua. */
    fun start(iconPx: Int) {
        if (started || iconPx <= 0) return
        started = true
        val selection = combine(
            appearance.config.map { Parts(it.iconTint, it.iconPack, it.glassLook.corner) }.distinctUntilChanged(),
            observeVisibleApps().filter { it.isNotEmpty() }.map(IconStyleSamples::pick).distinctUntilChanged()
        ) { parts, samples -> Selection(parts, samples) }
        viewModelScope.launch {
            selection.mapLatest { render(it, iconPx) }.collect { _previews.value = it }
        }
    }

    private suspend fun render(sel: Selection, iconPx: Int): Map<IconStyle, List<Bitmap>> {
        val packKey = sel.parts.pack?.let { withContext(io) { iconPacks.keyOf(it) } }.orEmpty()
        return IconStyleChoices.list.associateWith { style ->
            // Màu tô chỉ có nghĩa với TINTED (như IconStyleSource), để kiểu khác trúng cache sẵn có.
            val tint = if (style == IconStyle.TINTED) sel.parts.tint else IconTint.DEFAULT
            val key = IconStyleKey(style, tint, packKey, corner = sel.parts.corner)
            sel.samples.mapNotNull { appRepository.iconWithStyle(it.key, iconPx, key) }
        }
    }
}
