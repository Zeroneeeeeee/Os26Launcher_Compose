package gambi.global.os26launcher.data.layout.db

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * CF — tùy chỉnh theo từng app (khóa = component, giống `ComponentKey`): ẩn (5.1), tên riêng (5.3), icon riêng (5.2, chưa làm).
 * Thay bảng `hidden_apps` (v1) và DataStore `app_labels` (RN). Một hàng chỉ tồn tại khi ít nhất một cột khác mặc định: ghi xong mà mọi
 * cột về mặc định thì xóa hàng (`pruneEmpty`). Không nằm trong `launcher_items` vì bảng đó là VỊ TRÍ, app ẩn không có hàng ở đó
 * (`docs/room-schema.md` mục 4).
 */
@Entity(tableName = "app_overrides", primaryKeys = ["packageName", "className", "userSerial"])
data class AppOverrideEntity(
    val packageName: String,
    val className: String,
    val userSerial: Long,
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    val customLabel: String? = null,
    /** File trong `files/custom_icons/`; null = icon hệ thống. */
    val customIconPath: String? = null
)

/** CF — một tên riêng đọc từ [AppOverrideEntity] (chỉ hàng có `customLabel`). */
data class AppLabelRow(
    val packageName: String,
    val className: String,
    val userSerial: Long,
    val customLabel: String
)

/** P3-4 — một icon riêng đọc từ [AppOverrideEntity] (chỉ hàng có `customIconPath`). */
data class AppIconRow(
    val packageName: String,
    val className: String,
    val userSerial: Long,
    val customIconPath: String
)
