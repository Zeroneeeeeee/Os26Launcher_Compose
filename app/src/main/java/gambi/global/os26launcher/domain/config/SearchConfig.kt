package gambi.global.os26launcher.domain.config

/** CF — nhóm "Cài đặt tìm kiếm" (3.3), thay `SearchPreferences` (M8d). Bốn nguồn đầu mặc định bật. `docs/config-schema.md` mục 3.4. */
data class SearchConfig(
    val systemSettings: Boolean = true,
    val shortcuts: Boolean = true,
    /** P3-5: "Gợi ý tìm kiếm" = tìm kiếm thịnh hành ở ô trống. Tắt chỉ ẩn mục đó, không đụng lịch sử app, không đụng gợi ý web khi gõ. */
    val suggestions: Boolean = true,
    /** P3-5: "App gần đây" ở ô trống. Tắt thì ẩn cả mục (kể cả app hệ thống lấp chỗ) và ngừng ghi, KHÔNG xóa lịch sử đã lưu. */
    val recentApps: Boolean = true,
    /** G3a: tìm trong danh bạ (và hiện hàng mời xin quyền). Tắt không thu hồi quyền hệ thống, chỉ ngừng đọc. */
    val contacts: Boolean = true,
    /** Trạng thái, không phải lựa chọn: đã từng bật hộp thoại xin quyền Danh bạ (biết "từ chối vĩnh viễn"). */
    val contactsAsked: Boolean = false,
    val files: Boolean = false,
    val webSuggestions: Boolean = false,
    /** Package công cụ tìm kiếm web; null = mặc định hệ thống. */
    val webEngine: String? = null
)
