package gambi.global.os26launcher.domain.repository

import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.RecentApp
import kotlinx.coroutines.flow.Flow

/** M8d Task 5 — lịch sử mở app của chính launcher (nguồn của "Gần đây" và "Gợi ý"). Chỉ lưu ở máy. */
interface RecentAppsRepository {
    /** Tối đa [MAX_ENTRIES] mục, chưa lọc app đã gỡ/ẩn (ViewModel giao với danh sách app hiện có). */
    val entries: Flow<List<RecentApp>>

    /** Ghi một lần mở app thành công. Không chặn người gọi. */
    fun record(key: ComponentKey)

    suspend fun clear()

    /** P3-7: xóa hẳn một app khỏi lịch sử (khi ẩn app: không để lại dấu vết trong file lưu). */
    suspend fun remove(key: ComponentKey)

    companion object {
        const val MAX_ENTRIES = 30
    }
}
