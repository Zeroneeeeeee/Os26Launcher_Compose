package gambi.global.os26launcher.ui.settings

import gambi.global.os26launcher.domain.config.ThemeId
import gambi.global.os26launcher.domain.config.WallpaperKind
import gambi.global.os26launcher.domain.model.GlassCustom
import gambi.global.os26launcher.domain.model.GlassLevelOverride
import gambi.global.os26launcher.domain.model.GlassLook
import gambi.global.os26launcher.domain.model.GridPreset
import gambi.global.os26launcher.domain.model.IconStyle
import gambi.global.os26launcher.domain.model.IconTint

/** M8a Task 3 — các Task sau thêm trường (không đổi tên trường cũ). */
data class SettingsUiState(
    /** true cho tới khi cài đặt phát lần đầu: ẩn danh sách, hiện tiến trình. */
    val loading: Boolean = true,
    val isDefaultLauncher: Boolean = false,
    /** Chưa là mặc định VÀ máy có đường mở màn chọn launcher. */
    val canRequestDefault: Boolean = false,
    val showLabels: Boolean = true,
    val glassOverride: GlassLevelOverride = GlassLevelOverride.AUTO,
    val gridPreset: GridPreset = GridPreset.DEFAULT,
    /** PB Task 4: ẩn thanh điều hướng ở Home (`general_nav_bar_hidden`). */
    val navBarHidden: Boolean = false,
    /** Đang đổi cỡ lưới (xếp lại icon): khóa hàng Cỡ lưới và hiện tiến trình. */
    val applyingGrid: Boolean = false,
    val hasBackdrop: Boolean = false,
    /** P3-2: tên ảnh tự chọn gần nhất (hàng "Ảnh nền" hiện tên này trong mọi trường hợp), null = chưa có. */
    val pickedImageName: String? = null,
    /** WP: có ảnh mặc định (iOS 27, bản thử) khi chưa chọn ảnh. */
    val hasDefaultBackdrop: Boolean = false,
    /** Lý do máy không đặt được wallpaper hệ thống, null nếu đặt được (tính một lần). */
    val wallpaperUnavailableReason: String? = null,
    // G2b
    val iconStyle: IconStyle = IconStyle.DEFAULT,
    val iconTint: IconTint = IconTint.DEFAULT,
    /** IP: package gói biểu tượng đang chọn và tên hiển thị; null khi không dùng hoặc gói đã gỡ. */
    val iconPack: String? = null,
    val iconPackLabel: String? = null,
    // GS: công tắc kính tùy chỉnh (chỉ hiện khi glassOverride = CUSTOM) và máy có đường kính thật (API >= 33) hay không (để cảnh báo)
    val glassCustom: GlassCustom = GlassCustom(),
    val realGlassCapable: Boolean = true,
    // GT: thông số kính dùng chung (bốn thanh trượt)
    val glassLook: GlassLook = GlassLook.DEFAULT,
    /** RS: theme đang chọn (card "Launcher hiện tại"); null = chưa từng chọn theme. */
    val themeId: ThemeId? = null,
    /** RS Task 8: nguồn nền và màu nền (ô nền màu ở màn 2.2 biết ô nào đang chọn). */
    val wallpaperKind: WallpaperKind = WallpaperKind.AUTO,
    val wallpaperColor: Int = 0,
    /** WI: id ảnh cài sẵn đang dùng (chỉ có nghĩa khi [wallpaperKind] = PRESET). */
    val wallpaperPreset: String? = null
)
