package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.GridPosition
import gambi.global.os26launcher.domain.model.HomePage
import gambi.global.os26launcher.domain.model.PlacedApp
import javax.inject.Inject

/**
 * Xếp [apps] (ĐÃ sắp theo nhãn) vào các trang [columns]×[rows], điền trái→phải rồi trên→dưới.
 * App nằm trong [dock] bị loại khỏi lưới (so theo `ComponentKey`, M4 sẽ nối dock thật ở Task 4).
 * `columns`/`rows` <= 0 hoặc hết app sau khi trừ dock đều trả list rỗng — không bao giờ chia cho 0
 * (Review Focus 4 của M3). (M3 Task 3 Bước 1)
 *
 * WF Task 2 (chủ dự án 2026-11-25): [reservedFirstPage] là các vùng (x, y, spanX, spanY) đã dành cho widget seed trên trang đầu: app bỏ qua các ô đó,
 * vẫn điền theo thứ tự đọc. [pinFirstPackage] (app launcher của mình) được đưa lên đứng đầu danh sách, nên vào ô trống đầu tiên của trang đầu.
 */
class BuildDefaultLayoutUseCase @Inject constructor() {

    operator fun invoke(
        apps: List<AppItem>,
        dock: List<AppItem>,
        columns: Int,
        rows: Int,
        reservedFirstPage: List<GridPosition> = emptyList(),
        pinFirstPackage: String? = null
    ): List<HomePage> {
        if (columns <= 0 || rows <= 0) return emptyList()

        val dockKeys = dock.mapTo(HashSet()) { it.key }
        val filtered = apps.filterNot { it.key in dockKeys }
        val pinned = pinFirstPackage?.let { pkg -> filtered.firstOrNull { it.key.packageName == pkg } }
        val gridApps = if (pinned == null) filtered else listOf(pinned) + filtered.filterNot { it === pinned }
        if (gridApps.isEmpty()) return emptyList()

        // Ô trống của trang đầu theo thứ tự đọc (trừ vùng widget); các trang sau đủ cả lưới.
        val firstPageCells = (0 until columns * rows).map { GridPosition(x = it % columns, y = it / columns) }
            .filterNot { c -> reservedFirstPage.any { r -> c.x >= r.x && c.x < r.x + r.spanX && c.y >= r.y && c.y < r.y + r.spanY } }
        val allCells = (0 until columns * rows).map { GridPosition(x = it % columns, y = it / columns) }

        val pages = ArrayList<HomePage>()
        var next = 0
        while (next < gridApps.size) {
            val cells = if (pages.isEmpty()) firstPageCells else allCells
            // itemId/pageId = 0L: đây là layout NHÁP chưa ghi DB, LayoutReconciler.seedIfEmpty() chỉ đọc
            // position/app của items này để tạo hàng DB thật rồi tự lấy id thật từ đó (xem seedIfEmpty).
            val items = cells.zip(gridApps.subList(next, minOf(next + cells.size, gridApps.size))) { pos, app ->
                PlacedApp(itemId = 0L, pos, app)
            }
            next += items.size
            // Trang đầu kín hết bởi widget thì vẫn giữ trang (rỗng app); vòng sau dùng cả lưới (columns, rows > 0) nên không lặp vô hạn.
            pages.add(HomePage(index = pages.size, pageId = 0L, items = items))
        }
        return pages
    }
}
