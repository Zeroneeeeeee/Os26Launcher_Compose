package gambi.global.os26launcher.data.layout.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Một hàng cho MỌI loại item (app, thư mục, shortcut, widget). Lý do từng trường: docs/room-schema.md mục 3.
 *
 * Bất biến (compiler không kiểm tra, phải tự giữ):
 * - [pageId] chỉ có nghĩa khi [container] = [CONTAINER_HOME]; dock và thư mục để 0.
 * - [pageId] là id của bảng home_pages, KHÔNG phải chỉ số trang.
 * - [container] là MỘT cột mang bốn nghĩa: [CONTAINER_HOME], [CONTAINER_DOCK], [CONTAINER_TODAY] (TV), hoặc id (>0) của thư mục chứa nó.
 * - Số trong [itemType] đã ghi xuống DB thì không bao giờ được đổi nghĩa.
 */
@Entity(
    tableName = "launcher_items",
    indices = [Index("container", "pageId"), Index("packageName")]
)
data class LauncherItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val container: Long,
    val pageId: Long = 0,
    val gridX: Int = 0,
    val gridY: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val rank: Int = 0,
    val itemType: Int,
    val packageName: String? = null,
    val className: String? = null,
    val userSerial: Long = 0,
    val title: String? = null,
    val shortcutId: String? = null,
    val appWidgetId: Int? = null
) {
    companion object {
        const val CONTAINER_HOME = -100L
        const val CONTAINER_DOCK = -101L

        /** TV: màn widget bên trái trang 1. Cùng giá trị với `ContainerId.TODAY`. Chỉ chứa widget (itemType 3, 4), thứ tự theo [rank]. */
        const val CONTAINER_TODAY = -102L

        const val ITEM_TYPE_APP = 0
        const val ITEM_TYPE_FOLDER = 1
        const val ITEM_TYPE_SHORTCUT = 2
        const val ITEM_TYPE_WIDGET = 3

        /** G2c: widget tự vẽ (đồng hồ, lịch). `className` = mã loại (`WidgetKind.id`), `spanX/spanY` = cỡ, `packageName`/`appWidgetId` = null. Số 4 đã ghi xuống DB thì không đổi nghĩa. */
        const val ITEM_TYPE_BUILTIN_WIDGET = 4
    }
}
