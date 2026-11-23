package gambi.global.os26launcher.domain.model

/**
 * Sự kiện package có kiểu, lấy từ `LauncherApps.Callback`. Khác với việc chỉ biết "danh sách app đổi":
 * ở đây phân biệt được GỠ THẬT ([Type.REMOVED]) với TẠM KHÔNG KHẢ DỤNG ([Type.UNAVAILABLE], [Type.SUSPENDED],
 * và `onPackageChanged` khi bật/tắt app). Chỉ [Type.REMOVED] (và [Type.USER_REMOVED] khi cả hồ sơ bị bỏ) mới được xóa hàng khỏi bố cục (room-schema mục 7).
 */
data class PackageEvent(val type: Type, val packageNames: List<String>, val userSerial: Long) {
    enum class Type {
        ADDED, REMOVED, CHANGED, AVAILABLE, UNAVAILABLE, SUSPENDED, UNSUSPENDED,

        /** C10: hồ sơ (user profile) [userSerial] đã bị bỏ: mọi hàng của user đó phải đi, không phụ thuộc danh sách package ([packageNames] rỗng). */
        USER_REMOVED
    }
}
