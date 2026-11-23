package gambi.global.os26launcher.data.weather

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationKind

/** WU — khóa DataStore của thời tiết, tách khỏi `SettingsKeys` (file dùng chung), cùng kiểu `SearchPrefsKeys`. */
internal object WeatherPrefsKeys {
    /** Tên enum `TemperatureUnit`; vắng = °C. */
    val TEMPERATURE_UNIT = stringPreferencesKey("weather_temperature_unit")

    /** WN: công tắc thông báo hằng ngày / ngày mai; vắng = tắt. */
    val NOTIFY_DAILY = booleanPreferencesKey("weather_notify_daily")
    val NOTIFY_TOMORROW = booleanPreferencesKey("weather_notify_tomorrow")

    /** WN: mã ngày `yyyy-MM-dd` của lần đăng gần nhất; vắng = chưa từng. */
    private val SHOWN_DAILY = stringPreferencesKey("weather_notify_shown_daily")
    private val SHOWN_TOMORROW = stringPreferencesKey("weather_notify_shown_tomorrow")

    fun notifyKey(kind: WeatherNotificationKind) = when (kind) {
        WeatherNotificationKind.DAILY -> NOTIFY_DAILY
        WeatherNotificationKind.TOMORROW -> NOTIFY_TOMORROW
    }

    fun shownKey(kind: WeatherNotificationKind) = when (kind) {
        WeatherNotificationKind.DAILY -> SHOWN_DAILY
        WeatherNotificationKind.TOMORROW -> SHOWN_TOMORROW
    }
}
