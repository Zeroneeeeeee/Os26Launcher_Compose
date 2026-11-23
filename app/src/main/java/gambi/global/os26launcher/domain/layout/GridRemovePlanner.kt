package gambi.global.os26launcher.domain.layout

/**
 * Thuật toán "ẩn 1 icon thì các icon sau nó dồn lên" của 1 trang lưới (M7 Task 8, Ruling G2) — hàm THUẦN, không
 * `android.*`, không DB. Dùng chung cho đường ghi thật (`LayoutReconciler.setHidden`) và cập nhật lạc quan ở UI
 * (`HomeViewModel`), cùng nguyên tắc với [GridInsertPlanner]. Đối xứng với chèn: ẩn rồi chèn lại đúng ô cũ về nguyên trạng.
 *
 * Quy tắc: mọi item đứng SAU item bị ẩn theo thứ tự đọc (`y` rồi `x`) dịch về trước đúng 1 bậc (chỉ số đọc - 1); item
 * đứng trước giữ nguyên; khe trống đang có giữ nguyên (chỉ dịch theo); KHÔNG kéo item của trang kế sang.
 *
 * **G2c — widget:** "1 bậc" tính trên dãy ô tự do S của trang ([PageGeometry]): icon lùi 1 vị trí trong S nên bước qua ô bị
 * widget phủ. Widget không bao giờ dịch; gỡ chính một widget không dồn gì (ô trống để lại, giống [GridInsertPlanner] khi kéo đi).
 * Trang không có widget: y hệt trước G2c.
 */
object GridRemovePlanner {

    data class Move(val itemId: Long, val x: Int, val y: Int)

    /**
     * [slots] = mọi item của trang (kể cả [removedItemId]). Trả chỉ các item THỰC SỰ đổi chỗ; rỗng nếu item không có trong
     * trang hoặc là widget. [rows] cần để biết dãy ô tự do khi trang có widget.
     */
    fun plan(
        slots: List<GridInsertPlanner.Slot>,
        removedItemId: Long,
        columns: Int,
        rows: Int
    ): List<Move> {
        val removed = slots.firstOrNull { it.itemId == removedItemId } ?: return emptyList()
        if (PageGeometry.isWidget(removed)) return emptyList()
        val removedIndex = removed.y * columns + removed.x
        val free = PageGeometry.freeIndices(slots, columns, rows)
        return slots
            .filter { it.itemId != removedItemId && !PageGeometry.isWidget(it) && it.y * columns + it.x > removedIndex }
            .mapNotNull {
                val seq = PageGeometry.seqOf(free, it.y * columns + it.x)
                if (seq < 1) {
                    null // ô ngoài lưới / bị phủ (dữ liệu lỗi): không dồn
                } else {
                    val index = free[seq - 1]
                    Move(it.itemId, index % columns, index / columns)
                }
            }
    }
}
