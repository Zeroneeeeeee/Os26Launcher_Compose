package gambi.global.os26launcher.domain.layout

/**
 * Thuật toán chèn 1 item vào dock (M7 Task 6) — hàm THUẦN, không `android.*`, không DB. Dùng chung cho đường ghi
 * thật (`LayoutReconciler.moveItem`) và cập nhật lạc quan ở UI (`HomeViewModel`) để hai nơi không lệch nhau
 * (cùng nguyên tắc với [GridInsertPlanner]).
 *
 * Quy tắc: bỏ [movingId] khỏi dock nếu đang ở đó; nếu phần còn lại đã đủ [maxSlots] thì TỪ CHỐI (trả `null`);
 * ngược lại chèn tại [slot] (kẹp vào `0..size`). Kết quả là thứ tự id mới, `rank` = chỉ số. Sắp xếp lại trong
 * dock đầy vẫn được (item đang kéo không tính vào giới hạn).
 */
object DockInsertPlanner {

    fun plan(dockIds: List<Long>, movingId: Long, slot: Int, maxSlots: Int): List<Long>? {
        val rest = dockIds.filter { it != movingId }
        if (rest.size >= maxSlots) return null
        return rest.toMutableList().apply { add(slot.coerceIn(0, size), movingId) }
    }
}
