package gambi.global.os26launcher.domain.layout

/**
 * M8a Task 6 — xếp lại TOÀN BỘ icon lưới chính khi đổi cỡ lưới. Hàm THUẦN, không `android.*`, không DB; dùng chung kiểu dữ
 * liệu với [GridInsertPlanner].
 *
 * **Quy tắc (quyết định D1-D3 của M8a):** trải phẳng icon theo thứ tự đọc (chỉ số trang, rồi `y`, rồi `x`; hòa thì theo
 * `itemId` để kết quả xác định), nén hết khe trống; icon thứ `i` vào trang `i / (columns*rows)`, ô `i % (columns*rows)`
 * (cột = ô % columns, hàng = ô / columns). Một chiều, không có "hoàn tác" thông minh.
 *
 * Chấp nhận cả vị trí NGOÀI lưới (x >= columns hoặc y >= rows, ca DataStore lệch DB): chúng vẫn nằm đúng thứ tự đọc.
 *
 * **G2c — widget:** cùng thứ tự đọc, nhưng mỗi item (icon 1 ô hoặc widget hình chữ nhật) được đặt vào VỊ TRÍ ĐẦU TIÊN CÒN VỪA
 * của trang hiện tại, quét từ ô (0,0); hết chỗ thì sang trang kế. Icon đến sau một widget được phép lấp khe cạnh widget trong
 * CÙNG trang, không bao giờ lùi về trang trước. Span lớn hơn lưới bị kẹp (ghi trong [Plan.spanChanges] để người gọi ghi lại).
 * Không widget thì mỗi item rơi đúng ô kế tiếp: kết quả y hệt quy tắc cũ.
 */
object GridReflowPlanner {

    /** Kẹp span của một item cho vừa lưới (dữ liệu lỗi hoặc cỡ widget lớn hơn lưới sau này). */
    data class SpanChange(val itemId: Long, val spanX: Int, val spanY: Int)

    /**
     * [placements] chỉ gồm icon THỰC SỰ đổi chỗ. `pageIndex >= pages.size` nghĩa là trang mới (do người gọi tạo).
     * [totalPages] = số trang cần có sau khi xếp (>= 1): trang có chỉ số >= [totalPages] sẽ trống.
     */
    data class Plan(
        val placements: List<GridInsertPlanner.Placement>,
        val totalPages: Int,
        val spanChanges: List<SpanChange> = emptyList()
    )

    fun plan(pages: List<GridInsertPlanner.PageSlots>, columns: Int, rows: Int): Plan {
        require(columns > 0 && rows > 0) { "columns=$columns rows=$rows phải > 0" }
        val capacity = columns * rows

        // (pageIndex, slot) theo thứ tự đọc.
        val ordered = pages.flatMapIndexed { pageIndex, page ->
            page.slots.map { pageIndex to it }
        }.sortedWith(
            compareBy({ it.first }, { it.second.y }, { it.second.x }, { it.second.itemId })
        )

        val placements = ArrayList<GridInsertPlanner.Placement>()
        val spanChanges = ArrayList<SpanChange>()
        val occupied = ArrayList<BooleanArray>()
        occupied.add(BooleanArray(capacity))
        var current = 0
        for ((oldPage, slot) in ordered) {
            val sx = slot.spanX.coerceIn(1, columns)
            val sy = slot.spanY.coerceIn(1, rows)
            if (sx != slot.spanX || sy != slot.spanY) spanChanges.add(
                SpanChange(
                    slot.itemId,
                    sx,
                    sy
                )
            )
            var at = firstFit(occupied[current], sx, sy, columns, rows)
            while (at < 0) {
                current++
                if (current == occupied.size) occupied.add(BooleanArray(capacity))
                at = firstFit(occupied[current], sx, sy, columns, rows)
            }
            for (dy in 0 until sy) for (dx in 0 until sx) occupied[current][at + dy * columns + dx] =
                true
            val x = at % columns
            val y = at / columns
            if (current != oldPage || x != slot.x || y != slot.y) {
                placements.add(GridInsertPlanner.Placement(slot.itemId, current, x, y))
            }
        }
        return Plan(placements, occupied.size, spanChanges)
    }

    /** Chỉ số đọc của góc trên-trái hình chữ nhật [sx] x [sy] trống đầu tiên trong [taken], hoặc -1. */
    private fun firstFit(taken: BooleanArray, sx: Int, sy: Int, columns: Int, rows: Int): Int {
        for (y in 0..rows - sy) for (x in 0..columns - sx) {
            var ok = true
            loop@ for (dy in 0 until sy) for (dx in 0 until sx) {
                if (taken[(y + dy) * columns + x + dx]) {
                    ok = false
                    break@loop
                }
            }
            if (ok) return y * columns + x
        }
        return -1
    }
}
