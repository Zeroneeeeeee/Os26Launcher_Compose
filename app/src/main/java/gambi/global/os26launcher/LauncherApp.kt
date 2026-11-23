package gambi.global.os26launcher

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import dagger.hilt.android.HiltAndroidApp
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.core.ui.TransitionMotion
import gambi.global.os26launcher.domain.config.MotionConfigRepository
import gambi.global.os26launcher.domain.photo.PhotoWidgetRepository
import gambi.global.os26launcher.domain.repository.LayoutRepository
import gambi.global.os26launcher.ui.widget.PhotoBitmapCache
import gambi.global.os26launcher.ui.widget.PhotoWidgetHub
import gambi.global.os26launcher.domain.weather.WeatherPlacesRepository
import gambi.global.os26launcher.domain.weather.WeatherPreferencesRepository
import gambi.global.os26launcher.domain.weather.WeatherRepository
import gambi.global.os26launcher.ui.widget.WeatherWidgetHub
import gambi.global.os26launcher.domain.weather.WeatherNotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LauncherApp : Application() {
    @Inject
    lateinit var weatherRepository: WeatherRepository

    @Inject
    lateinit var weatherPreferences: WeatherPreferencesRepository

    @Inject
    lateinit var weatherPlaces: WeatherPlacesRepository

    @Inject
    lateinit var photoWidgets: PhotoWidgetRepository

    @Inject
    lateinit var layoutRepository: LayoutRepository

    @Inject
    lateinit var motionConfig: MotionConfigRepository // TR: hiệu ứng chuyển cảnh

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    // WN: Lazy để không dựng bộ lên lịch trên luồng chính lúc khởi động
    @Inject
    lateinit var weatherNotificationScheduler: dagger.Lazy<WeatherNotificationScheduler>

    override fun onCreate() {
        super.onCreate()
        WeatherWidgetHub.init(
            weatherRepository,
            weatherPreferences,
            weatherPlaces,
            appScope
        ) // G3e: nối thời tiết với các widget đang hiện

        // WN: đặt lại lịch thông báo thời tiết (idempotent; chạy ở nền, không chặn khởi động). Khối độc lập để rebase với nhánh khác dễ.
        appScope.launch { weatherNotificationScheduler.get().rearm() }

        TransitionMotion.init(motionConfig, appScope) // TR: các chuyển động mở/đóng đọc cấu hình 4.1

        PhotoWidgetHub.init(photoWidgets, layoutRepository, appScope) // G3b: nối kho ảnh với các widget Ảnh đang hiện
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) PhotoBitmapCache.evictAll() // G3b: nhả cache ảnh khi app xuống nền
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = PhotoBitmapCache.evictAll()
        })
    }
}
