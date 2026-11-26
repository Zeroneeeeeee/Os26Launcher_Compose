package gambi.global.os26launcher.domain.model

/**
 * AG (2026-11-26) — nhóm app có thể gom thành thư mục từ Cài đặt → Chung. Thứ tự khai báo là thứ tự hàng trong Cài đặt. Chữ hiển thị (tên
 * thư mục) ở `strings.xml`; luật nhận diện ở `AppGroupPlanner`.
 */
enum class AppGroupKind { GOOGLE, SOCIAL, GAMES, ENTERTAINMENT, UTILITIES, MANUFACTURER }
