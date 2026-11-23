package gambi.global.os26launcher.domain.config

import gambi.global.os26launcher.domain.model.GlassLevelOverride
import gambi.global.os26launcher.domain.model.GlassLook
import gambi.global.os26launcher.domain.model.IconStyle
import gambi.global.os26launcher.domain.model.IconTint

/**
 * CF — bốn theme cài sẵn (màn 1.1, onboarding 0.2). Theme là một [AppearanceConfig]; áp theme = chép mọi trường "theme ghi" của preset
 * (`docs/config-schema.md` mục 6), giữ [AppearanceConfig.iconPack] và [AppearanceConfig.glassCustom] (lựa chọn riêng). Giá trị chốt
 * 2026-11-17; Classic 26, Minimal, Dark Pro chỉnh theo ảnh xem trước màn 0.2 (Figma `302:3704`) cùng ngày. Nền màu là lựa chọn tạm
 * tới khi có thư viện hình nền.
 */
object ThemePresets {

    fun of(id: ThemeId): AppearanceConfig = when (id) {
        ThemeId.LIQUID_GLASS_27 -> AppearanceConfig(
            wallpaperKind = WallpaperKind.COLOR, wallpaperColor = 0xFF1E3A8A.toInt(),
            iconStyle = IconStyle.GLASS, iconTint = IconTint.BLUE, iconBevel = true,
            iconShape = IconShapeKind.SQUIRCLE, iconCorner = 62, iconSize = 50,
            showLabels = true,
            glassLevel = GlassLevelOverride.AUTO,
            glassLook = GlassLook(refraction = 40, gloss = 60, blur = 55, corner = 50),
            glassTransparency = 70, glassTint = 0, surfaceTone = SurfaceTone.LIGHT
        )

        ThemeId.CLASSIC_26 -> AppearanceConfig(
            wallpaperKind = WallpaperKind.COLOR, wallpaperColor = 0xFF27142C.toInt(), // tím mận tối, lấy màu ảnh xem trước 0.2
            iconStyle = IconStyle.DEFAULT, iconTint = IconTint.BLUE,
            iconShape = IconShapeKind.SQUIRCLE, iconCorner = 62, iconSize = 50,
            showLabels = true,
            glassLevel = GlassLevelOverride.FORCE_LOW,
            glassLook = GlassLook(refraction = 0, gloss = 30, blur = 50, corner = 50),
            glassTransparency = 80, glassTint = 0, surfaceTone = SurfaceTone.LIGHT
        )

        ThemeId.MINIMAL -> AppearanceConfig(
            wallpaperKind = WallpaperKind.COLOR, wallpaperColor = 0xFF11141B.toInt(), // gần đen ánh xanh, lấy màu ảnh xem trước 0.2
            iconStyle = IconStyle.TRANSPARENT, iconTint = IconTint.BLUE,
            iconShape = IconShapeKind.SQUARE, iconCorner = 30, iconSize = 50,
            showLabels = false,
            glassLevel = GlassLevelOverride.FORCE_LOW,
            glassLook = GlassLook(refraction = 0, gloss = 20, blur = 40, corner = 30),
            glassTransparency = 90, glassTint = 0, surfaceTone = SurfaceTone.LIGHT
        )

        ThemeId.DARK_PRO -> AppearanceConfig(
            wallpaperKind = WallpaperKind.COLOR, wallpaperColor = 0xFF000000.toInt(),
            // Ô icon gradient đen + hình trắng-xám = Tô màu GRAPHITE (ô V 0,27 -> 0,15, không sắc độ).
            iconStyle = IconStyle.TINTED, iconTint = IconTint.GRAPHITE, iconBevel = true,
            iconShape = IconShapeKind.CIRCLE, iconCorner = 100, iconSize = 50,
            showLabels = true,
            glassLevel = GlassLevelOverride.AUTO,
            glassLook = GlassLook(refraction = 20, gloss = 40, blur = 60, corner = 50),
            glassTransparency = 50, glassTint = 0, surfaceTone = SurfaceTone.DARK
        )
    }

    /**
     * Chép các trường theme ghi từ preset [id] lên config này; giữ gói icon và công tắc kính tùy chỉnh. WI (2026-11-18): giữ cả hình nền
     * (nguồn, màu, ảnh cài sẵn): từ khi có ảnh cài sẵn, hình nền là lựa chọn riêng, áp theme không đổi nền và chọn nền không làm theme
     * thành "đã chỉnh". Nền màu của preset chỉ còn là giá trị tham khảo.
     */
    fun AppearanceConfig.applyPreset(id: ThemeId): AppearanceConfig =
        of(id).copy(
            themeId = id, iconPack = iconPack, glassCustom = glassCustom,
            wallpaperKind = wallpaperKind, wallpaperColor = wallpaperColor, wallpaperPreset = wallpaperPreset,
            pickedImageName = pickedImageName // P3-2
        )
}
