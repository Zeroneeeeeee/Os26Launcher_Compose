package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.GridPosition
import gambi.global.os26launcher.domain.model.HomePage
import gambi.global.os26launcher.domain.model.PlacedApp
import gambi.global.os26launcher.domain.model.PlacedFolder
import gambi.global.os26launcher.domain.model.PlacedItem
import javax.inject.Inject

/**
 * Xếp [apps] (ĐÃ sắp theo nhãn) vào các trang [columns]×[rows], điền trái→phải rồi trên→dưới.
 * App nằm trong [dock] bị loại khỏi lưới (so theo `ComponentKey`, M4 sẽ nối dock thật ở Task 4).
 * `columns`/`rows` <= 0 hoặc hết app sau khi trừ dock đều trả list rỗng — không bao giờ chia cho 0
 * (Review Focus 4 của M3). (M3 Task 3 Bước 1)
 *
 * WF Task 2 (chủ dự án 2026-11-25): [reservedFirstPage] là các vùng (x, y, spanX, spanY) đã dành cho widget seed trên trang đầu: app bỏ qua các ô đó,
 * vẫn điền theo thứ tự đọc. [pinFirstPackage] (app launcher của mình) được đưa lên đứng đầu danh sách, nên vào ô trống đầu tiên của trang đầu.
 *
 * AG mở lại (2026-11-26, QĐ 12): [folders] (tên, app theo thứ tự) đứng ngay sau app ghim, trước app phẳng; mỗi thư mục là một ô
 * ([PlacedFolder] NHÁP: `itemId = 0`, `childIds` rỗng, `seedIfEmpty` tự ghi hàng thư mục + app con). Thành viên trùng dock bị bỏ; thư mục
 * còn < 2 app thì không tạo, app của nó về nằm phẳng. App đã vào thư mục không nằm phẳng nữa.
 */
class BuildDefaultLayoutUseCase @Inject constructor() {

    operator fun invoke(
        apps: List<AppItem>,
        dock: List<AppItem>,
        columns: Int,
        rows: Int,
        reservedFirstPage: List<GridPosition> = emptyList(),
        pinFirstPackage: String? = null,
        folders: List<Pair<String, List<AppItem>>> = emptyList()
    ): List<HomePage> {
        if (columns <= 0 || rows <= 0) return emptyList()

        val dockKeys = dock.mapTo(HashSet()) { it.key }
        val filtered = apps.filterNot { it.key in dockKeys }
        val pinned = pinFirstPackage?.let { pkg -> filtered.firstOrNull { it.key.packageName == pkg } }
        // Thư mục nháp: chỉ giữ thành viên có trên lưới (không ở dock, không phải app ghim), mỗi app một thư mục, ≥ 2 app.
        val gridKeys = filtered.mapTo(HashSet()) { it.key }
        pinned?.let { gridKeys.remove(it.key) }
        val inFolder = HashSet<ComponentKey>()
        val folderDrafts = folders.mapNotNull { (title, members) ->
            val kept = members.filter { it.key in gridKeys && it.key !in inFolder }
            if (kept.size < 2) {
                null
            } else {
                kept.mapTo(inFolder) { it.key }
                PlacedFolder(itemId = 0L, position = GridPosition(0, 0), title = title, previewApps = kept, childIds = emptyList(), appCount = kept.size)
            }
        }
        val flat = filtered.filterNot { it === pinned || it.key in inFolder }
            .map<AppItem, PlacedItem> { PlacedApp(itemId = 0L, GridPosition(0, 0), it) }
        val gridItems: List<PlacedItem> =
            listOfNotNull<PlacedItem>(pinned?.let { PlacedApp(itemId = 0L, GridPosition(0, 0), it) }) + folderDrafts + flat
        if (gridItems.isEmpty()) return emptyList()

        // Ô trống của trang đầu theo thứ tự đọc (trừ vùng widget); các trang sau đủ cả lưới.
        val firstPageCells = (0 until columns * rows).map { GridPosition(x = it % columns, y = it / columns) }
            .filterNot { c -> reservedFirstPage.any { r -> c.x >= r.x && c.x < r.x + r.spanX && c.y >= r.y && c.y < r.y + r.spanY } }
        val allCells = (0 until columns * rows).map { GridPosition(x = it % columns, y = it / columns) }

        val pages = ArrayList<HomePage>()
        var next = 0
        while (next < gridItems.size) {
            val cells = if (pages.isEmpty()) firstPageCells else allCells
            // itemId/pageId = 0L: đây là layout NHÁP chưa ghi DB, LayoutReconciler.seedIfEmpty() chỉ đọc
            // position/app (AG: cả tên + app con của thư mục) của items này để tạo hàng DB thật rồi tự lấy id thật từ đó (xem seedIfEmpty).
            val items = cells.zip(gridItems.subList(next, minOf(next + cells.size, gridItems.size))) { pos, item ->
                item.withPosition(pos)
            }
            next += items.size
            // Trang đầu kín hết bởi widget thì vẫn giữ trang (rỗng app); vòng sau dùng cả lưới (columns, rows > 0) nên không lặp vô hạn.
            pages.add(HomePage(index = pages.size, pageId = 0L, items = items))
        }
        return pages
    }
}
