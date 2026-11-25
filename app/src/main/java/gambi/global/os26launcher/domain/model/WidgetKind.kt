package gambi.global.os26launcher.domain.model

/** Kích thước một widget tính theo ô lưới. */
data class WidgetSize(val spanX: Int, val spanY: Int)

/**
 * G2c — loại widget tự vẽ. [id] là chuỗi ổn định lưu ở cột `className` của hàng `launcher_items` có `itemType = 4`: đã ghi xuống
 * DB thì KHÔNG đổi (tên enum thì đổi được khi refactor, cùng lý do `itemType` là Int, `room-schema.md` mục 3).
 *
 * Mọi cỡ trong [sizes] phải <= 4x2 để vừa cả ba preset lưới (nhỏ nhất 4x5, `GridPreset`): đổi cỡ lưới không bao giờ làm một widget
 * không còn vừa.
 */
enum class WidgetKind(val id: String, val sizes: List<WidgetSize>) {
    CLOCK("clock", listOf(WidgetSize(2, 2), WidgetSize(4, 2))),
    CALENDAR("calendar", listOf(WidgetSize(2, 2), WidgetSize(4, 2))),

    /** G3e: thời tiết (OpenWeatherMap). Cùng `itemType = 4`, không đổi schema; `className = "weather"`. */
    WEATHER("weather", listOf(WidgetSize(2, 2), WidgetSize(4, 2))),

    /** G3b: widget Ảnh (một ảnh hoặc bộ ảnh đổi mỗi giờ). Cùng `itemType = 4`, không đổi schema; `className = "photo"`. */
    PHOTO("photo", listOf(WidgetSize(2, 2), WidgetSize(4, 2))),

    /**
     * WF Task 1 (chủ dự án 2026-11-25): "App gần đây" trên lưới Home (trước chỉ có ở Today). Cùng `itemType = 4`, không đổi schema;
     * `className = "recents"` (trùng `TodayWidget.RECENTS`). Vẽ bằng Compose (`HomeRecentsWidget`), không qua `WidgetRenderer`.
     */
    RECENTS("recents", listOf(WidgetSize(2, 2), WidgetSize(4, 2)));

    /** Cỡ ([spanX] x [spanY]) có được phép với loại này không (hàng DB ngoài danh sách bị bỏ qua khi đọc). */
    fun allows(spanX: Int, spanY: Int): Boolean =
        sizes.any { it.spanX == spanX && it.spanY == spanY }

    companion object {
        /** Mã lạ (bản mới hơn, dữ liệu lỗi) -> null. */
        fun fromId(id: String?): WidgetKind? = entries.find { it.id == id }
    }
}
