package gambi.global.os26launcher.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Phải là delegate ở CẤP FILE, không đặt trong object: đặt trong object sẽ tạo nhiều
// instance cho cùng một file và DataStore ném lỗi lúc chạy.
private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "launcher_settings"
)

// CF: tên app tự đặt (RN, trước đây DataStore `app_labels`) đã chuyển sang Room `app_overrides`. File `app_labels` cũ còn trên máy thì
// không ai đọc nữa (gỡ cài lại là sạch).

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.launcherDataStore
}
