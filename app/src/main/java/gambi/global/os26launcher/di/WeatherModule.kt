package gambi.global.os26launcher.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.BuildConfig
import gambi.global.os26launcher.data.weather.CoarseLocationSourceImpl
import gambi.global.os26launcher.data.weather.notify.AlarmWeatherNotificationScheduler
import gambi.global.os26launcher.domain.weather.WeatherNotificationScheduler
import gambi.global.os26launcher.data.weather.WeatherPlacesRepositoryImpl
import gambi.global.os26launcher.data.weather.WeatherPreferencesRepositoryImpl
import gambi.global.os26launcher.data.weather.WeatherProviderConfig
import gambi.global.os26launcher.data.weather.WeatherRepositoryImpl
import gambi.global.os26launcher.domain.weather.CoarseLocationSource
import gambi.global.os26launcher.domain.weather.WeatherPlacesRepository
import gambi.global.os26launcher.domain.weather.WeatherPreferencesRepository
import gambi.global.os26launcher.domain.weather.WeatherProvider
import gambi.global.os26launcher.domain.weather.WeatherRepository
import javax.inject.Singleton
import gambi.global.os26launcher.data.weather.OpenMeteoConfig
import gambi.global.os26launcher.data.weather.FallbackWeatherProvider

/**
 * G3e — nối nguồn thời tiết. Đổi sang proxy/backend: đổi `baseUrl`/khóa ở đây hoặc bind một [WeatherProvider] khác.
 * WM: nguồn đang bind là [FallbackWeatherProvider] (OWM chính, Open-Meteo dự phòng).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WeatherModule {
    @Binds
    abstract fun bindWeatherProvider(impl: FallbackWeatherProvider): WeatherProvider

    // Scope @Singleton nằm trên WeatherRepositoryImpl.
    @Binds
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository

    // S1: ui/weather chỉ biết interface ở domain. Scope @Singleton nằm trên CoarseLocationSourceImpl.
    @Binds
    abstract fun bindCoarseLocationSource(impl: CoarseLocationSourceImpl): CoarseLocationSource

    // WU: lựa chọn của người dùng (đơn vị nhiệt độ). Scope @Singleton nằm trên WeatherPreferencesRepositoryImpl.
    @Binds
    abstract fun bindWeatherPreferencesRepository(impl: WeatherPreferencesRepositoryImpl): WeatherPreferencesRepository

    // WX: danh sách địa điểm theo dõi + địa điểm của từng widget. Scope @Singleton nằm trên WeatherPlacesRepositoryImpl.
    @Binds
    abstract fun bindWeatherPlacesRepository(impl: WeatherPlacesRepositoryImpl): WeatherPlacesRepository

    // WN: lịch thông báo thời tiết. Scope @Singleton nằm trên AlarmWeatherNotificationScheduler.
    @Binds
    abstract fun bindWeatherNotificationScheduler(impl: AlarmWeatherNotificationScheduler): WeatherNotificationScheduler

    companion object {
        @Provides
        @Singleton
        fun provideWeatherProviderConfig(): WeatherProviderConfig =
            WeatherProviderConfig(
                baseUrl = "https://api.openweathermap.org",
                apiKey = BuildConfig.OWM_API_KEY
            )

        /** WM: nguồn dự phòng Open-Meteo (cấu hình ở `buildTypes`). */
        @Provides
        @Singleton
        fun provideOpenMeteoConfig(): OpenMeteoConfig =
            OpenMeteoConfig(
                baseUrl = BuildConfig.OPEN_METEO_BASE_URL,
                apiKey = BuildConfig.OPEN_METEO_API_KEY
            )
    }
}
