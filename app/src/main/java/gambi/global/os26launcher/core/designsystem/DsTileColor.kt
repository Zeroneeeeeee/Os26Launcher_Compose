package gambi.global.os26launcher.core.designsystem

import androidx.annotation.ColorRes
import gambi.global.os26launcher.R

/**
 * DS3 Task 3: 10 màu ô icon v3 ([tint] = nền ô `v2/Tile/Fill/<màu>`, [glyph] = màu nét `Core/Icon Glyph/<màu>`).
 *
 * Ánh xạ mục trang chủ Cài đặt của Figma v3 → màu (dùng cho plan Home cài đặt):
 * - Màn hình chính mặc định [BLUE] · Chung [SLATE] · Ngôn ngữ [SKY] · Thời tiết [AMBER] · Vị trí [GREEN]
 * - Liquid Glass [VIOLET] · Hình nền [PINK] · Bộ biểu tượng [ORANGE] · Lướt màn hình [TEAL] · Thư viện ứng dụng [BLUE]
 * - Cài đặt tìm kiếm [SLATE] · Huy hiệu thông báo [RED] · Hiệu ứng chuyển cảnh [VIOLET] · Hiệu ứng ứng dụng [AMBER]
 * - Ứng dụng ẩn [SLATE] · Thay đổi biểu tượng [PINK] · Thay đổi tên [ORANGE] · Đánh giá [AMBER]
 * - Tải ứng dụng khác [BLUE] · Chính sách bảo mật [GREEN]
 *
 * Thứ tự enum = giá trị của attr `dsTileColor` trong `ds_attrs.xml`; đổi thứ tự thì sửa cả hai.
 */
enum class DsTileColor(@ColorRes val tint: Int, @ColorRes val glyph: Int) {
    BLUE(R.color.ds_tile_blue, R.color.ds_glyph_blue),
    VIOLET(R.color.ds_tile_violet, R.color.ds_glyph_violet),
    SKY(R.color.ds_tile_sky, R.color.ds_glyph_sky),
    AMBER(R.color.ds_tile_amber, R.color.ds_glyph_amber),
    GREEN(R.color.ds_tile_green, R.color.ds_glyph_green),
    TEAL(R.color.ds_tile_teal, R.color.ds_glyph_teal),
    ORANGE(R.color.ds_tile_orange, R.color.ds_glyph_orange),
    PINK(R.color.ds_tile_pink, R.color.ds_glyph_pink),
    RED(R.color.ds_tile_red, R.color.ds_glyph_red),
    SLATE(R.color.ds_tile_slate, R.color.ds_glyph_slate)
}
