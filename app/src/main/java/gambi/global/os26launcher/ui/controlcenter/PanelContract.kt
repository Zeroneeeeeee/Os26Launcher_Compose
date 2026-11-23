package gambi.global.os26launcher.ui.controlcenter

import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.controlcenter.CustomTileType

// JS7b: phần dùng chung giữa panel Compose, màn chọn ô và các View cũ (chuyển từ MediaViews.kt / ControlCenterView.kt, nội dung giữ
// nguyên), để xoá View cũ sau này không kéo theo.

/** G3d Task 7: nút điều khiển nhạc của panel (lùi/phát/tới). */
interface MediaActions {
    fun onPrevious()
    fun onPlayPause()
    fun onNext()
    fun onSeek(positionMs: Long)
    fun onOpenApp()
    fun onOutput()
}

/** G3d: icon và nhãn của từng loại ô tùy chỉnh (RE mục 5.3). */
internal fun CustomTileType.iconRes(): Int = when (this) {
    CustomTileType.FLASHLIGHT -> R.drawable.ic_cc_flashlight
    CustomTileType.TIMER -> R.drawable.ic_cc_timer
    CustomTileType.CALCULATOR -> R.drawable.ic_cc_calculator
    CustomTileType.CAMERA -> R.drawable.ic_cc_camera
    CustomTileType.SCREEN_RECORD -> R.drawable.ic_cc_screen_record
    CustomTileType.BATTERY -> R.drawable.ic_cc_battery
    CustomTileType.VOICE_MEMO -> R.drawable.ic_cc_voice_memo
    CustomTileType.SETTINGS -> R.drawable.ic_cc_settings
    CustomTileType.DARK_MODE -> R.drawable.ic_cc_dark_mode
    CustomTileType.NOTES -> R.drawable.ic_cc_notes
    CustomTileType.NIGHT_SHIFT -> R.drawable.ic_cc_night_shift
    CustomTileType.SCREEN_MIRROR -> R.drawable.ic_cc_screen_mirror // TF
}

internal fun CustomTileType.labelRes(): Int = when (this) {
    CustomTileType.FLASHLIGHT -> R.string.cc_tile_flashlight
    CustomTileType.TIMER -> R.string.cc_tile_timer
    CustomTileType.CALCULATOR -> R.string.cc_tile_calculator
    CustomTileType.CAMERA -> R.string.cc_tile_camera
    CustomTileType.SCREEN_RECORD -> R.string.cc_tile_screen_record
    CustomTileType.BATTERY -> R.string.cc_tile_battery
    CustomTileType.VOICE_MEMO -> R.string.cc_tile_voice_memo
    CustomTileType.SETTINGS -> R.string.cc_tile_settings
    CustomTileType.DARK_MODE -> R.string.cc_tile_dark_mode
    CustomTileType.NOTES -> R.string.cc_tile_notes
    CustomTileType.NIGHT_SHIFT -> R.string.cc_tile_night_shift
    CustomTileType.SCREEN_MIRROR -> R.string.cc_tile_screen_mirror // TF
}

/** Ô nào là công tắc (đọc được Bật/Tắt), ô nào chỉ là lối tắt. */
internal fun CustomTileType.isToggle(): Boolean = when (this) {
    CustomTileType.FLASHLIGHT, CustomTileType.BATTERY, CustomTileType.DARK_MODE, CustomTileType.NIGHT_SHIFT -> true
    else -> false
}
