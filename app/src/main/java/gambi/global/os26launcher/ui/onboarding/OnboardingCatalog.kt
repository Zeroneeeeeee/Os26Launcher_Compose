package gambi.global.os26launcher.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.config.ThemeId

/** OB 0.2 — thẻ theme: chữ và ảnh xem trước (xuất từ Figma `302:3706/3715/3721/3727`, đã bỏ dấu chọn và giờ, xem plan OB Q1). */
data class ThemeInfo(
    val id: ThemeId,
    @StringRes val name: Int,
    @StringRes val description: Int,
    @DrawableRes val preview: Int,
    val isNew: Boolean = false
)

object ThemeCatalog {
    val list: List<ThemeInfo> = listOf(
        ThemeInfo(ThemeId.LIQUID_GLASS_27, R.string.onboarding_theme_liquid_glass_27, R.string.onboarding_theme_liquid_glass_27_desc, R.drawable.onb_theme_liquid_glass_27),
        ThemeInfo(ThemeId.CLASSIC_26, R.string.onboarding_theme_classic_26, R.string.onboarding_theme_classic_26_desc, R.drawable.onb_theme_classic_26),
        ThemeInfo(ThemeId.MINIMAL, R.string.onboarding_theme_minimal, R.string.onboarding_theme_minimal_desc, R.drawable.onb_theme_minimal),
        ThemeInfo(ThemeId.DARK_PRO, R.string.onboarding_theme_dark_pro, R.string.onboarding_theme_dark_pro_desc, R.drawable.onb_theme_dark_pro, isNew = true)
    )

    fun of(id: ThemeId): ThemeInfo = list.first { it.id == id }
}

/* WI (2026-11-18): ô màu 0.3 (`WallpaperSwatches`) thay bằng ảnh cài sẵn `core.wallpaper.WallpaperPresets`, bỏ ô "Theo giao diện". */
