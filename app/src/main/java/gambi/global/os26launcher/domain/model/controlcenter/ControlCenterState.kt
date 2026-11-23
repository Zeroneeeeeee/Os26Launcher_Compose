package gambi.global.os26launcher.domain.model.controlcenter

/**
 * G3d: trạng thái hệ thống mà panel hiển thị. Bất biến; `SystemControlsPort` phát bản mới mỗi khi hệ thống đổi.
 * [brightness], [volume]: 0..1. [batteryLevel]: 0..100, -1 = chưa biết. [carrier]: tên nhà mạng, rỗng nếu không có SIM.
 */
data class ControlCenterState(
    val wifiOn: Boolean = false,
    val bluetoothOn: Boolean = false,
    val bluetoothAvailable: Boolean = true,
    val airplaneOn: Boolean = false,
    val syncOn: Boolean = false,
    val torchOn: Boolean = false,
    val torchAvailable: Boolean = false,
    val rotationLocked: Boolean = false,
    val dndOn: Boolean = false,
    val brightness: Float = 0.5f,
    val autoBrightness: Boolean = false,
    val volume: Float = 0.5f,
    val screenTimeoutMs: Int = 30_000,
    val batteryLevel: Int = -1,
    val charging: Boolean = false,
    val powerSave: Boolean = false,
    val darkMode: Boolean = false,
    val carrier: String = "",
    val nightShiftOn: Boolean = false
)
