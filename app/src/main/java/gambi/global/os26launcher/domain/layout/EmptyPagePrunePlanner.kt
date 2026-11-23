package gambi.global.os26launcher.domain.layout

/**
 * EP — chọn các trang màn hình chính cần xóa vì không còn item nào. Hàm THUẦN, không `android.*`, không DB.
 *
 * "Trang trống" (D1) = không có hàng nào ở lưới chính trỏ tới trang đó; icon, thư mục, widget đều tính là có item, kể cả hàng
 * của app đang không khả dụng (bị tắt, thẻ nhớ tháo) để bật lại thì icon về đúng trang cũ (F1). Luôn giữ ≥ 1 trang (D2):
 * mọi trang đều trống thì giữ trang có `rank` nhỏ nhất. Trang ở giữa cũng bị xóa (Q2); `rank` các trang còn lại giữ nguyên.
 */
object EmptyPagePrunePlanner {

    data class Page(val id: Long, val rank: Int)

    /** [pages] = mọi trang; [usedPageIds] = các `pageId` đang có ít nhất 1 hàng ở lưới chính. Trả id trang cần xóa. */
    fun plan(pages: List<Page>, usedPageIds: Set<Long>): List<Long> {
        val sorted = pages.sortedBy { it.rank }
        val empty = sorted.filter { it.id !in usedPageIds }
        if (empty.isEmpty()) return emptyList()
        return if (empty.size == sorted.size) empty.drop(1).map { it.id } else empty.map { it.id }
    }
}
