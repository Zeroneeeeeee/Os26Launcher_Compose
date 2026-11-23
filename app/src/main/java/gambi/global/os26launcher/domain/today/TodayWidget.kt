package gambi.global.os26launcher.domain.today

import gambi.global.os26launcher.domain.model.WidgetKind
import kotlinx.coroutines.flow.Flow

/**
 * TV-3 — một widget trên Today View. Không có hàng `launcher_items`: danh sách Today lưu riêng ([TodayWidgetsRepository]).
 *
 * [id] nằm trong dải riêng >= [ID_BASE] (2^40, xa mọi id tự tăng của Room) để các kho đánh theo `itemId` (địa điểm thời tiết, ảnh của widget Ảnh)
 * dùng được nguyên như widget trên Home mà không bao giờ trùng một hàng thật. [kindId] là `WidgetKind.id` hoặc [RECENTS] (thẻ app gần đây, chỉ có
 * trên Today). Mọi widget cao 2 ô; [wide] = 4 × 2, không thì 2 × 2.
 */
data class TodayWidget(val id: Long, val kindId: String, val wide: Boolean) {
    val spanX: Int get() = if (wide) 4 else 2
    val spanY: Int get() = 2
    val isRecents: Boolean get() = kindId == RECENTS

    /** Loại widget tự vẽ; null với thẻ app gần đây. */
    val builtin: WidgetKind? get() = WidgetKind.fromId(kindId)

    companion object {
        const val RECENTS = "recents"

        /** Đầu dải id của widget Today (xem kdoc lớp). */
        const val ID_BASE = 1L shl 40

        fun isTodayId(id: Long): Boolean = id >= ID_BASE

        /** Bộ mặc định khi chưa lưu gì (chủ dự án 2026-11-20): app gần đây 4 × 2, Thời tiết + Lịch 2 × 2, Đồng hồ số 4 × 2. */
        val DEFAULT = listOf(
            TodayWidget(ID_BASE + 1, RECENTS, wide = true),
            TodayWidget(ID_BASE + 2, WidgetKind.WEATHER.id, wide = false),
            TodayWidget(ID_BASE + 3, WidgetKind.CALENDAR.id, wide = false),
            TodayWidget(ID_BASE + 4, WidgetKind.CLOCK.id, wide = true)
        )

        /** Id cấp cho widget thêm mới bắt đầu từ đây (chừa chỗ cho bộ mặc định). */
        const val FIRST_FREE_ID = ID_BASE + 100

        /** Mã loại [kindId] có hợp lệ không (dữ liệu lạ của bản khác bị bỏ khi đọc). */
        fun isKnownKind(kindId: String): Boolean = kindId == RECENTS || WidgetKind.fromId(kindId) != null
    }
}

/** TV-3 — danh sách widget của Today View, theo thứ tự hiện. Chưa lưu gì thì phát [TodayWidget.DEFAULT]. */
interface TodayWidgetsRepository {
    val widgets: Flow<List<TodayWidget>>

    /** Thêm vào CUỐI danh sách. */
    suspend fun add(kindId: String, wide: Boolean)

    suspend fun remove(id: Long)

    /** Đặt lại thứ tự theo [ids]; id không có trong danh sách bị bỏ qua, widget không có trong [ids] giữ ở cuối theo thứ tự cũ. */
    suspend fun reorder(ids: List<Long>)

    /**
     * Bản Compose (2026-11-21) — chuyển dữ liệu Today cũ (hàng Room `container = TODAY` của bản Compose trước khi port kho này): ghi [kinds]
     * (theo thứ tự, `kindId` + rộng) làm danh sách Today NẾU kho chưa từng được ghi. Trả true nếu đã ghi; false nếu kho đã có dữ liệu (không đụng).
     */
    suspend fun importLegacy(kinds: List<Pair<String, Boolean>>): Boolean
}
