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
 */
class BuildDefaultLayoutUseCase @Inject constructor() {

    operator fun invoke(
        apps: List<AppItem>,
        dock: List<AppItem>,
        columns: Int,
        rows: Int
    ): List<HomePage> {
        if (columns <= 0 || rows <= 0) return emptyList()

        val dockKeys = dock.mapTo(HashSet()) { it.key }
        val gridApps = apps.filterNot { it.key in dockKeys }
        if (gridApps.isEmpty()) return emptyList()

        val perPage = columns * rows
        return gridApps.chunked(perPage).mapIndexed { pageIndex, pageApps ->
            // itemId/pageId = 0L: đây là layout NHÁP chưa ghi DB, LayoutReconciler.seedIfEmpty() chỉ đọc
            // position/app của items này để tạo hàng DB thật rồi tự lấy id thật từ đó (xem seedIfEmpty).
            val items = pageApps.mapIndexed { i, app ->
                PlacedApp(itemId = 0L, GridPosition(x = i % columns, y = i / columns), app)
            }
            HomePage(index = pageIndex, pageId = 0L, items = items)
        }
    }
}
