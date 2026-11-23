package gambi.global.os26launcher.domain.config

import gambi.global.os26launcher.domain.model.GlassCustom
import gambi.global.os26launcher.domain.model.GlassLevelOverride
import gambi.global.os26launcher.domain.model.GlassLook
import gambi.global.os26launcher.domain.model.IconStyle
import gambi.global.os26launcher.domain.model.IconTint

/**
 * CF — nhóm "Giao diện": mọi trường theme có thể ghi, cộng [iconPack] và [glassCustom] (lựa chọn riêng, theme không đụng).
 * Khóa, mặc định và màn của từng trường: `docs/config-schema.md` mục 3.1. Mặc định = hành vi hiện tại của app.
 */
data class AppearanceConfig(
    val themeId: ThemeId? = null,
    val wallpaperKind: WallpaperKind = WallpaperKind.AUTO,
    val wallpaperColor: Int = DEFAULT_WALLPAPER_COLOR,
    /** WI: id hình nền cài sẵn (`WallpaperPresets`), chỉ có nghĩa khi [wallpaperKind] = PRESET. Theme không ghi. */
    val wallpaperPreset: String? = null,
    /**
     * P3-2: tên (bỏ đuôi) của ảnh người dùng TỰ CHỌN gần nhất, hiện ở hàng "Ảnh nền" khi nguồn nền là ảnh tự chọn (ảnh cài sẵn hiện tên
     * ảnh cài sẵn, ảnh mặc định hiện "Ảnh mặc định"). null = chưa từng chọn, hoặc trình chọn ảnh không trả tên. Theme không ghi.
     */
    val pickedImageName: String? = null,
    val iconStyle: IconStyle = IconStyle.DEFAULT,
    /** Chỉ có nghĩa khi [iconStyle] là TINTED. */
    val iconTint: IconTint = IconTint.DEFAULT,
    /** CF Step 7: lớp nổi 3D chồng lên [iconStyle] (Kính, Tô màu, Trong suốt). Chỉ theme ghi; màn 2.3 không có. THREE_D luôn nổi. */
    val iconBevel: Boolean = false,
    /** IP: package gói biểu tượng, null = không dùng. Theme không ghi. */
    val iconPack: String? = null,
    val iconShape: IconShapeKind = IconShapeKind.SQUIRCLE,
    val iconCorner: Int = 62,
    val iconSize: Int = 50,
    val showLabels: Boolean = true,
    val glassLevel: GlassLevelOverride = GlassLevelOverride.AUTO,
    /** GS: công tắc kính thật/mờ từng phần tử; chỉ có nghĩa khi [glassLevel] = CUSTOM. Theme không ghi. */
    val glassCustom: GlassCustom = GlassCustom(),
    val glassLook: GlassLook = GlassLook.DEFAULT,
    val glassTransparency: Int = 70,
    /** ARGB; 0 = không tô. */
    val glassTint: Int = 0,
    val surfaceTone: SurfaceTone = SurfaceTone.LIGHT
) {
    companion object {
        const val DEFAULT_WALLPAPER_COLOR: Int = 0xFF000000.toInt()

        /** Khoảng của các trường phần trăm (iconCorner, iconSize, glassTransparency). */
        const val PERCENT_MIN = 0
        const val PERCENT_MAX = 100
    }
}
