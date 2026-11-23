package gambi.global.os26launcher.data.layout.db

import androidx.room.Database
import androidx.room.RoomDatabase

// exportSchema = true: file schema commit vào app/schemas/. Mỗi lần đổi entity là một Migration viết tay (dự án không có test đỡ
// migration, A7). Ngoại lệ duy nhất: v1 -> v2 (CF, 2026-11-17, app chưa phát hành) phá dữ liệu cũ (`DatabaseModule`); từ v2 trở đi không
// được dùng fallbackToDestructiveMigration nữa.
@Database(
    entities = [LauncherItemEntity::class, HomePageEntity::class, AppOverrideEntity::class],
    version = 2,
    exportSchema = true
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun launcherItemDao(): LauncherItemDao
    abstract fun homePageDao(): HomePageDao

    // M7: app ẩn. CF (v2): đọc/ghi cột `hidden` của bảng `app_overrides`.
    abstract fun hiddenAppDao(): HiddenAppDao

    // CF (v2): tên riêng (và sau này icon riêng) của từng app.
    abstract fun appOverrideDao(): AppOverrideDao
}
