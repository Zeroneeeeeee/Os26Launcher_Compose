package gambi.global.os26launcher.domain.model

/**
 * G3c — tham chiếu một provider widget của app khác, không dùng kiểu Android (domain không có `ComponentName`): package, lớp provider
 * (`AppWidgetProviderInfo.provider`) và số hiệu hồ sơ chứa provider (`launcher_items.userSerial`, cùng nghĩa với `ComponentKey.userSerial`).
 */
data class AppWidgetRef(
    val packageName: String,
    val className: String,
    val userSerial: Long
)

/**
 * G3c Task 6 — một provider widget của app khác trong danh mục bảng "Tiện ích", chỉ gồm số (dp, ô) và cờ: domain không giữ `AppWidgetProviderInfo`.
 * Nhãn, ảnh xem trước tải lười ở `core/appwidget/AppWidgetAssets` theo [ref] khi người dùng mở mục của app.
 * [targetCellWidth]/[targetCellHeight] = 0 khi máy dưới API 31 hoặc provider không khai.
 */
data class AppWidgetProviderEntry(
    val ref: AppWidgetRef,
    val minWidthDp: Int,
    val minHeightDp: Int,
    val minResizeWidthDp: Int,
    val minResizeHeightDp: Int,
    val maxResizeWidthDp: Int,
    val maxResizeHeightDp: Int,
    val resizeHorizontal: Boolean,
    val resizeVertical: Boolean,
    val targetCellWidth: Int,
    val targetCellHeight: Int
)

/** G3c Task 6 — các provider của một app (một hàng trong bảng "Tiện ích"). */
data class AppWidgetAppEntry(
    val packageName: String,
    val userSerial: Long,
    val appLabel: String,
    val providers: List<AppWidgetProviderEntry>
)
