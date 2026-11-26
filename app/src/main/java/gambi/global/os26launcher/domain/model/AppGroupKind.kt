package gambi.global.os26launcher.domain.model

/**
 * AG (2026-11-26) — nhóm app có thể gom thành thư mục từ Cài đặt → Chung. Thứ tự khai báo là thứ tự hàng trong Cài đặt. Chữ hiển thị (tên
 * thư mục) ở `strings.xml`; luật nhận diện ở `AppGroupPlanner`.
 *
 * Mở lại 2026-11-26 (QĐ 14): thứ tự này cũng là thứ tự thư mục trên trang 1 của bố cục lần đầu, và ở seed app khớp nhiều nhóm vào nhóm
 * đứng trước (Tiện ích, Google trước theo chủ dự án).
 */
enum class AppGroupKind { UTILITIES, GOOGLE, SOCIAL, GAMES, ENTERTAINMENT, MANUFACTURER }
