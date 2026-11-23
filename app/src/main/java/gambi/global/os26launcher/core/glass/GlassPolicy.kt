package gambi.global.os26launcher.core.glass

import gambi.global.os26launcher.domain.model.GlassCustom

/**
 * Hàm thuần: tier + vai trò (+ công tắc Tùy chỉnh) -> [GlassMode]. Không import `android.*` để review bằng đọc.
 *
 * Bảng chốt ở Task 6 của GL theo số đo Live 4 (2026-11-03) và Oppo O1: mọi vai trò là [GlassMode.STATIC_BLUR] trên mọi tier, không
 * phần tử nào phải về [GlassMode.FLAT] (xem [GlassBudget] cho số ô). Số ô tối đa mỗi trang/màn là ngân sách chứ không phải chế
 * độ. Riêng hai panel và menu nhấn giữ icon (SM) là [GlassMode.REAL] trên LENS (Task 7), và dock (GS) là [GlassMode.REAL] trừ tier CLASSIC_CPU.
 */
object GlassPolicy {
    /**
     * [realGlassCapable]: máy thật sự có đường Lens (`GlassTierResolver.isRealGlassCapable`). Tier một mình không đủ: `FORCE_HIGH`
     * cho tier LENS cả trên máy API < 33 (đường chụp CPU, đo trên Live 4: S1 Janky 30.7%), nên `REAL` cho panel cần CẢ HAI.
     *
     * GS — [custom]: khác null CHỈ khi mức kính là Tùy chỉnh. Khi đó phần tử có công tắc (role.element != null) lấy theo công tắc
     * (bật = thật, tắt = mờ) và KHÔNG hỏi tier hay [realGlassCapable] (khả năng chỉ dùng cho cảnh báo ở Cài đặt). Vai trò không có
     * công tắc (ô thư mục, ô nhóm) luôn theo bảng cũ. [custom] = null chạy đúng bảng hiện tại.
     */
    fun modeFor(
        tier: GlassTier,
        role: GlassRole,
        realGlassCapable: Boolean,
        custom: GlassCustom? = null
    ): GlassMode {
        val element = role.element
        if (custom != null && element != null) return if (custom[element]) GlassMode.REAL else GlassMode.STATIC_BLUR
        return when (role) {
            // Dock: đúng quy tắc của GlassSurfaceFactory trước GS (LENS và CLASSIC_GPU thật, CLASSIC_CPU mờ tĩnh).
            GlassRole.DOCK -> if (tier != GlassTier.CLASSIC_CPU) GlassMode.REAL else GlassMode.STATIC_BLUR
            // Hai panel: bề mặt lớn, đứng yên, mỗi lúc một cái -> kính thật trên LENS (Task 7). Các tier classic chỉ có 1 kính thật
            // (dock) nên panel dùng kính mờ tĩnh. SM: menu nhấn giữ icon đi cùng nhánh (mỗi lúc một cái, đứng yên sau khi mở).
            GlassRole.FOLDER_PANEL, GlassRole.GROUP_PANEL, GlassRole.ICON_MENU ->
                if (tier == GlassTier.LENS && realGlassCapable) GlassMode.REAL else GlassMode.STATIC_BLUR

            else -> GlassMode.STATIC_BLUR
        }
    }
}
