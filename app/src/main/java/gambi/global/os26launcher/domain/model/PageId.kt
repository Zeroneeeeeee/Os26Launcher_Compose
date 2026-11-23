package gambi.global.os26launcher.domain.model

/**
 * Sentinel cho id trang (M7 Task 7). [NEW] = "một trang MỚI ở cuối": là `pageId` của trang tạm (ghost page) trong UI và
 * là `targetPageId` truyền cho `moveItem` để `LayoutReconciler` tạo hàng `home_pages` thật trong cùng transaction với
 * việc đặt item. Không bao giờ trùng id DB (luôn dương) hay id tạm của trang cascade (âm nhỏ, -1, -2, ...).
 */
object PageId {
    const val NEW = Long.MIN_VALUE
}
