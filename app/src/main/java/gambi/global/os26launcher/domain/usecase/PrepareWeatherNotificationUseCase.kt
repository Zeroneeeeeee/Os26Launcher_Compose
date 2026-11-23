package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.weather.NotificationPrep
import gambi.global.os26launcher.domain.model.weather.NotificationSkip
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationConfig
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationKind
import gambi.global.os26launcher.domain.model.weather.WeatherPlaces
import gambi.global.os26launcher.domain.weather.WeatherForecastSummarizer
import gambi.global.os26launcher.domain.weather.WeatherNotificationPlanner
import gambi.global.os26launcher.domain.weather.WeatherPlacesRepository
import gambi.global.os26launcher.domain.weather.WeatherPreferencesRepository
import gambi.global.os26launcher.domain.weather.WeatherRepository
import java.time.ZoneId
import javax.inject.Inject

/**
 * WN Task 4 — chuẩn bị bản tóm tắt cho một thông báo thời tiết (plan D3/D6). Trả [NotificationPrep.Skip] (= KHÔNG đăng, không báo lỗi) khi:
 * quá thời gian đăng của hôm nay hoặc hôm nay đã đăng; build không có khóa; "Vị trí hiện tại" chưa xác định; không có dữ liệu dùng được
 * ([NotificationSkip.NO_DATA]: chưa có cache, cache quá cũ [WeatherNotificationConfig.MAX_STALE_CACHE_MS], hoặc không đủ mốc dự báo).
 *
 * Dữ liệu: [WeatherRepository.snapshotForNotification] ("current"): dùng cache nếu chưa đến hạn 2 giờ, đến hạn thì tải MỘT lần; người gọi chỉ chờ
 * tối đa [fetchTimeoutMs], hết hạn hay tải lỗi thì dùng cache; không bao giờ lấy vị trí. [force] (chỉ công cụ gỡ lỗi) bỏ qua kiểm thời gian đăng
 * và ngày đã đăng. Không log gì (không địa điểm, không nội dung).
 */
class PrepareWeatherNotificationUseCase @Inject constructor(
    private val weather: WeatherRepository,
    private val places: WeatherPlacesRepository,
    private val prefs: WeatherPreferencesRepository
) {
    suspend operator fun invoke(
        kind: WeatherNotificationKind,
        nowMillis: Long,
        force: Boolean = false,
        fetchTimeoutMs: Long = WeatherNotificationConfig.JOB_FETCH_TIMEOUT_MS
    ): NotificationPrep {
        if (!force) {
            val zone = ZoneId.systemDefault()
            if (!WeatherNotificationPlanner.withinWindow(kind, nowMillis, zone)) return NotificationPrep.Skip(NotificationSkip.WINDOW)
            if (prefs.lastShownDay(kind) == WeatherNotificationPlanner.dayKey(nowMillis, zone)) {
                return NotificationPrep.Skip(NotificationSkip.SHOWN)
            }
        }
        if (!weather.hasKey) return NotificationPrep.Skip(NotificationSkip.NO_KEY)
        places.awaitLoaded()
        if (places.resolve(WeatherPlaces.CURRENT) == null) return NotificationPrep.Skip(NotificationSkip.NO_PLACE) // không đăng, không tải
        val snapshot = weather.snapshotForNotification(WeatherPlaces.CURRENT, fetchTimeoutMs)
            ?: return NotificationPrep.Skip(NotificationSkip.NO_DATA)
        if (nowMillis - snapshot.fetchedAtMillis > WeatherNotificationConfig.MAX_STALE_CACHE_MS) {
            return NotificationPrep.Skip(NotificationSkip.NO_DATA)
        }
        val summary = WeatherForecastSummarizer.summarize(snapshot, kind, nowMillis)
            ?: return NotificationPrep.Skip(NotificationSkip.NO_DATA)
        return NotificationPrep.Ready(summary)
    }
}
