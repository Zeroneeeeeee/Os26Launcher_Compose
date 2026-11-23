package gambi.global.os26launcher.data.layout.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * App bị ẩn. CF: từ Room v2 trạng thái ẩn là cột `hidden` của bảng `app_overrides` (cùng tên riêng, icon riêng); tên và chữ ký mọi hàm
 * giữ nguyên như bản `hidden_apps` để `LayoutReconciler`, `LayoutRepositoryImpl` không phải sửa. SQLite API 26 không có UPSERT: xem
 * [AppOverrideDao].
 */
@Dao
abstract class HiddenAppDao {

    @Query("SELECT packageName, className, userSerial FROM app_overrides WHERE hidden = 1")
    abstract fun observeAll(): Flow<List<HiddenAppEntity>>

    @Query("SELECT packageName, className, userSerial FROM app_overrides WHERE hidden = 1")
    abstract suspend fun getAll(): List<HiddenAppEntity>

    /** P3-7: app có đang ẩn không (lịch sử app gần đây không ghi app ẩn). */
    @Query("SELECT EXISTS(SELECT 1 FROM app_overrides WHERE hidden = 1 AND packageName = :packageName AND className = :className AND userSerial = :userSerial)")
    abstract suspend fun isHidden(packageName: String, className: String, userSerial: Long): Boolean

    @Transaction
    open suspend fun insert(entity: HiddenAppEntity) {
        insertDefault(entity.packageName, entity.className, entity.userSerial)
        setHidden(entity.packageName, entity.className, entity.userSerial, true)
    }

    /** Bỏ ẩn; hàng còn tên/icon riêng thì giữ, không thì xóa. */
    @Transaction
    open suspend fun delete(packageName: String, className: String, userSerial: Long) {
        setHidden(packageName, className, userSerial, false)
        pruneEmpty()
    }

    /** M7 Task 11: dọn khi app bị gỡ THẬT (mọi activity của package, đúng user profile). CF: xóa cả hàng (bỏ mọi tùy chỉnh). */
    @Query("DELETE FROM app_overrides WHERE packageName = :packageName AND userSerial = :userSerial")
    abstract suspend fun deleteByPackage(packageName: String, userSerial: Long)

    /** FX5 (C10): hồ sơ công việc bị bỏ thì dọn mọi tùy chỉnh của user đó. */
    @Query("DELETE FROM app_overrides WHERE userSerial = :userSerial")
    abstract suspend fun deleteByUser(userSerial: Long)

    /**
     * FX3 (A3): app đổi tên activity khởi động khi cập nhật vẫn giữ tùy chỉnh (khóa chính gồm `className` nên đổi bằng UPDATE). Trả số
     * hàng đã đổi. CF: áp cho cả tên riêng/icon riêng, không chỉ trạng thái ẩn.
     */
    @Query("UPDATE app_overrides SET className = :newClass WHERE packageName = :packageName AND className = :oldClass AND userSerial = :userSerial")
    abstract suspend fun renameComponent(packageName: String, oldClass: String, newClass: String, userSerial: Long): Int

    @Query("INSERT OR IGNORE INTO app_overrides(packageName, className, userSerial, hidden) VALUES(:packageName, :className, :userSerial, 0)")
    protected abstract suspend fun insertDefault(packageName: String, className: String, userSerial: Long)

    @Query("UPDATE app_overrides SET hidden = :hidden WHERE packageName = :packageName AND className = :className AND userSerial = :userSerial")
    protected abstract suspend fun setHidden(packageName: String, className: String, userSerial: Long, hidden: Boolean)

    @Query("DELETE FROM app_overrides WHERE hidden = 0 AND customLabel IS NULL AND customIconPath IS NULL")
    protected abstract suspend fun pruneEmpty()
}
