package gambi.global.os26launcher.domain.controlcenter

import gambi.global.os26launcher.domain.model.controlcenter.ControlCenterState
import gambi.global.os26launcher.domain.model.controlcenter.ControlResult
import gambi.global.os26launcher.domain.model.controlcenter.ControlTile
import kotlinx.coroutines.flow.Flow

/** G3d: đọc trạng thái hệ thống và gửi lệnh cho Trung tâm điều khiển. */
interface SystemControlsPort {
    /** Đọc trạng thái ngay (gọi mỗi lần bắt đầu kéo mở, như app mẫu). */
    fun snapshot(): ControlCenterState

    /** Phát trạng thái mỗi khi hệ thống đổi. Chỉ đăng ký receiver/observer khi có collector (panel đang mở). */
    fun observe(): Flow<ControlCenterState>

    fun toggle(tile: ControlTile): ControlResult

    /** [value] 0..1. Cần quyền ghi cài đặt hệ thống. */
    fun setBrightness(value: Float): ControlResult

    /** Bật/tắt độ sáng tự động (bảng độ sáng lớn, RE mục 5.2). Cần quyền ghi cài đặt hệ thống. */
    fun toggleAutoBrightness(): ControlResult

    /** [value] 0..1, luồng nhạc. */
    fun setVolume(value: Float): ControlResult

    /** Thời gian chờ tắt màn hình (RE mục 5.2). Cần quyền ghi cài đặt hệ thống. */
    fun setScreenTimeout(millis: Int): ControlResult

    companion object {
        /** Các mức của app mẫu (`uvd`): 15 giây tới 30 phút. */
        val SCREEN_TIMEOUTS_MS = listOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000, 1_800_000)
    }
}
