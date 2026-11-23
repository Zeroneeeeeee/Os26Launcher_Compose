package gambi.global.os26launcher.ui.weather.notify

import gambi.global.os26launcher.domain.model.weather.NotificationPrep
import gambi.global.os26launcher.domain.model.weather.NotificationSkip
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationKind
import gambi.global.os26launcher.domain.usecase.PrepareWeatherNotificationUseCase
import gambi.global.os26launcher.domain.weather.WeatherNotificationPlanner
import gambi.global.os26launcher.domain.weather.WeatherPreferencesRepository
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WN — một lượt "báo thức nổ -> đăng thông báo", dùng chung cho receiver (ngay lúc nổ, trong `goAsync`, chờ dữ liệu tối đa vài giây) và
 * [WeatherNotificationJobService] (thử lại khi có mạng). Thứ tự: công tắc còn bật? -> hệ thống cho đăng (thông báo + kênh)? -> use case (cache hoặc
 * tải tối đa một lần theo quy tắc 2 giờ) -> đăng -> ghi "đã đăng hôm nay" (trừ [force], chỉ gỡ lỗi). Không log nội dung.
 */
@Singleton
class WeatherNotificationDispatcher @Inject constructor(
    private val prepare: PrepareWeatherNotificationUseCase,
    private val notifier: WeatherNotifier,
    private val prefs: WeatherPreferencesRepository
) {
    enum class Outcome {
        SHOWN, OFF, BLOCKED, SKIPPED,

        /** Không có dữ liệu dùng được: đáng thử lại bằng job khi có mạng. */
        RETRY
    }

    suspend fun run(kind: WeatherNotificationKind, force: Boolean, fetchTimeoutMs: Long): Outcome {
        if (!prefs.notificationSettings().isEnabled(kind)) return Outcome.OFF
        if (!notifier.canPost(kind)) return Outcome.BLOCKED // chặn thì KHÔNG tải: không tốn lệnh API
        val now = System.currentTimeMillis()
        return when (val prep = prepare(kind, now, force, fetchTimeoutMs)) {
            is NotificationPrep.Ready -> {
                notifier.show(prep.summary, prefs.unit.first())
                if (!force) prefs.markShown(kind, WeatherNotificationPlanner.dayKey(now, ZoneId.systemDefault()))
                Outcome.SHOWN
            }

            is NotificationPrep.Skip -> if (prep.reason == NotificationSkip.NO_DATA) Outcome.RETRY else Outcome.SKIPPED
        }
    }
}
