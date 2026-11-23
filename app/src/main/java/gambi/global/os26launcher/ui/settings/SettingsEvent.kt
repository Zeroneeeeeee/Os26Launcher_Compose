package gambi.global.os26launcher.ui.settings

import androidx.annotation.StringRes
import gambi.global.os26launcher.domain.model.WallpaperTarget

/** M8a Task 5 — sự kiện một lần của màn Cài đặt (không được phát lại khi Activity dựng lại). */
sealed interface SettingsEvent {
    /** Toast. [arg] (nếu có) thế vào `%1$s` của [res]; null thì Activity dùng "không rõ lý do". */
    data class Message(@StringRes val res: Int, val arg: String? = null) : SettingsEvent

    /** Ảnh nền vừa nhập xong và máy đặt được wallpaper: hỏi đích áp dụng. */
    data object AskWallpaperTarget : SettingsEvent

    data class WallpaperApplied(val target: WallpaperTarget) : SettingsEvent
}
