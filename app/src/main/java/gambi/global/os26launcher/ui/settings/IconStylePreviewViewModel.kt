package gambi.global.os26launcher.ui.settings

import android.graphics.Bitmap
import android.util.Log
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
import gambi.global.os26launcher.domain.repository.BackdropRepository
import gambi.global.os26launcher.domain.repository.IconPackRepository
import gambi.global.os26launcher.domain.usecase.ObserveVisibleAppsUseCase
import kotlinx.coroutines.CancellationException
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

/**
 * P3-3 — dữ liệu khung xem trước ở đầu màn "Bộ biểu tượng" (2.3): ảnh nền đang dùng và 4 icon mẫu vẽ theo kiểu đang chọn.
 *
 * Icon vẽ bằng [AppRepository.iconWithStyle] với [IconStyleKey] tính THẲNG từ cấu hình vừa lưu (không qua cache và kiểu của
 * `IconStyleSource`, vì hai flow đọc DataStore độc lập nên kiểu mới có thể tới cache chậm hơn màn này). App mẫu lấy từ danh sách app
 * ĐƯỢC HIỆN (P3-7: app ẩn không bao giờ làm mẫu).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IconStylePreviewViewModel @Inject constructor(
    appearance: AppearanceRepository,
    private val iconPacks: IconPackRepository,
    private val appRepository: AppRepository,
    private val backdropRepository: BackdropRepository,
    observeVisibleApps: ObserveVisibleAppsUseCase,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    /** Ảnh nền thu nhỏ; null = chưa nạp hoặc không có. */
    private val _background = MutableStateFlow<Bitmap?>(null)
    val background: StateFlow<Bitmap?> = _background.asStateFlow()

    /** 4 icon mẫu theo kiểu đang chọn; rỗng = chưa vẽ xong. */
    private val _icons = MutableStateFlow<List<Bitmap>>(emptyList())
    val icons: StateFlow<List<Bitmap>> = _icons.asStateFlow()

    private var iconSizePx = 0
    private var started = false

    private data class Selection(val config: StyleParts, val samples: List<AppItem>)
    private data class StyleParts(
        val style: IconStyle,
        val tint: IconTint,
        val pack: String?,
        val bevel: Boolean,
        val corner: Int
    )

    private val selection = combine(
        appearance.config.map { StyleParts(it.iconStyle, it.iconTint, it.iconPack, it.iconBevel, it.glassLook.corner) }.distinctUntilChanged(),
        observeVisibleApps().filter { it.isNotEmpty() }.map(::pickSamples).distinctUntilChanged()
    ) { style, samples -> Selection(style, samples) }

    /**
     * Bắt đầu nạp (gọi một lần khi màn dựng, sau khi biết cỡ icon [iconPx] và cỡ khung [bgWidth] x [bgHeight]). Gọi lại thì bỏ qua.
     */
    fun start(iconPx: Int, bgWidth: Int, bgHeight: Int) {
        if (started || iconPx <= 0) return
        started = true
        iconSizePx = iconPx
        viewModelScope.launch {
            _background.value = try {
                when {
                    backdropRepository.hasBackdrop() -> backdropRepository.loadBackdrop(bgWidth, bgHeight)
                    backdropRepository.hasDefaultBackdrop() -> backdropRepository.loadDefaultBackdrop(bgWidth, bgHeight)
                    else -> null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "background failed", e)
                null
            }
        }
        viewModelScope.launch {
            selection.mapLatest { render(it) }.collect { _icons.value = it }
        }
    }

    private suspend fun render(sel: Selection): List<Bitmap> {
        val s = sel.config
        val packKey = s.pack?.let { withContext(io) { iconPacks.keyOf(it) } }.orEmpty()
        val key = IconStyleKey(s.style, s.tint, packKey, IconStyleKey.bevelFor(s.style, s.bevel), s.corner)
        return sel.samples.mapNotNull { appRepository.iconWithStyle(it.key, iconSizePx, key) }
    }

    /**
     * 4 app mẫu: Thời tiết, Máy ảnh, Cài đặt, Lịch (khớp theo tên gói, như app phổ thông trên máy), thiếu thì bù app hệ thống rồi app bất
     * kỳ. Mỗi gói một lần.
     */
    private fun pickSamples(apps: List<AppItem>): List<AppItem> {
        val out = ArrayList<AppItem>(SAMPLE_COUNT)
        for (hint in SAMPLE_HINTS) {
            apps.firstOrNull { a -> a.key.packageName.contains(hint, ignoreCase = true) && out.none { it.key.packageName == a.key.packageName } }
                ?.let(out::add)
        }
        for (a in apps.filter { it.isSystem } + apps) {
            if (out.size >= SAMPLE_COUNT) break
            if (out.none { it.key.packageName == a.key.packageName }) out.add(a)
        }
        return out.take(SAMPLE_COUNT)
    }

    private companion object {
        const val TAG = "IconStylePreviewVM"
        const val SAMPLE_COUNT = 4

        /** Đoạn tên gói của app mẫu theo thứ tự ưu tiên (chủ dự án: icon thời tiết hoặc app phổ thông). */
        val SAMPLE_HINTS = listOf("weather", "camera", "android.settings", "calendar")
    }
}
