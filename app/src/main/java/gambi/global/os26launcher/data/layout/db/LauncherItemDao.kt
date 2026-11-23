package gambi.global.os26launcher.data.layout.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Không dùng tham số có giá trị mặc định trong @Query: Room sinh code từ chữ ký, giá trị mặc định của
// Kotlin không tới được SQL. Phía gọi truyền hằng CONTAINER_* của LauncherItemEntity.
@Dao
interface LauncherItemDao {

    @Query("SELECT * FROM launcher_items WHERE container = :container ORDER BY pageId, gridY, gridX")
    fun observeByContainer(container: Long): Flow<List<LauncherItemEntity>>

    @Query("SELECT * FROM launcher_items WHERE container = :container ORDER BY rank")
    fun observeByRank(container: Long): Flow<List<LauncherItemEntity>>

    // Thêm ở Task 2 (không đổi schema): cần thấy cả hàng mồ côi (container > 0 không có thư mục cha) để Log.w.
    @Query("SELECT * FROM launcher_items ORDER BY pageId, gridY, gridX")
    fun observeAll(): Flow<List<LauncherItemEntity>>

    @Query("SELECT * FROM launcher_items")
    suspend fun getAll(): List<LauncherItemEntity>

    @Query("SELECT COUNT(*) FROM launcher_items")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(items: List<LauncherItemEntity>): List<Long>

    @Update
    suspend fun updateAll(items: List<LauncherItemEntity>)

    /** G3c: `appWidgetId` của hàng widget app (`itemType = 3`) có id [id]; null nếu hàng không phải widget app hoặc không tồn tại. Không đổi schema (chỉ là truy vấn). */
    @Query("SELECT appWidgetId FROM launcher_items WHERE id = :id AND itemType = 3")
    suspend fun appWidgetIdOf(id: Long): Int?

    @Query("DELETE FROM launcher_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * FX3 (A3): đổi `className` của MỌI hàng app (`itemType = 0`) của một activity khởi động đã đổi tên khi app cập nhật.
     * Chỉ ghi giá trị, giữ `id`, `container`, `pageId`, ô và `rank` nên vị trí, slot dock và chỗ trong thư mục không đổi. Trả số hàng đã đổi.
     */
    @Query("UPDATE launcher_items SET className = :newClass WHERE packageName = :packageName AND className = :oldClass AND userSerial = :userSerial AND itemType = 0")
    suspend fun renameComponent(packageName: String, oldClass: String, newClass: String, userSerial: Long): Int
}
