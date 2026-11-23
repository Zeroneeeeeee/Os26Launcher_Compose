package gambi.global.os26launcher.ui.settings

import gambi.global.os26launcher.domain.config.ThemeId

/**
 * HC Task 1 — nội dung vòng tròn bên phải Hero Card trạng thái "Đã mặc định" (ghi chú Figma 464:2648 mục 7): theme có số trong tên thì
 * hiện số (Liquid Glass 27 → "27", Classic 26 → "26"); theme không có số (Minimal, Dark Pro) → null = ảnh xem trước thu nhỏ trong vòng
 * tròn. Chưa chọn theme: như Liquid Glass 27 (lựa chọn mặc định của onboarding 0.2).
 */
object ThemeBadge {
    fun numberOf(theme: ThemeId?): String? = when (theme ?: ThemeId.LIQUID_GLASS_27) {
        ThemeId.LIQUID_GLASS_27 -> "27"
        ThemeId.CLASSIC_26 -> "26"
        ThemeId.MINIMAL, ThemeId.DARK_PRO -> null
    }
}
