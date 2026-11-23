package gambi.global.os26launcher.ui.settings

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.glass.GlassEnvironment
import gambi.global.os26launcher.domain.config.AppearanceRepository
import gambi.global.os26launcher.domain.config.GeneralConfigRepository
import gambi.global.os26launcher.core.wallpaper.WallpaperPresets
import gambi.global.os26launcher.domain.config.WallpaperKind
import gambi.global.os26launcher.domain.model.GlassElement
import gambi.global.os26launcher.domain.model.GlassLevelOverride
import gambi.global.os26launcher.domain.model.GlassLook
import gambi.global.os26launcher.domain.model.GlassLookParam
import gambi.global.os26launcher.domain.model.GridPreset
import gambi.global.os26launcher.domain.model.IconPackInfo
import gambi.global.os26launcher.domain.model.IconStyle
import gambi.global.os26launcher.domain.model.IconTint
import gambi.global.os26launcher.domain.model.WallpaperTarget
import gambi.global.os26launcher.domain.repository.BackdropRepository
import gambi.global.os26launcher.domain.repository.DefaultLauncherRepository
import gambi.global.os26launcher.domain.repository.IconPackRepository
import gambi.global.os26launcher.domain.usecase.ChangeGridPresetUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** M8a Task 3 — trạng thái màn Cài đặt. Giá trị hiển thị luôn lấy từ flow (nguồn thật), không giữ bản sao riêng. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appearance: AppearanceRepository,
    private val generalConfig: GeneralConfigRepository,
    private val defaultLauncherRepository: DefaultLauncherRepository,
    private val backdropRepository: BackdropRepository,
    private val changeGridPreset: ChangeGridPresetUseCase,
    private val glassEnvironment: GlassEnvironment, // GS
    private val iconPacks: IconPackRepository // IP
) : ViewModel() {

    private data class DefaultInfo(val isDefault: Boolean, val canRequest: Boolean)

    private val defaultInfo = MutableStateFlow(readDefaultInfo())

    // hasBackdrop() là kiểm tra file (không phải flow): đọc lúc khởi tạo và sau mỗi lần nhập ảnh thành công.
    private val hasBackdrop = MutableStateFlow(backdropRepository.hasBackdrop())
    private val wallpaperUnavailableReason: String? =
        backdropRepository.wallpaperUnavailableReason()
    private var importing = false
    private val applyingGrid = MutableStateFlow(false)

    // IP: tên gói đang chọn; hỏi PackageManager ngoài luồng chính. null = không dùng hoặc gói không còn cài.
    private val packLabel: Flow<Pair<String?, String?>> = appearance.config
        .map { it.iconPack }
        .distinctUntilChanged()
        .map { pkg -> pkg to pkg?.let(iconPacks::labelOf) }
        .flowOn(Dispatchers.IO)

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(appearance.config, generalConfig.config) { a, g -> a to g },
        defaultInfo,
        combine(hasBackdrop, applyingGrid, packLabel) { hasImage, applying, pack ->
            Triple(
                hasImage,
                applying,
                pack
            )
        }
    ) { (settings, general), info, (hasImage, applying, pack) ->
        SettingsUiState(
            loading = false,
            isDefaultLauncher = info.isDefault,
            canRequestDefault = info.canRequest,
            showLabels = settings.showLabels,
            glassOverride = settings.glassLevel,
            // RS Task 8 + WI: nền màu / ảnh cài sẵn thay ảnh tự chọn (như BackdropRepository.hasBackdrop)
            hasBackdrop = hasImage && settings.wallpaperKind != WallpaperKind.COLOR &&
                !(settings.wallpaperKind == WallpaperKind.PRESET && WallpaperPresets.of(settings.wallpaperPreset) != null),
            hasDefaultBackdrop = backdropRepository.hasDefaultBackdrop(),
            gridPreset = general.gridPreset,
            navBarHidden = general.navBarHidden, // PB Task 4
            applyingGrid = applying,
            wallpaperUnavailableReason = wallpaperUnavailableReason,
            iconStyle = settings.iconStyle,
            iconTint = settings.iconTint,
            iconPack = pack.second?.let { pack.first }, // gói đã gỡ (không có tên) coi như không dùng
            iconPackLabel = pack.second,
            glassCustom = settings.glassCustom, // GS
            realGlassCapable = glassEnvironment.realGlassCapable,
            glassLook = settings.glassLook, // GT
            themeId = settings.themeId, // RS
            wallpaperKind = settings.wallpaperKind, // RS Task 8
            wallpaperColor = settings.wallpaperColor,
            wallpaperPreset = settings.wallpaperPreset, // WI
            pickedImageName = settings.pickedImageName // P3-2
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    /** Gọi ở onResume: người dùng có thể vừa đổi launcher mặc định ở màn hệ thống. */
    fun refreshDefaultLauncher() {
        defaultInfo.value = readDefaultInfo()
    }

    fun createSetDefaultIntent(): Intent? = defaultLauncherRepository.createSetDefaultIntent()

    // B12
    fun isDefaultLauncher(): Boolean = defaultLauncherRepository.isDefaultLauncher()

    fun onShowLabelsChanged(checked: Boolean) {
        viewModelScope.launch {
            try {
                appearance.update { it.copy(showLabels = checked) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Công tắc tự trở về giá trị thật vì hàng được dựng lại từ flow (giá trị không đổi nên cần ép bind lại: xem Activity)
                Log.e(TAG, "setShowLabels($checked) failed", e)
                _events.trySend(SettingsEvent.Message(R.string.settings_save_failed))
            }
        }
    }

    /** PB Task 4: Home thu `GeneralConfig` và tự ẩn/hiện thanh điều hướng, không cần dựng lại. */
    fun onNavBarHiddenChanged(checked: Boolean) {
        viewModelScope.launch {
            try {
                generalConfig.update { it.copy(navBarHidden = checked) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "setNavBarHidden($checked) failed", e)
                _events.trySend(SettingsEvent.Message(R.string.settings_save_failed))
            }
        }
    }

    /**
     * M8a Task 4: ghi xong (suspend, DataStore đã commit) rồi mới trả về. HomeActivity.onResume đọc lại giá trị này để
     * tự tạo lại -- vì vậy chỉ ghi ở đây, KHÔNG recreate từ màn Cài đặt (nó không sở hữu dock).
     */
    fun onGlassOverrideChosen(value: GlassLevelOverride) {
        viewModelScope.launch {
            try {
                appearance.update { it.copy(glassLevel = value) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "setGlassLevelOverride($value) failed", e)
                _events.trySend(SettingsEvent.Message(R.string.settings_save_failed))
            }
        }
    }

    /**
     * M8a Task 5: người dùng đã chọn ảnh. Nhập thất bại -> giữ ảnh cũ (importBackdrop chỉ ghi file khi giải mã xong).
     * Thành công -> hỏi đích wallpaper (nếu máy đặt được), còn không thì Toast nêu lý do; nền trong app vẫn đổi (Home tự
     * nạp lại ở onResume nhờ backdropVersion()).
     */
    // P2-4: ảnh vừa chọn, chờ người dùng chọn đích ở bottom sheet. Hủy bottom sheet = bỏ ảnh, không áp vào đâu (kể cả nền launcher).
    private var pendingImage: Uri? = null

    /**
     * P2-4 (chủ dự án 2026-11-19): chọn ảnh xong CHƯA nhập: hỏi đích trước ([SettingsEvent.AskWallpaperTarget]); chỉ khi chọn đích mới nhập
     * làm nền launcher và áp hệ thống ([onWallpaperTargetChosen]). Máy không đặt được wallpaper: chỉ đổi nền launcher (không có gì để hỏi).
     */
    fun onImagePicked(uri: Uri, width: Int, height: Int) {
        if (importing || width <= 0 || height <= 0) return
        val reason = wallpaperUnavailableReason
        if (reason == null) {
            pendingImage = uri
            _events.trySend(SettingsEvent.AskWallpaperTarget)
            return
        }
        importImage(uri, width, height) { ok ->
            if (ok) _events.trySend(SettingsEvent.Message(R.string.settings_wallpaper_unavailable, reason))
        }
    }

    /** P2-4: bottom sheet đóng mà không chọn đích. */
    fun onWallpaperTargetCancelled() {
        pendingImage = null
    }

    private fun importImage(uri: Uri, width: Int, height: Int, then: suspend (Boolean) -> Unit) {
        if (importing) return
        importing = true
        viewModelScope.launch {
            try {
                val bmp = backdropRepository.importBackdrop(uri, width, height)
                hasBackdrop.value = backdropRepository.hasBackdrop()
                if (bmp == null) _events.trySend(SettingsEvent.Message(R.string.settings_backdrop_failed))
                then(bmp != null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "importBackdrop failed", e)
                _events.trySend(SettingsEvent.Message(R.string.settings_backdrop_failed))
            } finally {
                importing = false
            }
        }
    }

    /** [target] null = "Không áp dụng": chỉ đổi nền trong app. */
    /** P2-4: đích đã chọn cho ảnh đang chờ: nhập làm nền launcher rồi áp hệ thống. */
    fun onWallpaperTargetChosen(target: WallpaperTarget?, width: Int, height: Int) {
        val uri = pendingImage
        pendingImage = null
        if (uri == null || target == null || width <= 0 || height <= 0) return
        importImage(uri, width, height) { ok ->
            if (!ok) return@importImage
            reportApply(target, backdropRepository.applyAsWallpaper(target, width, height))
        }
    }

    private fun reportApply(target: WallpaperTarget, result: Result<Unit>) {
        result.fold(
            onSuccess = { _events.trySend(SettingsEvent.WallpaperApplied(target)) },
            onFailure = {
                Log.w(TAG, "applyAsWallpaper($target) failed", it)
                _events.trySend(SettingsEvent.Message(R.string.wallpaper_failed, it.message))
            }
        )
    }

    /**
     * M8a Task 6: người dùng đã xác nhận đổi cỡ lưới. Chạy trong [NonCancellable]: thoát màn Cài đặt giữa chừng không được
     * bỏ dở giữa hai bước ghi (use case giữ icon luôn nằm trong lưới hợp lệ, nhưng cứ làm cho xong). Lỗi -> Toast + log;
     * giá trị hiển thị luôn lấy từ flow nên hàng tự về đúng cỡ thật.
     */
    fun onGridPresetConfirmed(preset: GridPreset) {
        if (applyingGrid.value) return
        applyingGrid.value = true
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { changeGridPreset(preset) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "changeGridPreset($preset) failed", e)
                _events.trySend(SettingsEvent.Message(R.string.settings_grid_failed))
            } finally {
                applyingGrid.value = false
            }
        }
    }

    /**
     * G2b: ghi kiểu icon. Chỉ ghi; IconStyleSource tự thấy giá trị mới (xóa cache icon). Home đang chạy làm mới ở pha B
     * (hook RecreateGuard của R1), ở pha A thì thấy kiểu mới khi Home dựng lại.
     */
    fun onIconStyleChosen(style: IconStyle) {
        viewModelScope.launch {
            try {
                // P3-3 (chủ dự án 2026-11-20, phương án b): người dùng tự chọn kiểu thì tắt lớp nổi 3D mà theme bật (iconBevel), để kiểu hiện
                // đúng như tên ("Mặc định" phẳng). Chọn lại đúng kiểu đang dùng vẫn tắt nổi; ghi trùng thì repository bỏ qua (putIfChanged).
                appearance.update { it.copy(iconStyle = style, iconBevel = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "setIconStyle($style) failed", e)
                _events.trySend(SettingsEvent.Message(R.string.settings_save_failed))
            }
        }
    }

    /** IP: các gói biểu tượng đã cài (chậm: IPC + mở tài nguyên gói). */
    suspend fun installedIconPacks(): List<IconPackInfo> = iconPacks.installedPacks()

    /** IP: chọn gói (null = không dùng). Chỉ ghi; IconStyleSource và RecreateGuard của Home lo phần làm mới. */
    fun onIconPackChosen(packageName: String?) {
        if (packageName == uiState.value.iconPack) return
        viewModelScope.launch {
            try {
                appearance.update { it.copy(iconPack = packageName) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "setIconPack failed", e)
                _events.trySend(SettingsEvent.Message(R.string.settings_save_failed))
            }
        }
    }

    /**
     * RS Task 8 + WI: chọn ô ảnh cài sẵn ở màn 2.2, ghi như onboarding 0.3 (`WallpaperKind.PRESET` + id). Ảnh đã chọn (nếu có) vẫn giữ
     * trong bộ nhớ app, chọn lại ở "Chọn ảnh của bạn" thì nền về ảnh (BackdropRepository).
     */
    /**
     * WI: ghi ảnh cài sẵn [id] làm nền launcher. P2-4: [target] khác null thì áp luôn làm wallpaper hệ thống (người dùng đã chọn đích ở
     * bottom sheet); null = chỉ nền launcher (máy không đặt được wallpaper).
     */
    fun onWallpaperPresetChosen(id: String, target: WallpaperTarget? = null) {
        viewModelScope.launch {
            try {
                appearance.update { it.copy(wallpaperKind = WallpaperKind.PRESET, wallpaperPreset = id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "setWallpaperPreset failed", e)
                _events.trySend(SettingsEvent.Message(R.string.settings_save_failed))
                return@launch
            }
            if (target != null) reportApply(target, backdropRepository.applyPresetAsWallpaper(id, target))
        }
    }

    fun onIconTintChosen(tint: IconTint) {
        if (tint == uiState.value.iconTint) return
        viewModelScope.launch {
            try {
                appearance.update { it.copy(iconTint = tint) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "setIconTint($tint) failed", e)
                _events.trySend(SettingsEvent.Message(R.string.settings_save_failed))
            }
        }
    }

    /**
     * GS: chọn mức Tùy chỉnh. Chưa từng có cấu hình tùy chỉnh -> gieo công tắc từ thứ máy đang hiển thị dưới mức vừa rời (vào Tùy
     * chỉnh không đổi màn hình); đã có -> giữ nguyên (có thể đổi màn hình: đó là cài đặt được nhớ). Ghi công tắc TRƯỚC, mức sau:
     * giữa hai lần ghi mức chưa phải Tùy chỉnh nên công tắc chưa có tác dụng. Chỉ ghi; Home tự dựng lại ở onResume.
     */
    fun onGlassCustomChosen() {
        viewModelScope.launch {
            try {
                val current = appearance.config.first()
                if (!appearance.glassCustomSaved.first()) {
                    appearance.seedGlassCustom(glassEnvironment.seedFor(current.glassLevel))
                }
                appearance.update { it.copy(glassLevel = GlassLevelOverride.CUSTOM) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "onGlassCustomChosen failed", e)
                _events.trySend(SettingsEvent.Message(R.string.settings_save_failed))
            }
        }
    }

    /** GS: bật/tắt một công tắc kính tùy chỉnh (cảnh báo API < 33 do Activity hỏi trước khi gọi). Lỗi -> Toast, hàng dựng lại từ flow. */
    fun onGlassCustomToggled(element: GlassElement, on: Boolean) {
        viewModelScope.launch {
            try {
                appearance.update { it.copy(glassCustom = it.glassCustom.with(element, on)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "setGlassCustomElement($element, $on) failed", e)
                _events.trySend(SettingsEvent.Message(R.string.settings_save_failed))
            }
        }
    }

    /** GT: ảnh nền của Home cho khung xem trước (ảnh đã chọn, không có thì ảnh mặc định); null nếu không có gì hoặc lỗi đọc. */
    suspend fun loadBackdropForPreview(width: Int, height: Int): Bitmap? = try {
        when {
            width <= 0 || height <= 0 -> null
            backdropRepository.hasBackdrop() -> backdropRepository.loadBackdrop(width, height)
            backdropRepository.hasDefaultBackdrop() -> backdropRepository.loadDefaultBackdrop(width, height)
            else -> null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "loadBackdropForPreview failed", e)
        null
    }

    // GT: hai thanh trượt nhả gần nhau không được ghi đè nhau -> đọc giá trị đã lưu rồi ghi, lần lượt.
    private val glassLookMutex = Mutex()

    /** GT: người dùng nhả một thanh trượt kính. Chỉ ghi; Home tự dựng lại ở onResume (thông số nằm trong GlassConfig). */
    fun onGlassLookChanged(param: GlassLookParam, value: Int) = saveGlassLook { it.with(param, value) }

    fun onGlassLookReset() = saveGlassLook { GlassLook.DEFAULT }

    private fun saveGlassLook(change: (GlassLook) -> GlassLook) {
        viewModelScope.launch {
            try {
                glassLookMutex.withLock {
                    val current = appearance.config.first().glassLook
                    val next = change(current)
                    if (next != current) appearance.update { it.copy(glassLook = next) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "setGlassLook failed", e)
                _events.trySend(SettingsEvent.Message(R.string.settings_save_failed))
            }
        }
    }

    private fun readDefaultInfo(): DefaultInfo {
        val isDefault = defaultLauncherRepository.isDefaultLauncher()
        return DefaultInfo(
            isDefault,
            !isDefault && defaultLauncherRepository.createSetDefaultIntent() != null
        )
    }

    private companion object {
        const val TAG = "SettingsVM"
    }
}
