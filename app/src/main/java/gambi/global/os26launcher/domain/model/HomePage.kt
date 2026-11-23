package gambi.global.os26launcher.domain.model

/**
 * Một trang lưới icon, [index] từ 0 (thứ tự hiển thị, suy ra từ `rank`). [pageId] là id hàng
 * `home_pages` thật (M7 Task 5 thêm, để UI truyền đúng `targetPageId` khi kéo-thả) -- `0L` chỉ dùng
 * cho layout NHÁP chưa ghi DB, cùng lý do với [PlacedItem.itemId]. (M3 Task 3)
 */
data class HomePage(val index: Int, val pageId: Long, val items: List<PlacedItem>)

/**
 * Một app đang nằm trong dock. [itemId] là id hàng `launcher_items` thật (M7 Task 6, để kéo-thả được từ/vào dock
 * theo đúng hàng DB — trước đó dock chỉ là `List<AppItem>` không có id). Thứ tự trong danh sách = `rank`.
 * Ô dock là app ([app] != null) hoặc thư mục ([folder] != null, `position.x` = ô dock), không bao giờ cả hai.
 */
data class DockApp(val itemId: Long, val app: AppItem?, val folder: PlacedFolder? = null)
