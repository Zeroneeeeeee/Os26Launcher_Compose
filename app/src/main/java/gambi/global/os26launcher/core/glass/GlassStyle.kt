package gambi.global.os26launcher.core.glass

import android.content.Context
import androidx.annotation.ColorInt
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.GlassLook

/**
 * Màu của một bề mặt kính tại chỗ: [tint] phủ lên ảnh nền đã mờ, [border] là viền sáng mảnh,
 * [flat] là màu dự phòng khi chưa có ảnh nền (cùng màu nền phẳng cũ của phần tử đó).
 */
data class GlassStyle(
    @ColorInt val tint: Int,
    @ColorInt val border: Int,
    @ColorInt val flat: Int
) {
    /** GT: style sau khi áp độ bóng của [look] lên tint và viền ([flat] giữ nguyên: nó chỉ hiện khi chưa có ảnh nền). */
    fun tuned(look: GlassLook): GlassStyle =
        if (look.gloss == GlassLook.DEFAULT.gloss) this
        else copy(tint = GlassTuning.tint(look, tint), border = GlassTuning.border(look, border))

    companion object {
        /**
         * Panel nằm dưới lớp tối `search_scrim` 80% và mang chữ trắng nên tint TỐI (chủ dự án 2026-11-03: giữ lớp tối,
         * kính tint tối). Các vai trò chưa có style riêng (Task 3-4) tạm dùng style ô.
         */
        fun forRole(context: Context, role: GlassRole): GlassStyle = when (role) {
            // FP Task 3 (2026-11-18): khung thư mục kiểu iOS 26 = kính SÁNG, rất trong, viền sáng mảnh (ảnh mẫu chủ dự án gửi);
            // tách khỏi GROUP_PANEL để panel nhóm Library giữ màu cũ.
            GlassRole.FOLDER_PANEL -> GlassStyle(
                tint = context.getColor(R.color.folder_panel_glass_tint),
                border = context.getColor(R.color.folder_panel_glass_border),
                flat = context.getColor(R.color.folder_panel_flat_color)
            )
            GlassRole.GROUP_PANEL -> GlassStyle(
                tint = context.getColor(R.color.gl_panel_tint),
                border = context.getColor(R.color.gl_panel_border),
                flat = context.getColor(R.color.folder_panel_background)
            )
            // SM: menu nhấn giữ icon, màu theo sáng/tối hệ thống (values / values-night).
            GlassRole.ICON_MENU -> GlassStyle(
                tint = context.getColor(R.color.shortcut_menu_glass_tint),
                border = context.getColor(R.color.shortcut_menu_glass_border),
                flat = context.getColor(R.color.shortcut_menu_flat)
            )

            GlassRole.SEARCH_BAR -> bar(context, R.color.search_field_bg)
            GlassRole.LIBRARY_SEARCH_BAR -> bar(context, R.color.folder_background)
            GlassRole.SEARCH_PILL -> bar(context, R.color.search_pill_bg)
            // GT: hai style trước đây dựng tại chỗ trong IconCellView.
            GlassRole.ICON_PLATE -> GlassStyle(
                tint = context.getColor(R.color.icon_glass_tint),
                border = context.getColor(R.color.icon_glass_border),
                flat = context.getColor(R.color.icon_glass_flat)
            )
            GlassRole.WIDGET -> GlassStyle(
                tint = context.getColor(R.color.widget_glass_tint),
                border = context.getColor(R.color.widget_glass_border),
                flat = context.getColor(R.color.widget_glass_flat)
            )
            else -> GlassStyle(
                tint = context.getColor(R.color.gl_cell_tint),
                border = context.getColor(R.color.gl_cell_border),
                flat = context.getColor(R.color.folder_background)
            )
        }

        /** Thanh tìm kiếm và pill: chữ/biểu tượng trắng hoặc xám nhạt nên tint tối hơn ô (điểm xuất phát, chỉnh theo mắt). */
        private fun bar(context: Context, flatColor: Int) = GlassStyle(
            tint = context.getColor(R.color.gl_bar_tint),
            border = context.getColor(R.color.gl_bar_border),
            flat = context.getColor(flatColor)
        )
    }
}
