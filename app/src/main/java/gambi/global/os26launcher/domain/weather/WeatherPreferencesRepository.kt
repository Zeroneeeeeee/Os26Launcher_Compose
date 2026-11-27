package gambi.global.os26launcher.domain.weather

import gambi.global.os26launcher.domain.model.weather.TemperatureUnit
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationKind
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationSettings
import kotlinx.coroutines.flow.Flow
import gambi.global.os26launcher.domain.model.weather.WeatherRefreshInterval

/** WU — lựa chọn của người dùng cho thời tiết (đơn vị nhiệt độ; WN thêm hai công tắc thông báo và ngày đã đăng). */
interface WeatherPreferencesRepository {
    /** Khóa vắng hoặc tên lạ = [TemperatureUnit.DEFAULT]. */
    val unit: Flow<TemperatureUnit>

    /** Trả về sau khi ghi xong. */
    suspend fun setUnit(unit: TemperatureUnit)

    /** WR: chu kỳ "Làm mới sau"; khóa vắng hoặc tên lạ = [WeatherRefreshInterval.DEFAULT] (1 ngày). */
    val refreshInterval: Flow<WeatherRefreshInterval>

    suspend fun setRefreshInterval(interval: WeatherRefreshInterval)

    /** WN: hai công tắc thông báo; khóa vắng = tắt. */
    val notifications: Flow<WeatherNotificationSettings>

    suspend fun setNotificationEnabled(kind: WeatherNotificationKind, enabled: Boolean)

    /** WN: một lần đọc công tắc hiện tại (cho receiver/job không muốn giữ collector). */
    suspend fun notificationSettings(): WeatherNotificationSettings

    /** WN: mã ngày `yyyy-MM-dd` (giờ máy) của lần đã đăng gần nhất của [kind]; null = chưa từng. Chống đăng trùng trong ngày. */
    suspend fun lastShownDay(kind: WeatherNotificationKind): String?

    suspend fun markShown(kind: WeatherNotificationKind, day: String)
}
