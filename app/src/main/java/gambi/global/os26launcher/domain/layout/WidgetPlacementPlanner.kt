package gambi.global.os26launcher.domain.layout

/**
 * G2c Task 2 — tìm chỗ cho widget mới khi người dùng THÊM từ bảng chọn (không phải kéo): hình chữ nhật trống đầu tiên, KHÔNG bao
 * giờ đẩy icon (chủ dự án xác nhận 2026-11-05, Q5). Hàm THUẦN, không `android.*`.
 */
object WidgetPlacementPlanner {

    /** Chỗ tìm được: góc trên-trái ([x], [y]) của trang [pageIndex] (trong danh sách truyền vào). */
    data class FitSpot(val pageIndex: Int, val x: Int, val y: Int)

    /**
     * Quét các trang từ [startPageIndex] tới hết, rồi vòng về các trang trước nó; trong trang quét theo thứ tự đọc. Ô bị icon HOẶC
     * widget phủ (kể cả span) là ô bận. Không có chỗ nào thì trả null: người gọi tạo trang mới ở cuối và đặt tại (0,0).
     */
    fun firstFit(
        pages: List<GridInsertPlanner.PageSlots>,
        spanX: Int,
        spanY: Int,
        startPageIndex: Int,
        columns: Int,
        rows: Int
    ): FitSpot? {
        if (spanX < 1 || spanY < 1 || spanX > columns || spanY > rows || pages.isEmpty()) return null
        val start = startPageIndex.coerceIn(0, pages.size - 1)
        val order = (start until pages.size) + (0 until start)
        for (pageIndex in order) {
            val taken = BooleanArray(columns * rows)
            for (s in pages[pageIndex].slots) {
                for (dy in 0 until s.spanY) for (dx in 0 until s.spanX) {
                    val cx = s.x + dx
                    val cy = s.y + dy
                    if (cx in 0 until columns && cy in 0 until rows) taken[cy * columns + cx] = true
                }
            }
            for (y in 0..rows - spanY) for (x in 0..columns - spanX) {
                var ok = true
                loop@ for (dy in 0 until spanY) for (dx in 0 until spanX) {
                    if (taken[(y + dy) * columns + x + dx]) {
                        ok = false
                        break@loop
                    }
                }
                if (ok) return FitSpot(pageIndex, x, y)
            }
        }
        return null
    }
}
