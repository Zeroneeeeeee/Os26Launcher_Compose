package gambi.global.os26launcher.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** BG: một app trong một hồ sơ người dùng (huy hiệu tính theo gói, không theo activity). */
data class BadgeTarget(val packageName: String, val userSerial: Long) {
    /** Chuỗi lưu trong `HomeScreenConfig.badgeDisabledApps`. */
    val storageKey: String get() = "$packageName#$userSerial"
}

/**
 * BG: số thông báo đang có của từng app, do `ControlCenterService` (NotificationListenerService) đếm. Chỉ giữ trong bộ nhớ.
 * Map rỗng khi chưa có quyền "Truy cập thông báo" hoặc listener chưa nối. App không có trong map = 0.
 */
interface NotificationBadgeRepository {
    val counts: StateFlow<Map<BadgeTarget, Int>>
}
