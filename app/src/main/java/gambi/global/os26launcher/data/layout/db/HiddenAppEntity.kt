package gambi.global.os26launcher.data.layout.db

/**
 * Một app bị ẩn (khóa component). CF: từ Room v2 không còn là bảng riêng; đây là hình dạng kết quả truy vấn của [HiddenAppDao] trên bảng
 * `app_overrides` (hàng có `hidden = 1`). Giữ tên lớp để `LayoutReconciler` không phải sửa.
 */
data class HiddenAppEntity(
    val packageName: String,
    val className: String,
    val userSerial: Long
)
