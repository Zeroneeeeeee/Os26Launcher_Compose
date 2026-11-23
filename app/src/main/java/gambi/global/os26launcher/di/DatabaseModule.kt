package gambi.global.os26launcher.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.data.layout.db.AppOverrideDao
import gambi.global.os26launcher.data.layout.db.HiddenAppDao
import gambi.global.os26launcher.data.layout.db.HomePageDao
import gambi.global.os26launcher.data.layout.db.LauncherDatabase
import gambi.global.os26launcher.data.layout.db.LauncherItemDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // Không allowMainThreadQueries(). Room tạo file DB lười, ở truy vấn đầu tiên.
    // CF: CHỈ v1 -> v2 được phá dữ liệu (app chưa phát hành, chủ dự án chốt 2026-11-17): cài đè lên bản v1 thì bố cục Home và app ẩn
    // dựng lại từ đầu thay vì sập. Từ v2 mọi đổi schema phải có Migration viết tay; không thêm version nào vào danh sách này.
    @Provides
    @Singleton
    fun provideLauncherDatabase(@ApplicationContext context: Context): LauncherDatabase =
        Room.databaseBuilder(context, LauncherDatabase::class.java, "launcher.db")
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
            .build()

    @Provides
    fun provideLauncherItemDao(db: LauncherDatabase): LauncherItemDao = db.launcherItemDao()

    @Provides
    fun provideHomePageDao(db: LauncherDatabase): HomePageDao = db.homePageDao()

    // M7
    @Provides
    fun provideHiddenAppDao(db: LauncherDatabase): HiddenAppDao = db.hiddenAppDao()

    // CF
    @Provides
    fun provideAppOverrideDao(db: LauncherDatabase): AppOverrideDao = db.appOverrideDao()
}
