package gambi.global.os26launcher.domain.layout

/**
 * G2c Task 1 — hình học một trang lưới khi có item nhiều ô (widget). Hàm THUẦN, không `android.*`.
 *
 * Một [GridInsertPlanner.Slot] có `spanX > 1 || spanY > 1` là **widget**: vật cản cố định, phủ hình chữ nhật
 * `spanX x spanY` ô tính từ góc trên-trái `(x, y)`. Mọi slot còn lại là **icon** (1x1).
 *
 * **Dãy ô tự do S** = các ô của trang KHÔNG bị widget nào phủ, theo thứ tự đọc (`y` rồi `x`). Icon chỉ đứng trong S.
 * Mỗi ô được định danh bằng **chỉ số đọc** `y * columns + x`; S là mảng tăng dần các chỉ số đọc đó. Trang không có widget
 * thì S = `0 until columns*rows`, nên `seq` (vị trí trong S) trùng chỉ số đọc và mọi planner cho kết quả y hệt bản 1x1 cũ.
 */
internal object PageGeometry {

    fun isWidget(slot: GridInsertPlanner.Slot): Boolean = slot.spanX > 1 || slot.spanY > 1

    /** Chỉ số đọc các ô tự do của trang ([slots] chỉ cần có widget; icon bị bỏ qua), tăng dần. */
    fun freeIndices(slots: List<GridInsertPlanner.Slot>, columns: Int, rows: Int): IntArray {
        val covered = BooleanArray(columns * rows)
        for (s in slots) {
            if (!isWidget(s)) continue
            for (dy in 0 until s.spanY) for (dx in 0 until s.spanX) {
                val cx = s.x + dx
                val cy = s.y + dy
                if (cx in 0 until columns && cy in 0 until rows) covered[cy * columns + cx] = true
            }
        }
        var n = 0
        for (c in covered) if (!c) n++
        val out = IntArray(n)
        var k = 0
        for (i in covered.indices) if (!covered[i]) out[k++] = i
        return out
    }

    /** Vị trí (seq) của chỉ số đọc [readIndex] trong [free], hoặc -1 nếu ô đó bị phủ / ngoài lưới. */
    fun seqOf(free: IntArray, readIndex: Int): Int {
        val i = java.util.Arrays.binarySearch(free, readIndex)
        return if (i >= 0) i else -1
    }

    /** Vị trí trong [free] của ô đầu tiên có chỉ số đọc >= [readIndex]; `free.size` nếu không có. */
    fun lowerBound(free: IntArray, readIndex: Int): Int {
        val i = java.util.Arrays.binarySearch(free, readIndex)
        return if (i >= 0) i else -i - 1
    }

    /**
     * Ánh xạ ô đích của một lần chèn icon về ô tự do: ô tự do thì giữ nguyên; ô bị widget phủ thì lấy ô tự do đầu tiên có chỉ số
     * đọc LỚN HƠN (không có thì ô tự do cuối). Trả vị trí trong [free], hoặc -1 nếu trang không còn ô tự do.
     */
    fun resolveInsertSeq(free: IntArray, targetReadIndex: Int): Int {
        if (free.isEmpty()) return -1
        val exact = seqOf(free, targetReadIndex)
        if (exact >= 0) return exact
        val after = lowerBound(free, targetReadIndex)
        return if (after < free.size) after else free.size - 1
    }
}
