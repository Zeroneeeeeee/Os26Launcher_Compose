package gambi.global.os26launcher.domain.controlcenter

import android.graphics.Bitmap
import gambi.global.os26launcher.domain.model.controlcenter.ControlCenterConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** G3d: cài đặt Trung tâm điều khiển (RE mục 5.4). Lưu ở DataStore riêng `control_center`. */
interface ControlCenterPrefsRepository {
    val config: Flow<ControlCenterConfig>

    /** Đổi nguyên khối trong một lần ghi (DataStore tuần tự hóa các lần ghi nên không mất cập nhật). */
    suspend fun update(transform: (ControlCenterConfig) -> ControlCenterConfig)

    /** Đường tắt cho công tắc bật (D11, mặc định tắt). */
    val enabled: Flow<Boolean> get() = config.map { it.enabled }.distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }
}

/**
 * G3d: ảnh nền tự chọn của panel (RE mục 5.4, `state_wall` = 4). Bitmap là ngoại lệ có chủ ý của luật domain, như
 * `BackdropRepository`.
 */
interface ControlCenterBackgroundStore {
    /** Chép ảnh từ [uri] (chuỗi content://) vào bộ nhớ riêng, thu nhỏ. false nếu không đọc được. */
    suspend fun importImage(uri: String): Boolean

    /** Ảnh đã chép, thu nhỏ vừa [reqW]×[reqH]; null nếu chưa có. */
    suspend fun load(reqW: Int, reqH: Int): Bitmap?

    /** Dấu phiên bản (đổi khi người dùng chọn ảnh mới); 0 nếu chưa có ảnh. */
    fun version(): Long
}
