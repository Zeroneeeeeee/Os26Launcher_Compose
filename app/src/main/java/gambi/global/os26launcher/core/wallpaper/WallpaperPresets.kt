package gambi.global.os26launcher.core.wallpaper

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import gambi.global.os26launcher.R

/**
 * WI — một hình nền cài sẵn: [id] lưu trong `appearance_wallpaper_preset` (không đổi id đã phát hành), ảnh đầy đủ [full] cho Home,
 * ảnh nhỏ [thumb] (400 px rộng, đủ nét cho khung xem trước 176 dp ở 0.3) cho ô chọn ở 0.3 và 2.2.
 */
data class WallpaperPreset(
    val id: String,
    @StringRes val name: Int,
    @DrawableRes val full: Int,
    @DrawableRes val thumb: Int,
    val isNew: Boolean = false
)

/**
 * WI — hình nền cài sẵn. Từ 2026-11-20: 6 ảnh của bản thiết kế (Figma `syeBLaaxlBxm1Gy352Yer3`, trang "Asset & thử nghiệm", node
 * `Wallpaper / …` 570:2455–570:2460), xuất 1320 × 2264, WebP chất lượng 90; ảnh nhỏ 400 px rộng. Thay 5 ảnh iOS 26/27 thử nghiệm
 * (`wi_wallpaper_1..5`, đã xóa). Id `wi_*` cũ không còn: người đã chọn ảnh cũ rơi về như AUTO ([of] trả null).
 * WI (2026-11-25): thêm 4 ảnh iOS 27 (`wp_ios27_1..4`, 1320 × 2868) lên ĐẦU danh sách; ô đầu ([DEFAULT_ID]) là ô chọn sẵn ở 0.3 và
 * cũng là ảnh mặc định khi chưa chọn gì (`DefaultBackdropSource`).
 */
object WallpaperPresets {
    /** WI: id ảnh cài sẵn mặc định (lần chạy đầu / chưa chọn). Phải là phần tử đầu của [list]. */
    const val DEFAULT_ID = "wp_ios27_1"

    val list: List<WallpaperPreset> = listOf(
        WallpaperPreset("wp_ios27_1", R.string.wallpaper_preset_ios27_1, R.drawable.wp_ios27_1, R.drawable.wp_ios27_1_thumb, isNew = true),
        WallpaperPreset("wp_ios27_2", R.string.wallpaper_preset_ios27_2, R.drawable.wp_ios27_2, R.drawable.wp_ios27_2_thumb, isNew = true),
        WallpaperPreset("wp_ios27_3", R.string.wallpaper_preset_ios27_3, R.drawable.wp_ios27_3, R.drawable.wp_ios27_3_thumb, isNew = true),
        WallpaperPreset("wp_ios27_4", R.string.wallpaper_preset_ios27_4, R.drawable.wp_ios27_4, R.drawable.wp_ios27_4_thumb, isNew = true),
        WallpaperPreset("wp_lake", R.string.wallpaper_preset_lake, R.drawable.wp_lake, R.drawable.wp_lake_thumb),
        WallpaperPreset("wp_blue_wave", R.string.wallpaper_preset_blue_wave, R.drawable.wp_blue_wave, R.drawable.wp_blue_wave_thumb),
        WallpaperPreset("wp_red", R.string.wallpaper_preset_red, R.drawable.wp_red, R.drawable.wp_red_thumb),
        WallpaperPreset("wp_sunset", R.string.wallpaper_preset_sunset, R.drawable.wp_sunset, R.drawable.wp_sunset_thumb),
        WallpaperPreset("wp_silk", R.string.wallpaper_preset_silk, R.drawable.wp_silk, R.drawable.wp_silk_thumb),
        WallpaperPreset("wp_night_street", R.string.wallpaper_preset_night_street, R.drawable.wp_night_street, R.drawable.wp_night_street_thumb)
    )

    /** Hình nền có id [id]; null nếu id lạ (bản cũ đã bỏ ảnh đó) hoặc null. */
    fun of(id: String?): WallpaperPreset? = id?.let { wanted -> list.firstOrNull { it.id == wanted } }

    /** Vị trí của [id] trong [list], -1 nếu không có. */
    fun indexOf(id: String?): Int = list.indexOfFirst { it.id == id }
}
