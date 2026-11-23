package gambi.global.os26launcher.domain.model.controlcenter

/**
 * G3d: quyền đặc biệt mà Trung tâm điều khiển cần. Người dùng tự cấp ở màn hệ thống (trừ [BLUETOOTH_CONNECT] là quyền runtime).
 * [required]: thiếu thì không hiện được dải mép (D11 của plan); còn lại chỉ làm vài ô không chạy.
 */
enum class ControlPermission(val required: Boolean) {
    /** Vẽ trên ứng dụng khác: dải mép và panel là cửa sổ `TYPE_APPLICATION_OVERLAY`. */
    OVERLAY(true),

    /** Truy cập thông báo: hệ thống bind service giữ process sống, và là chìa khóa đọc phiên media (ô nhạc). */
    NOTIFICATION_LISTENER(true),

    /** Bỏ tối ưu pin: khuyên dùng để ROM không giết service (D12). */
    BATTERY_OPTIMIZATION(false),

    /** Ghi cài đặt hệ thống: độ sáng, khóa xoay. */
    WRITE_SETTINGS(false),

    /** Quyền Không làm phiền. */
    DND_ACCESS(false),

    /** API 31+: 31-32 bật/tắt Bluetooth trực tiếp; 33+ mở hộp thoại bật Bluetooth của hệ thống. */
    BLUETOOTH_CONNECT(false)
}
