package gambi.global.os26launcher.domain.model.controlcenter

/** G3d: kết quả một lệnh của Trung tâm điều khiển. ui đổi [SystemPanel] thành Intent (domain không biết Intent). */
sealed interface ControlResult {
    /** Đã làm xong tại chỗ, trạng thái sẽ phát lại qua `observe()`. */
    data object Done : ControlResult

    /** Android không cho app tự làm: đóng panel rồi mở màn hệ thống này (D5-D7). */
    data class OpenSystemPanel(val panel: SystemPanel) : ControlResult

    /** Thiếu quyền: đóng panel rồi mở màn cài đặt Trung tâm điều khiển (D8). */
    data class NeedsPermission(val permission: ControlPermission) : ControlResult

    /** Máy không có phần cứng hoặc hệ thống từ chối. */
    data object Unavailable : ControlResult
}

/** G3d: màn hệ thống mà một ô mở ra (RE mục 2.6 và 5.3). */
enum class SystemPanel {
    WIFI,
    INTERNET,
    AIRPLANE,
    BLUETOOTH_ENABLE,
    BLUETOOTH_DISABLE,
    BLUETOOTH_SETTINGS,
    TETHER,
    TIMER,
    CALCULATOR,
    CAMERA,
    BATTERY_SAVER,
    DARK_MODE,
    SETTINGS,
    VOICE_MEMO,
    NOTES,
    SOUND_OUTPUT,
    DND_SETTINGS,
    SCREEN_MIRROR // TF
}
