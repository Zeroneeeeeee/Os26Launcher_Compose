package gambi.global.os26launcher.domain.weather

import gambi.global.os26launcher.domain.model.weather.WeatherNotificationConfig
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * WN — tính thời điểm nổ tiếp theo của một loại thông báo. Thuần (không `android.*`), dùng `java.time` để đúng khi đổi múi giờ hay giờ nhảy mùa hè.
 *
 * Quy tắc (plan D7):
 * - Hôm nay đã đăng ([lastShownDay] == ngày hôm nay): lần kế là mục tiêu của NGÀY MAI.
 * - Chưa đăng, và `now` đã qua mục tiêu hôm nay nhưng trong [WeatherNotificationConfig.GRACE_MS]: trả `now` (bù thông báo bị lỡ).
 * - `now` chưa tới mục tiêu hôm nay: mục tiêu hôm nay.
 * - Còn lại (qua mục tiêu + ân hạn): mục tiêu ngày mai.
 *
 * Ví dụ (UTC+7, DAILY 07:00): 06:00 chưa đăng -> 07:00 hôm nay; 07:20 chưa đăng -> ngay; 07:20 đã đăng -> 07:00 ngày mai;
 * 11:00 chưa đăng (quá ân hạn 3 giờ) -> 07:00 ngày mai. Giờ mục tiêu không tồn tại (nhảy mùa hè) -> `java.time` đẩy sang giờ hợp lệ gần nhất.
 */
object WeatherNotificationPlanner {

    /** Mã ngày `yyyy-MM-dd` theo [zone]; dùng làm `lastShownDay`. */
    fun dayKey(nowMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toString()

    /** Mili giây epoch của thời điểm đặt báo thức (có thể bằng [nowMillis]: nổ ngay). */
    fun nextTrigger(
        kind: WeatherNotificationKind,
        nowMillis: Long,
        zone: ZoneId,
        lastShownDay: String?
    ): Long {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val targetToday = targetMillis(kind, today, zone)
        val shownToday = lastShownDay == today.toString()
        return when {
            shownToday -> targetMillis(kind, today.plusDays(1), zone)
            nowMillis < targetToday -> targetToday
            nowMillis - targetToday <= WeatherNotificationConfig.GRACE_MS -> nowMillis
            else -> targetMillis(kind, today.plusDays(1), zone)
        }
    }

    /** Còn trong thời gian đăng của hôm nay không (dùng ở receiver/job: không đăng bản 07:00 lúc 15:00). */
    fun withinWindow(kind: WeatherNotificationKind, nowMillis: Long, zone: ZoneId): Boolean {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val age = nowMillis - targetMillis(kind, today, zone)
        return age in 0..WeatherNotificationConfig.GRACE_MS
    }

    private fun targetMillis(kind: WeatherNotificationKind, day: LocalDate, zone: ZoneId): Long =
        day.atTime(kind.hour, kind.minute).atZone(zone).toInstant().toEpochMilli()
}
