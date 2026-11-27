package gambi.global.os26launcher.ui.onboarding

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.core.wallpaper.WallpaperPresets
import gambi.global.os26launcher.domain.config.AppStateRepository
import gambi.global.os26launcher.domain.config.AppearanceRepository
import gambi.global.os26launcher.domain.config.WallpaperKind
import gambi.global.os26launcher.domain.model.IconStyle
import gambi.global.os26launcher.domain.model.WallpaperTarget
import gambi.global.os26launcher.domain.repository.BackdropRepository
import gambi.global.os26launcher.domain.repository.DefaultLauncherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * OB — trạng thái luồng Onboarding. Bước và lựa chọn nằm trong [SavedStateHandle] nên sống qua xoay màn và process death
 * (plan OB, Review Focus 1). Lựa chọn ghi vào config NGAY khi bấm "Tiếp tục" ở bước đó (Q4): Bỏ qua giữa chừng vẫn giữ phần đã chọn (Q2).
 * Máy đã đặt launcher này làm mặc định thì bước 0.4 bị bỏ qua cả khi tiến lẫn lùi (Q5).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val appearance: AppearanceRepository,
    private val appState: AppStateRepository,
    private val defaultLauncher: DefaultLauncherRepository,
    private val backdrop: BackdropRepository, // P2-4: áp ảnh 0.3 làm hình nền màn hình chính của hệ thống
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    // Bước đầu: bước đã lưu; chưa có thì 0.5 nếu Home mở sau khi đặt mặc định (extra của Intent nằm sẵn trong SavedStateHandle), không thì 0.1.
    private val _step = MutableStateFlow(
        saved.get<Int>(KEY_STEP)?.let { OnboardingStep.entries.getOrNull(it) }
            ?: if (saved.get<Boolean>(OnboardingActivity.EXTRA_START_AT_DONE) == true) OnboardingStep.DONE else OnboardingStep.WELCOME
    )
    val step: StateFlow<OnboardingStep> = _step.asStateFlow()

    // TR (2026-11-26): bước 0.2 chọn kiểu icon (thay chọn theme). Chưa có lựa chọn đã lưu thì tạm Mặc định, rồi [init] đặt kiểu đang lưu (R2).
    private val _iconStyle = MutableStateFlow(
        saved.get<String>(KEY_ICON_STYLE)?.let { name -> IconStyle.entries.firstOrNull { it.name == name } } ?: IconStyle.DEFAULT
    )

    /** Kiểu icon đang chọn ở 0.2 (chưa chắc đã ghi). */
    val iconStyle: StateFlow<IconStyle> = _iconStyle.asStateFlow()

    private val _swatch = MutableStateFlow(saved.get<Int>(KEY_SWATCH) ?: WallpaperPresets.indexOf(WallpaperPresets.DEFAULT_ID).coerceAtLeast(0)) // WI: mặc định wp_ios27_1

    /** Chỉ số ô hình nền đang chọn ở 0.3 ([WallpaperPresets.list], WI). */
    val swatch: StateFlow<Int> = _swatch.asStateFlow()

    /** Đang ghi config hoặc đang đóng: nút bỏ qua lần bấm này (chống bấm hai lần). */
    private var busy = false

    private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events: Flow<OnboardingEvent> = _events.receiveAsFlow()

    init {
        // Lần đầu (chưa có lựa chọn đã lưu): chọn sẵn kiểu icon đang lưu (máy mới: Mặc định, TR R2), và ô hình nền khớp nền đang áp
        // (mở thẳng 0.5 ở Onboarding mới: hàng tóm tắt đúng với những gì đã chọn ở bản trước).
        if (!saved.contains(KEY_ICON_STYLE)) viewModelScope.launch {
            val config = runCatching { appearance.config.first() }.getOrNull() ?: return@launch
            if (saved.contains(KEY_ICON_STYLE)) return@launch
            chooseIconStyle(config.iconStyle)
            if (!saved.contains(KEY_SWATCH) && config.wallpaperKind == WallpaperKind.PRESET) {
                val index = WallpaperPresets.indexOf(config.wallpaperPreset) // WI
                if (index >= 0) chooseSwatch(index)
            }
        }
    }

    fun chooseIconStyle(style: IconStyle) {
        _iconStyle.value = style
        saved[KEY_ICON_STYLE] = style.name
    }

    fun chooseSwatch(index: Int) {
        _swatch.value = index
        saved[KEY_SWATCH] = index
    }

    fun isDefaultLauncher(): Boolean = defaultLauncher.isDefaultLauncher()

    /** Nút chính ở chân trang. */
    fun onPrimary() {
        if (busy) return
        when (_step.value) {
            OnboardingStep.WELCOME -> go(nextOf(OnboardingStep.WELCOME))
            // TR: chỉ ghi kiểu icon; màu tô, gói icon giữ nguyên (như chọn kiểu trong Cài đặt → Bộ biểu tượng).
            OnboardingStep.ICON_STYLE -> writeThenNext {
                val style = _iconStyle.value
                appearance.update { it.copy(iconStyle = style) }
            }
            OnboardingStep.WALLPAPER -> writeThenNext {
                // WI: ghi ảnh cài sẵn đang chọn (chỉ số lạ coi như ảnh đầu).
                val preset = WallpaperPresets.list.getOrNull(_swatch.value) ?: WallpaperPresets.list.first()
                appearance.update { it.copy(wallpaperKind = WallpaperKind.PRESET, wallpaperPreset = preset.id) }
                applySystemWallpaper(preset.id)
            }
            OnboardingStep.DEFAULT_LAUNCHER -> _events.trySend(OnboardingEvent.RequestDefaultLauncher)
            OnboardingStep.DONE -> complete()
        }
    }

    /** Nút phụ (chỉ có ở 0.4: "Để sau"). */
    fun onSecondary() {
        if (busy || _step.value != OnboardingStep.DEFAULT_LAUNCHER) return
        go(OnboardingStep.DONE)
    }

    /** Kết quả đặt launcher mặc định (hộp thoại hệ thống, hoặc quay về từ màn hệ thống dự phòng). Chưa đặt thì ở lại 0.4. */
    fun onDefaultLauncherResult(isDefault: Boolean) {
        if (isDefault && _step.value == OnboardingStep.DEFAULT_LAUNCHER) go(OnboardingStep.DONE)
    }

    /** Back hệ thống / nút quay lại. false = đang ở bước đầu (Activity tự đóng, chưa đánh dấu xong: plan OB Q6). */
    fun back(): Boolean {
        if (busy) return true
        val previous = previousOf(_step.value) ?: return false
        go(previous)
        return true
    }

    /** Bỏ qua: đánh dấu xong, giữ những gì đã áp (Q2). */
    fun skip() {
        if (!busy) complete()
    }

    /** 0.5: chạm hàng tóm tắt thì về bước đó. Hàng "Màn hình chính" khi đã là mặc định thì không làm gì. */
    fun jumpTo(target: OnboardingStep) {
        if (busy || target == OnboardingStep.DONE) return
        if (target == OnboardingStep.DEFAULT_LAUNCHER && defaultLauncher.isDefaultLauncher()) return
        go(target)
    }

    private fun nextOf(step: OnboardingStep): OnboardingStep = when (step) {
        OnboardingStep.WELCOME -> OnboardingStep.ICON_STYLE
        OnboardingStep.ICON_STYLE -> OnboardingStep.WALLPAPER
        OnboardingStep.WALLPAPER ->
            if (defaultLauncher.isDefaultLauncher()) OnboardingStep.DONE else OnboardingStep.DEFAULT_LAUNCHER
        OnboardingStep.DEFAULT_LAUNCHER, OnboardingStep.DONE -> OnboardingStep.DONE
    }

    private fun previousOf(step: OnboardingStep): OnboardingStep? = when (step) {
        OnboardingStep.WELCOME -> null
        OnboardingStep.ICON_STYLE -> OnboardingStep.WELCOME
        OnboardingStep.WALLPAPER -> OnboardingStep.ICON_STYLE
        OnboardingStep.DEFAULT_LAUNCHER -> OnboardingStep.WALLPAPER
        OnboardingStep.DONE ->
            if (defaultLauncher.isDefaultLauncher()) OnboardingStep.WALLPAPER else OnboardingStep.DEFAULT_LAUNCHER
    }

    private fun go(step: OnboardingStep) {
        _step.value = step
        saved[KEY_STEP] = step.ordinal
    }

    /** Ghi config rồi sang bước sau. Ghi lỗi (đĩa đầy, DataStore hỏng) thì vẫn đi tiếp: Onboarding không được kẹt. */
    /**
     * P2-4 (chủ dự án 2026-11-19): ảnh chọn ở 0.3 tự áp làm hình nền MÀN HÌNH CHÍNH của hệ thống, không hỏi. Chạy ở scope ứng dụng (giải mã
     * + `setBitmap` mất cỡ một giây; không chặn bước kế, không bị hủy khi Onboarding đóng). Máy không cho đặt wallpaper hoặc lỗi: chỉ ghi log.
     */
    private fun applySystemWallpaper(presetId: String) {
        if (backdrop.wallpaperUnavailableReason() != null) return
        appScope.launch {
            backdrop.applyPresetAsWallpaper(presetId, WallpaperTarget.HOME)
                .onFailure { Log.w(TAG, "onboarding wallpaper failed ${it.javaClass.name}") }
        }
    }

    private fun writeThenNext(write: suspend () -> Unit) {
        busy = true
        val from = _step.value
        viewModelScope.launch {
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "write failed at $from", e)
            } finally {
                busy = false
            }
            if (_step.value == from) go(nextOf(from))
        }
    }

    private fun complete() {
        busy = true
        viewModelScope.launch {
            try {
                appState.setOnboardingDone(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "mark done failed", e)
            }
            _events.send(OnboardingEvent.Close)
        }
    }

    private companion object {
        const val TAG = "OB"
        const val KEY_STEP = "step"
        const val KEY_ICON_STYLE = "icon_style"
        const val KEY_SWATCH = "swatch"
    }
}
