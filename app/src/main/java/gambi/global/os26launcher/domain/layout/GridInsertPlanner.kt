package gambi.global.os26launcher.domain.layout

/**
 * Thuật toán chèn 1 item vào 1 ô của 1 trang lưới (M7 Task 5 Step 5) — hàm THUẦN, không `android.*`, không DB.
 * Dùng chung cho đường ghi thật (`LayoutReconciler.moveItem`) và cập nhật lạc quan ở UI (`HomeViewModel`), để
 * hai nơi không bao giờ lệch nhau.
 *
 * **Quy tắc (chốt 2026-10-28 sau khảo sát iPhone XS Max, thay bản "đẩy occupant sang ô trống gần nhất"):**
 * - Item đang kéo ([movingItemId]) bị gỡ khỏi chỗ cũ (để lại 1 ô trống, KHÔNG dồn — Task 1 Ruling 2) rồi đặt vào ô đích.
 * - Ô đích trống: chỉ item đó đổi chỗ.
 * - Ô đích có item khác (occupant): occupant và MỌI item sau nó theo thứ tự đọc (`y` rồi `x`) được **xếp sát nhau** từ ô
 *   kế sau ô đích (occupant vào ô đích + 1, item sau nó vào ô đích + 2, ...). Khe trống nằm SAU ô đích vì vậy bị dồn mất;
 *   item TRƯỚC ô đích giữ nguyên (bảo toàn khe trống đang có). (G2c Task 1: KDoc cũ ghi "dịch đúng 1 bậc", nhưng code
 *   — và xem trước đẩy-dồn của `IconGridView.applyPreview` — luôn dồn sát; KDoc này đã sửa cho khớp code.)
 * - Phần xếp tràn ngoài các ô của trang (luôn đúng 1 item, vì 1 trang không bao giờ quá đầy trước khi chèn) được đẩy sang
 *   ô tự do đầu tiên của trang kế theo cùng quy tắc (lặp); hết trang thì cần tạo thêm trang ở cuối ([Plan.newPages]).
 * - Kéo về đúng ô đang đứng: không đổi gì ([Plan.placements] rỗng). Kéo về ô cũ đã trống: chỉ item đó đổi chỗ,
 *   các item từng bị dồn trước đó KHÔNG tự dồn lại (hành vi chốt, để lại khe trống).
 *
 * **G2c — item nhiều ô (widget):** xem [PageGeometry]. "Ô kế sau" và "thứ tự đọc" ở trên được hiểu trên dãy ô tự do S (ô
 * không bị widget phủ); widget không bao giờ bị dịch bởi chèn icon. Ô đích bị widget phủ được đổi thành ô tự do đầu tiên có
 * chỉ số đọc lớn hơn ([PageGeometry.resolveInsertSeq]). Trang không có widget thì S là mọi ô và kết quả y hệt trước G2c.
 *
 * **FX3 (B3) — dữ liệu lệch:** hàm này chạy trên luồng chính (`HomeViewModel`, `IconGridView`) nên không được ném lỗi vì dữ liệu
 * DB lệch. (1) Item có ô ngoài `0 until columns` x `0 until rows` (`ensureFitsGrid` chữa lúc khởi động, không phải ở đây) bị
 * BỎ QUA: không chiếm ô, không bị dịch, không được tính là occupant. (2) Phần xếp tràn có thể nhiều hơn 1 item (trang có hơn
 * `columns*rows` item vì hàng trùng ô): cả khối đi sang trang kế qua [insertBlock], như [planWidget].
 */
object GridInsertPlanner {

    /** [spanX]/[spanY] > 1 nghĩa là widget (G2c); mặc định 1x1 nên nơi gọi cũ không phải đổi. */
    data class Slot(
        val itemId: Long,
        val x: Int,
        val y: Int,
        val spanX: Int = 1,
        val spanY: Int = 1
    )

    /** Ảnh chụp nội dung 1 trang, theo thứ tự `rank` của trang trong danh sách truyền vào [plan]. */
    data class PageSlots(val pageId: Long, val slots: List<Slot>)

    /** Vị trí CUỐI của 1 item. [pageIndex] = chỉ số trong `pages`; `pages.size + k` là trang mới thứ k (từ 0). */
    data class Placement(val itemId: Long, val pageIndex: Int, val x: Int, val y: Int)

    /** [placements] chỉ gồm item THỰC SỰ đổi chỗ (so với ảnh chụp đầu vào). Item không có trong `pages` (kéo từ dock) luôn có mặt. */
    data class Plan(
        val placements: List<Placement>,
        val newPages: Int,
        /** G2c: true = từ chối (widget chồng widget khác hoặc lớn hơn lưới); [placements] rỗng, người gọi không ghi gì và đưa item về chỗ cũ. */
        val rejected: Boolean = false
    )

    /**
     * [movingSpanX]/[movingSpanY] > 1 hoặc slot của item đang kéo có span > 1: item đang kéo là widget (G2c), xem [planWidget].
     * Item kéo từ trang tự có span qua slot của nó trong [pages]; item đến từ dock/thư mục không có slot nên người gọi truyền span.
     */
    fun plan(
        pages: List<PageSlots>,
        movingItemId: Long,
        targetPageIndex: Int,
        targetX: Int,
        targetY: Int,
        columns: Int,
        rows: Int,
        movingSpanX: Int = 1,
        movingSpanY: Int = 1
    ): Plan {
        require(targetPageIndex in pages.indices) { "targetPageIndex $targetPageIndex ngoài khoảng 0 until ${pages.size}" }
        val own =
            pages.firstNotNullOfOrNull { p -> p.slots.firstOrNull { it.itemId == movingItemId } }
        val spanX = maxOf(movingSpanX, own?.spanX ?: 1)
        val spanY = maxOf(movingSpanY, own?.spanY ?: 1)
        if (spanX > 1 || spanY > 1) return planWidget(
            pages,
            movingItemId,
            targetPageIndex,
            targetX,
            targetY,
            columns,
            rows,
            spanX,
            spanY
        )

        val original = HashMap<Long, Triple<Int, Int, Int>>()
        pages.forEachIndexed { pi, page ->
            page.slots.forEach {
                original[it.itemId] = Triple(pi, it.x, it.y)
            }
        }

        val working =
            pages.map { page -> page.slots.filter { it.itemId != movingItemId }.toMutableList() }
                .toMutableList()
        val result = LinkedHashMap<Long, Placement>()

        var pageIndex = targetPageIndex
        var targetIndex = targetY * columns + targetX
        var newPages = 0
        var block: List<Slot> = emptyList()

        while (true) {
            val inPage = working[pageIndex]
            val free = PageGeometry.freeIndices(inPage, columns, rows)
            val k = PageGeometry.resolveInsertSeq(free, targetIndex)
            if (k < 0) {
                // Trang bị widget phủ kín: không có chỗ cho icon, sang trang kế (ca biên; widget <= 4x2 trên lưới >= 4x5 không gây ra).
                pageIndex++
                if (pageIndex == working.size) {
                    working.add(mutableListOf())
                    newPages++
                }
                targetIndex = 0
                continue
            }
            val cell = free[k]
            val icons = inPage.filter { !PageGeometry.isWidget(it) && isInside(it, columns, rows) }
            val occupant = icons.firstOrNull { it.y * columns + it.x == cell }
            val toShift = if (occupant == null) {
                emptyList()
            } else {
                listOf(occupant) + icons
                    .filter { it.itemId != occupant.itemId && it.y * columns + it.x > cell }
                    .sortedBy { it.y * columns + it.x }
            }

            inPage.removeAll(toShift)
            inPage.add(Slot(movingItemId, cell % columns, cell / columns))
            result[movingItemId] = Placement(movingItemId, pageIndex, cell % columns, cell / columns)
            if (toShift.isEmpty()) break

            // FX3 (B3): chỉ `capacity` item đầu xếp sát vào các ô tự do sau ô đích; phần còn lại (thường đúng 1 item, nhiều hơn khi
            // trang có hàng trùng ô) thành KHỐI có thứ tự đẩy sang trang kế. Trước FX3 giả định đúng 1 item tràn nên `free[k + 1 + i]`
            // ném ArrayIndexOutOfBounds khi tràn nhiều hơn.
            val capacity = (free.size - (k + 1)).coerceAtLeast(0)
            val shifted = toShift.take(capacity)
            shifted.forEachIndexed { i, slot ->
                val r = free[k + 1 + i]
                inPage.add(Slot(slot.itemId, r % columns, r / columns))
                result[slot.itemId] = Placement(slot.itemId, pageIndex, r % columns, r / columns)
            }
            block = toShift.drop(capacity)
            break
        }

        // Khối tràn: vào đầu dãy ô tự do của trang kế, tạo trang mới nếu hết trang. Khối một phần tử cho kết quả y hệt vòng lặp
        // cũ (icon tràn bay sang ô tự do đầu tiên của trang kế, dồn sát mọi icon đứng sẵn nếu ô đó đang có icon).
        while (block.isNotEmpty()) {
            pageIndex++
            if (pageIndex == working.size) {
                working.add(mutableListOf())
                newPages++
            }
            block = insertBlock(working[pageIndex], pageIndex, block, columns, rows, result)
        }

        val changed =
            result.values.filter { original[it.itemId] != Triple(it.pageIndex, it.x, it.y) }
        return Plan(changed, newPages)
    }

    /**
     * G2c Task 2 — thả widget [spanX] x [spanY] xuống neo ([targetX], [targetY]) của trang [targetPageIndex].
     * - Neo bị kẹp vào `[0, columns - spanX] x [0, rows - spanY]`. Widget lớn hơn lưới: từ chối ([Plan.rejected]).
     * - WS (2026-11-25): hình chữ nhật chồng đúng MỘT widget khác và widget đang kéo ở cùng trang: đổi chỗ ([planSwap]); widget kia cũng
     *   có trong [Plan.placements]. Chồng nhiều widget, widget kéo từ trang khác / widget mới, hoặc đổi chỗ không vừa: từ chối.
     * - Hình chữ nhật không phủ icon nào: chỉ đặt widget, không icon nào đổi chỗ.
     * - Phủ >= 1 icon (đổi chỗ: phủ bởi widget kéo HOẶC widget bị đổi; "neo" = neo nhỏ hơn trong hai neo mới):
     *   ĐUÔI (mọi icon có chỉ số đọc >= chỉ số đọc của neo, kể cả icon bị phủ) xếp sát nhau theo thứ tự đọc vào
     *   các ô tự do (sau khi có widget mới) từ ô tự do đầu tiên có chỉ số đọc >= neo — cùng luật "dồn sát đuôi" của chèn icon.
     *   Icon vượt hết ô tự do của trang (có thể nhiều) thành một KHỐI có thứ tự sang trang kế ([insertBlock], lặp, tạo trang nếu cần).
     */
    private fun planWidget(
        pages: List<PageSlots>,
        movingItemId: Long,
        targetPageIndex: Int,
        targetX: Int,
        targetY: Int,
        columns: Int,
        rows: Int,
        spanX: Int,
        spanY: Int
    ): Plan {
        val rejected = Plan(emptyList(), 0, rejected = true)
        if (spanX > columns || spanY > rows) return rejected

        val original = HashMap<Long, Triple<Int, Int, Int>>()
        pages.forEachIndexed { pi, page ->
            page.slots.forEach {
                original[it.itemId] = Triple(pi, it.x, it.y)
            }
        }
        val working =
            pages.map { page -> page.slots.filter { it.itemId != movingItemId }.toMutableList() }
                .toMutableList()
        val result = LinkedHashMap<Long, Placement>()
        var newPages = 0

        val tx = targetX.coerceIn(0, columns - spanX)
        val ty = targetY.coerceIn(0, rows - spanY)
        val inPage = working[targetPageIndex]
        val overlapped = inPage.filter { PageGeometry.isWidget(it) && overlaps(it, tx, ty, spanX, spanY) }
        // WS: chồng đúng một widget W và item đang kéo ở cùng trang: đổi chỗ (W về neo cũ của item kéo), xem [planSwap]. Còn lại từ chối.
        val swap = if (overlapped.isEmpty()) {
            null
        } else {
            val own = pages[targetPageIndex].slots.firstOrNull { it.itemId == movingItemId }
            (if (overlapped.size == 1 && own != null) planSwap(inPage, overlapped[0], own, tx, ty, spanX, spanY, columns, rows) else null)
                ?: return rejected
        }
        val ax = swap?.first ?: tx
        val ay = swap?.second ?: ty
        val swapped: Slot? = swap?.third

        val anchorIndex = minOf(ay * columns + ax, swapped?.let { it.y * columns + it.x } ?: Int.MAX_VALUE)
        val icons = inPage.filter { !PageGeometry.isWidget(it) && isInside(it, columns, rows) }
        val displaced = icons.any {
            (it.x in ax until ax + spanX && it.y in ay until ay + spanY) ||
                (swapped != null && it.x in swapped.x until swapped.x + swapped.spanX && it.y in swapped.y until swapped.y + swapped.spanY)
        }
        inPage.add(Slot(movingItemId, ax, ay, spanX, spanY))
        result[movingItemId] = Placement(movingItemId, targetPageIndex, ax, ay)
        if (swapped != null) {
            inPage.removeAll { it.itemId == swapped.itemId }
            inPage.add(swapped)
            result[swapped.itemId] = Placement(swapped.itemId, targetPageIndex, swapped.x, swapped.y)
        }

        var block: List<Slot> = emptyList()
        if (displaced) {
            val tail = icons.filter { it.y * columns + it.x >= anchorIndex }
                .sortedBy { it.y * columns + it.x }
            inPage.removeAll(tail)
            val free = PageGeometry.freeIndices(inPage, columns, rows)
            val k0 = PageGeometry.lowerBound(free, anchorIndex)
            val overflow = ArrayList<Slot>()
            tail.forEachIndexed { i, slot ->
                if (k0 + i < free.size) {
                    val r = free[k0 + i]
                    inPage.add(Slot(slot.itemId, r % columns, r / columns))
                    result[slot.itemId] =
                        Placement(slot.itemId, targetPageIndex, r % columns, r / columns)
                } else {
                    overflow.add(slot)
                }
            }
            block = overflow
        }

        var pageIndex = targetPageIndex
        while (block.isNotEmpty()) {
            pageIndex++
            if (pageIndex == working.size) {
                working.add(mutableListOf())
                newPages++
            }
            block = insertBlock(working[pageIndex], pageIndex, block, columns, rows, result)
        }

        val changed =
            result.values.filter { original[it.itemId] != Triple(it.pageIndex, it.x, it.y) }
        return Plan(changed, newPages)
    }

    /**
     * WS — đổi chỗ widget: item kéo (chỗ cũ [own], cỡ [spanX] x [spanY]) đè lên đúng một widget [other] của [inPage] (đã gỡ item kéo).
     * [other] về neo cũ của item kéo; neo mới của item kéo thử lần lượt (a) neo đích ([ax], [ay]), (b) neo của [other] (lấy đúng chỗ nó).
     * Trả (neo x, neo y, [other] ở chỗ mới) của lựa chọn đầu tiên hợp lệ: [other] mới vừa lưới, không chồng item kéo mới, không chồng widget
     * khác; item kéo mới chỉ chồng [other] (ở chỗ cũ). null = không đổi chỗ được (người gọi từ chối).
     */
    private fun planSwap(
        inPage: List<Slot>,
        other: Slot,
        own: Slot,
        ax: Int,
        ay: Int,
        spanX: Int,
        spanY: Int,
        columns: Int,
        rows: Int
    ): Triple<Int, Int, Slot>? {
        val moved = Slot(other.itemId, own.x, own.y, other.spanX, other.spanY)
        if (moved.x < 0 || moved.y < 0 || moved.x + moved.spanX > columns || moved.y + moved.spanY > rows) return null
        val rest = inPage.filter { PageGeometry.isWidget(it) && it.itemId != other.itemId }
        if (rest.any { overlaps(it, moved.x, moved.y, moved.spanX, moved.spanY) }) return null
        val candidates = listOf(ax to ay, other.x.coerceIn(0, columns - spanX) to other.y.coerceIn(0, rows - spanY))
        for ((cx, cy) in candidates) {
            if (overlaps(moved, cx, cy, spanX, spanY)) continue
            if (rest.any { overlaps(it, cx, cy, spanX, spanY) }) continue
            return Triple(cx, cy, moved)
        }
        return null
    }

    /** Hình chữ nhật của [slot] chồng hình chữ nhật [spanX] x [spanY] neo ([x], [y]). */
    private fun overlaps(slot: Slot, x: Int, y: Int, spanX: Int, spanY: Int): Boolean =
        x < slot.x + slot.spanX && slot.x < x + spanX && y < slot.y + slot.spanY && slot.y < y + spanY

    /**
     * FX3 (B3): ô của [slot] nằm trong lưới [columns] x [rows]. Chỉ số đọc `y * columns + x` của item có `x >= columns` trùng
     * với ô của hàng kế (nhập nhằng), `y >= rows` vượt `columns * rows`; cả hai bị bỏ qua thay vì làm sai kế hoạch.
     */
    private fun isInside(slot: Slot, columns: Int, rows: Int): Boolean =
        slot.x in 0 until columns && slot.y in 0 until rows

    /**
     * Chèn KHỐI icon [block] (có thứ tự) vào đầu dãy ô tự do của [inPage] (trang [pageIndex]). Giống chèn 1 icon vào ô tự do đầu
     * tiên (block có 1 phần tử cho kết quả y hệt vòng lặp của [plan]): nếu có icon đứng trong các ô được block chiếm thì block rồi
     * MỌI icon của trang xếp sát nhau từ ô tự do đầu tiên, ngược lại chỉ đặt block. Trả phần vượt hết ô tự do (rỗng nếu vừa).
     */
    private fun insertBlock(
        inPage: MutableList<Slot>,
        pageIndex: Int,
        block: List<Slot>,
        columns: Int,
        rows: Int,
        result: MutableMap<Long, Placement>
    ): List<Slot> {
        val free = PageGeometry.freeIndices(inPage, columns, rows)
        val icons = inPage.filter { !PageGeometry.isWidget(it) && isInside(it, columns, rows) }
        val claimed = (0 until minOf(block.size, free.size)).map { free[it] }.toHashSet()
        val occupied = icons.any { it.y * columns + it.x in claimed }
        val assign = if (occupied) block + icons.sortedBy { it.y * columns + it.x } else block
        if (occupied) inPage.removeAll(icons)
        val overflow = ArrayList<Slot>()
        assign.forEachIndexed { i, slot ->
            if (i < free.size) {
                val r = free[i]
                inPage.add(Slot(slot.itemId, r % columns, r / columns))
                result[slot.itemId] = Placement(slot.itemId, pageIndex, r % columns, r / columns)
            } else {
                overflow.add(slot)
            }
        }
        return overflow
    }
}
