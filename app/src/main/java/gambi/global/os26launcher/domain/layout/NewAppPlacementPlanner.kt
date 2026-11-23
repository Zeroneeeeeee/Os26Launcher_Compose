package gambi.global.os26launcher.domain.layout

/**
 * FX3 (C20) — chỗ đặt cho app MỚI cài (và app được bỏ ẩn): **sau icon cuối cùng** của trang cuối, không lấp ô trống giữa trang.
 * Hàm THUẦN, không `android.*`, không DB. Chủ dự án chốt 2026-11-11; thay quy tắc "ô trống đầu tiên" (Q7 trong `room-schema.md`,
 * kiểu Launcher3). Gỡ app vẫn dồn ô ([GridRemovePlanner], `compactPageAfterRemoval`) nên không nằm ở đây.
 *
 * **Quy tắc:**
 * - Chỉ xét **trang cuối** (phần tử cuối của `pages`, người gọi đã sắp theo `rank`). Trang cuối trống vẫn là trang cuối.
 * - "Icon cuối cùng" = chỉ số đọc (`y * columns + x`) lớn nhất của item KHÔNG phải widget và nằm TRONG lưới; thư mục tính như icon
 *   (con của thư mục là hàng riêng, không nằm trên trang). Widget không tính là icon nhưng vẫn chiếm ô: app nhảy qua ô bị widget phủ
 *   ([PageGeometry.freeIndices]). Item ngoài lưới (dữ liệu lệch, `ensureFitsGrid` chữa lúc khởi động) bị bỏ qua.
 * - App thứ i của lô lấy ô tự do thứ i có chỉ số đọc LỚN HƠN icon cuối cùng. Hết ô thì sang trang MỚI (mọi ô tự do, từ ô đầu), cứ thế.
 * - Trang cuối còn khe giữa trang nhưng không còn ô sau icon cuối: vẫn tạo trang mới (không lấp khe).
 * - `pages` rỗng: coi như một trang trống mới.
 */
object NewAppPlacementPlanner {

    /** [pageIndex] = chỉ số trong `pages`; `pages.size + k` là trang mới thứ k (từ 0). */
    data class Spot(val pageIndex: Int, val x: Int, val y: Int)

    /** Trả đúng [count] [Spot], theo thứ tự của lô. */
    fun plan(pages: List<GridInsertPlanner.PageSlots>, count: Int, columns: Int, rows: Int): List<Spot> {
        require(columns > 0 && rows > 0) { "lưới $columns x $rows không hợp lệ" }
        if (count <= 0) return emptyList()

        var pageIndex: Int
        var candidates: IntArray
        val last = pages.lastOrNull()
        if (last == null) {
            pageIndex = 0
            candidates = IntArray(columns * rows) { it }
        } else {
            pageIndex = pages.lastIndex
            val lastIconIndex = last.slots
                .filter { !PageGeometry.isWidget(it) && it.x in 0 until columns && it.y in 0 until rows }
                .maxOfOrNull { it.y * columns + it.x } ?: -1
            candidates = PageGeometry.freeIndices(last.slots, columns, rows).filter { it > lastIconIndex }.toIntArray()
        }

        val spots = ArrayList<Spot>(count)
        var next = 0
        while (spots.size < count) {
            if (next < candidates.size) {
                val index = candidates[next++]
                spots.add(Spot(pageIndex, index % columns, index / columns))
            } else {
                pageIndex++
                candidates = IntArray(columns * rows) { it }
                next = 0
            }
        }
        return spots
    }
}
