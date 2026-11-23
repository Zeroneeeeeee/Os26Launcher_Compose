package gambi.global.os26launcher.data.layout.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * CF — tên riêng (và sau này icon riêng) trên bảng `app_overrides`. Trạng thái ẩn đi qua [HiddenAppDao] (cùng bảng). SQLite của API 26
 * (3.18) không có UPSERT: mọi ghi một cột là `INSERT OR IGNORE` hàng mặc định + `UPDATE`, rồi [pruneEmpty], trong một transaction.
 */
@Dao
abstract class AppOverrideDao {

    @Query("SELECT packageName, className, userSerial, customLabel FROM app_overrides WHERE customLabel IS NOT NULL")
    abstract fun observeLabels(): Flow<List<AppLabelRow>>

    /** P3-4: icon riêng (tên file trong `files/custom_icons/`). */
    @Query("SELECT packageName, className, userSerial, customIconPath FROM app_overrides WHERE customIconPath IS NOT NULL")
    abstract fun observeIcons(): Flow<List<AppIconRow>>

    /** P3-4: [path] null = về icon hệ thống. */
    @Transaction
    open suspend fun setIconPath(packageName: String, className: String, userSerial: Long, path: String?) {
        insertDefault(packageName, className, userSerial)
        updateIconPath(packageName, className, userSerial, path)
        pruneEmpty()
    }

    /** [label] null = khôi phục tên gốc. */
    @Transaction
    open suspend fun setLabel(packageName: String, className: String, userSerial: Long, label: String?) {
        insertDefault(packageName, className, userSerial)
        updateLabel(packageName, className, userSerial, label)
        pruneEmpty()
    }

    /** Bỏ mọi tên riêng của một package (app đã gỡ thật); giữ các cột khác. */
    @Transaction
    open suspend fun clearLabelsOfPackage(packageName: String, userSerial: Long) {
        clearLabelsOfPackageRaw(packageName, userSerial)
        pruneEmpty()
    }

    /** Bỏ mọi tên riêng của một user (hồ sơ đã bị bỏ); giữ các cột khác. */
    @Transaction
    open suspend fun clearLabelsOfUser(userSerial: Long) {
        clearLabelsOfUserRaw(userSerial)
        pruneEmpty()
    }

    @Query("INSERT OR IGNORE INTO app_overrides(packageName, className, userSerial, hidden) VALUES(:packageName, :className, :userSerial, 0)")
    protected abstract suspend fun insertDefault(packageName: String, className: String, userSerial: Long)

    @Query("UPDATE app_overrides SET customLabel = :label WHERE packageName = :packageName AND className = :className AND userSerial = :userSerial")
    protected abstract suspend fun updateLabel(packageName: String, className: String, userSerial: Long, label: String?)

    @Query("UPDATE app_overrides SET customIconPath = :path WHERE packageName = :packageName AND className = :className AND userSerial = :userSerial")
    protected abstract suspend fun updateIconPath(packageName: String, className: String, userSerial: Long, path: String?)

    @Query("UPDATE app_overrides SET customLabel = NULL WHERE packageName = :packageName AND userSerial = :userSerial")
    protected abstract suspend fun clearLabelsOfPackageRaw(packageName: String, userSerial: Long)

    @Query("UPDATE app_overrides SET customLabel = NULL WHERE userSerial = :userSerial")
    protected abstract suspend fun clearLabelsOfUserRaw(userSerial: Long)

    @Query("DELETE FROM app_overrides WHERE hidden = 0 AND customLabel IS NULL AND customIconPath IS NULL")
    protected abstract suspend fun pruneEmpty()
}
