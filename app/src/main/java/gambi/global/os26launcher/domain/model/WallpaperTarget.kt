package gambi.global.os26launcher.domain.model

/**
 * Nơi áp dụng ảnh nền làm wallpaper hệ thống. Tách riêng khỏi `WallpaperApplier.Target` (tầng data)
 * để `domain` và `ui` không phải import `data` (luật phụ thuộc, project_structure.md mục 2).
 * Chữ hiển thị nằm ở `strings.xml`, không nằm ở đây.
 */
enum class WallpaperTarget { HOME, LOCK, BOTH }
