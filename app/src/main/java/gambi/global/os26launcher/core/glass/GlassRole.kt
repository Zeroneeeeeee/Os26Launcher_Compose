package gambi.global.os26launcher.core.glass

import gambi.global.os26launcher.domain.model.GlassElement

/**
 * Các phần tử được áp kính. [element] = công tắc "Tùy chỉnh" điều khiển nó (GS); null = không có tùy chọn (ô thư mục, ô nhóm:
 * kính thật ở đó cần view riêng, phá rung/ảnh kéo/kéo-thả).
 */
enum class GlassRole(val element: GlassElement?) {
    FOLDER_CELL(null),
    FOLDER_PANEL(GlassElement.PANELS),
    GROUP_TILE(null),
    GROUP_PANEL(GlassElement.PANELS),
    SEARCH_BAR(GlassElement.SEARCH_BAR),
    LIBRARY_SEARCH_BAR(GlassElement.SEARCH_BAR),
    SEARCH_PILL(GlassElement.PILL),

    /** GS: dock, trước đây chỉ do `GlassSurfaceFactory` quyết định; nay đi qua [GlassPolicy]. */
    DOCK(GlassElement.DOCK),

    /**
     * SM: menu nhấn giữ icon, luật như hai panel (kính thật trên LENS + máy đủ khả năng, kính mờ tĩnh nơi khác; một khung ~24 ms mỗi
     * lần mở, GL Task 7, chủ dự án chấp nhận 2026-11-09 vì là tùy chọn). Ở Tùy chỉnh có công tắc riêng, cùng nghĩa bốn công tắc kia.
     */
    ICON_MENU(GlassElement.ICON_MENU),

    /** GT: tấm kính sau icon app (kiểu icon Kính) và nền widget. Luôn kính mờ tại chỗ; có vai trò để lấy màu qua [GlassStyle.forRole] như mọi phần tử. */
    ICON_PLATE(null),
    WIDGET(null)
}
