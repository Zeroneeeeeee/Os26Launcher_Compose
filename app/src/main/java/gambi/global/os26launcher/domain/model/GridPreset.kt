package gambi.global.os26launcher.domain.model

/**
 * M8a Task 1 — cỡ lưới màn hình chính (cột × hàng) người dùng chọn trong Cài đặt. Dock không đổi (4 ô).
 *
 * **Bất biến bao hàm (Global Constraints của plan M8a):** các preset phải sắp được thành chuỗi mà preset
 * sau chứa trọn preset trước (cột và hàng đều không nhỏ hơn). `LayoutReconciler.changeGridPreset` dựa vào đó để
 * chọn thứ tự ghi an toàn giữa DataStore và DB. Thêm preset phá bao hàm (ví dụ 3×8) phải thiết kế lại.
 */
enum class GridPreset(val columns: Int, val rows: Int) {
    P4X5(4, 5),
    P4X6(4, 6),
    P5X6(5, 6);

    /** Số ô tối đa của một trang. */
    val capacity: Int get() = columns * rows

    companion object {
        val DEFAULT = P4X6

        /** Tên lạ (bản cũ đổi tên) hoặc null → [DEFAULT], không crash Flow settings. */
        fun fromName(name: String?): GridPreset = entries.find { it.name == name } ?: DEFAULT
    }
}
