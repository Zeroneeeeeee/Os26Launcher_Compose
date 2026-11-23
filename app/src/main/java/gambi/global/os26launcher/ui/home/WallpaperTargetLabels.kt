package gambi.global.os26launcher.ui.home

import androidx.annotation.StringRes
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.WallpaperTarget

/** Nhãn của đích áp ảnh nền (Home / Khoá / Cả hai). JX: chuyển từ `BackdropController` (Home View) sang đây. */
@StringRes
internal fun WallpaperTarget.labelRes(): Int = when (this) {
    WallpaperTarget.HOME -> R.string.wallpaper_target_home
    WallpaperTarget.LOCK -> R.string.wallpaper_target_lock
    WallpaperTarget.BOTH -> R.string.wallpaper_target_both
}
