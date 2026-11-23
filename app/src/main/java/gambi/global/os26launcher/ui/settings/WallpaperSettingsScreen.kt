package gambi.global.os26launcher.ui.settings

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.config.WallpaperKind
import gambi.global.os26launcher.domain.model.WallpaperTarget
import gambi.global.os26launcher.core.wallpaper.WallpaperPreset
import gambi.global.os26launcher.core.wallpaper.WallpaperPresets

/**
 * G2d Task 4 — màn "Hình nền" (2.2): hàng ảnh nền, bộ chọn ảnh, hộp chọn đích wallpaper và các sự kiện của nó. P2-4: chọn ảnh từ máy hoặc
 * ảnh cài sẵn đều hỏi đích bằng bottom sheet TRƯỚC khi đổi gì; đóng sheet = không áp vào đâu.
 */
class WallpaperSettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity
    private val viewModel = ctx.viewModel

    private var wallpaperDialog: DsDialogHandle? = null
    private var colorDialog: DsDialogHandle? = null // RS Task 8: xác nhận đặt nền (WI: ảnh cài sẵn)

    /** P3-2: ảnh vừa chọn từ máy, chờ người dùng chọn đích (chỉ để vẽ xem trước; ViewModel giữ bản chính). */
    private var pickedUri: Uri? = null

    // RS Task 8 + WI: ô của lần dựng gần nhất (chạm ô tra ảnh ở đây).
    private var placeholders: List<WallpaperPreset> = emptyList()

    // Hủy chọn (uri null) = không làm gì. Đăng ký lúc dựng màn (trước STARTED), thứ tự cố định.
    private val pickImage =
        activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                pickedUri = uri // P3-2: ảnh cho khung xem trước của sheet chọn đích
                val m = activity.resources.displayMetrics
                viewModel.onImagePicked(uri, m.widthPixels, m.heightPixels)
            }
        }

    override val titleRes: Int = R.string.settings_hub_wallpaper

    override fun rows(state: SettingsUiState): List<SettingsRow> {
        placeholders = WallpaperPresets.list
        val chosen = if (state.wallpaperKind == WallpaperKind.PRESET) {
            placeholders.indexOfFirst { it.id == state.wallpaperPreset } // WI; nền màu cũ (COLOR) thì không ô nào
        } else {
            -1
        }
        return rowsWith(state, chosen)
    }

    private fun rowsWith(state: SettingsUiState, chosen: Int): List<SettingsRow> = listOf(
        // RS Task 8: hàng chọn ảnh giữ nguyên; bên dưới lưới ô như Figma 2.2 (WI: ảnh cài sẵn, cùng bộ với onboarding 0.3).
        SettingsRow.Value(
            SettingsRow.ID_BACKDROP,
            R.string.settings_backdrop,
            // P3-2 (chủ dự án 2026-11-20, sửa lần 2): tên của ảnh ĐANG DÙNG — ảnh cài sẵn: tên ảnh (vd "Đại dương"); ảnh tự chọn: tên
            // file (thiếu thì "Đã có ảnh"); ảnh mặc định: "Ảnh mặc định".
            WallpaperPresets.of(state.wallpaperPreset)?.takeIf { state.wallpaperKind == WallpaperKind.PRESET }
                ?.let { activity.getString(it.name) }
                ?: when {
                    state.hasBackdrop -> state.pickedImageName ?: activity.getString(R.string.settings_backdrop_set)
                    state.hasDefaultBackdrop -> activity.getString(R.string.settings_backdrop_default) // WP
                    else -> activity.getString(R.string.settings_backdrop_unset)
                }
        ),
        SettingsRow.WallpaperGrid(ID_PLACEHOLDERS, placeholders, chosen)
    )

    override fun onValueClicked(row: SettingsRow.Value) {
        if (row.id == SettingsRow.ID_BACKDROP) {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) = Unit

    /**
     * P2-4: chạm ảnh cài sẵn -> bottom sheet chọn đích (Màn hình chính / Khóa / Cả hai); chọn thì ghi nền launcher + áp hệ thống, đóng sheet
     * thì không đổi gì. Máy không đặt được wallpaper: giữ hộp xác nhận cũ (RS Task 8 + WI), chỉ đổi nền launcher.
     */
    override fun onWallpaperTileClicked(index: Int) {
        if (colorDialog != null || wallpaperDialog != null) return // một hộp mỗi lúc (chống chạm đúp)
        val tile = placeholders.getOrNull(index) ?: return
        if (viewModel.uiState.value.wallpaperUnavailableReason == null) {
            showTargetSheet(
                loadImage = { WallpaperTargetSheet.decodeResource(activity, tile.full) }, // P3-2
                onChosen = { target -> viewModel.onWallpaperPresetChosen(tile.id, target) },
                onCancelled = {}
            )
            return
        }
        colorDialog = SettingsDialogs.confirm(
            activity,
            activity.getString(R.string.wallpaper_color_confirm_title, activity.getString(tile.name)),
            activity.getString(R.string.wallpaper_color_confirm_message),
            activity.getString(R.string.wallpaper_color_confirm_ok),
            onPositive = { viewModel.onWallpaperPresetChosen(tile.id) }
        ).also { d -> d.setOnDismissListener { colorDialog = null } }
    }

    override fun onEvent(event: SettingsEvent): Boolean = when (event) {
        SettingsEvent.AskWallpaperTarget -> {
            showWallpaperDialog()
            true
        }

        is SettingsEvent.WallpaperApplied -> {
            Toast.makeText(
                activity,
                activity.getString(
                    R.string.wallpaper_applied,
                    activity.getString(event.target.labelRes())
                ),
                Toast.LENGTH_LONG
            ).show()
            true
        }

        is SettingsEvent.Message -> false
    }

    override fun onDestroy() {
        // Gỡ listener trước: dismiss() sẽ gọi onDismiss, không cần làm gì thêm lúc Activity đang hủy.
        wallpaperDialog?.setOnDismissListener(null)
        wallpaperDialog?.dismiss()
        wallpaperDialog = null
        colorDialog?.setOnDismissListener(null)
        colorDialog?.dismiss()
        colorDialog = null
    }

    /** Ảnh vừa chọn từ máy (P2-4): hỏi đích; đóng sheet = bỏ ảnh, không áp vào đâu (kể cả nền launcher). */
    private fun showWallpaperDialog() {
        val m = activity.resources.displayMetrics
        val uri = pickedUri
        showTargetSheet(
            loadImage = { uri?.let { WallpaperTargetSheet.decodeUri(activity, it) } }, // P3-2
            onChosen = { target -> viewModel.onWallpaperTargetChosen(target, m.widthPixels, m.heightPixels) },
            onCancelled = viewModel::onWallpaperTargetCancelled
        )
    }

    /**
     * RS Task 3 / P2-4 / P3-2: Figma 2.2 popup 302:2090 — sheet "Đặt hình nền cho" có hai khung xem trước (Màn hình chính, Màn hình khóa) và ba
     * nút đích ([WallpaperTargetSheet]). Nút đóng / vuốt / Back = hủy cả tiến trình ([onCancelled]).
     */
    private fun showTargetSheet(
        loadImage: suspend () -> Bitmap?,
        onChosen: (WallpaperTarget) -> Unit,
        onCancelled: () -> Unit
    ) {
        if (wallpaperDialog != null) return
        wallpaperDialog = WallpaperTargetSheet.show(
            activity,
            loadImage,
            onChosen = onChosen,
            onCancelled = onCancelled,
            onDismissed = {
                wallpaperDialog = null
                pickedUri = null
            }
        )
    }

    private fun WallpaperTarget.labelRes(): Int = when (this) {
        WallpaperTarget.HOME -> R.string.wallpaper_target_home
        WallpaperTarget.LOCK -> R.string.wallpaper_target_lock
        WallpaperTarget.BOTH -> R.string.wallpaper_target_both
    }

    private companion object {
        const val ID_PLACEHOLDERS = "wallpaper_placeholders" // RS Task 8
    }
}
