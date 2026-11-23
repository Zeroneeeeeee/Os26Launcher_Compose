package gambi.global.os26launcher.domain.repository

import android.graphics.Bitmap
import gambi.global.os26launcher.domain.model.ComponentKey
import kotlinx.coroutines.flow.Flow

/**
 * P3-4 (CI) — icon riêng của từng app (màn "Thay đổi biểu tượng", 5.2). Icon riêng KHÔNG bị áp kiểu icon / gói icon (chủ dự án
 * 2026-11-20, Q6): chỉ cắt theo hình dạng icon. `Bitmap` là ngoại lệ có chủ ý của luật "domain không import android.*" (như
 * [BackdropRepository]).
 */
interface CustomIconRepository {

    /** Các app đang có icon riêng (hàng "Đã đổi"). */
    val customized: Flow<Set<ComponentKey>>

    /** Lưu [bitmap] (vuông) làm icon riêng của [key]; thay icon riêng cũ nếu có. Trả false nếu lỗi ghi. */
    suspend fun setIcon(key: ComponentKey, bitmap: Bitmap): Boolean

    /** Về icon hệ thống. */
    suspend fun clearIcon(key: ComponentKey)
}
