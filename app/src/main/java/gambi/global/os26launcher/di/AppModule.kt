package gambi.global.os26launcher.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.core.coroutines.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // SupervisorJob: một flow lỗi không giết các flow khác dùng chung scope này.
    // Handler (B1): lỗi chưa bắt được ghi log thay vì làm sập process; một flow chia sẻ lỗi sẽ dừng, không sập.
    // Chỉ ghi tên lớp và một khung stack: thông điệp ngoại lệ có thể chứa URL/khóa (quy tắc bí mật G3e).
    // Scope không bao giờ cancel, đó là ý muốn: stateIn cần nó sống bằng tuổi process.
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher + uncaughtHandler)

    private const val TAG = "AppScope"

    private val uncaughtHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "uncaught ${e.javaClass.name} at ${e.stackTrace.firstOrNull()}")
        if (e is VirtualMachineError) throw e // hết bộ nhớ: để process chết như cũ
    }
}
