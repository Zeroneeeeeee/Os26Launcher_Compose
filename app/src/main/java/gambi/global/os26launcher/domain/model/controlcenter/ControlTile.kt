package gambi.global.os26launcher.domain.model.controlcenter

/**
 * G3d: các nút cố định của Trung tâm điều khiển (khối kết nối, trang mạng, khóa xoay, Không làm phiền). Ô tùy chỉnh dùng
 * [CustomTileType]; thanh trượt, ô thời gian chờ và độ sáng tự động có lệnh riêng ở `SystemControlsPort`.
 */
enum class ControlTile {
    AIRPLANE,
    CELLULAR,
    WIFI,
    BLUETOOTH,
    SYNC,
    HOTSPOT,
    ROTATION_LOCK,
    DND,
    FLASHLIGHT
}
