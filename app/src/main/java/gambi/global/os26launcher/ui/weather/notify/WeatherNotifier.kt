package gambi.global.os26launcher.ui.weather.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.locale.AppLocaleContext
import gambi.global.os26launcher.domain.model.weather.ForecastSummary
import gambi.global.os26launcher.domain.model.weather.TemperatureUnit
import gambi.global.os26launcher.domain.model.weather.WeatherCondition
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationKind
import gambi.global.os26launcher.ui.home.HomeActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WN Task 4 — kênh và việc đăng thông báo thời tiết (plan D5/D6). Kênh tạo LƯỜI (lúc bật công tắc / lúc đăng), không ở `Application.onCreate`.
 * Chạm thông báo mở Home (intent tường minh, `singleTask` đưa Home lên trước; không sửa Home). Màn hình khóa chỉ hiện bản công khai không có tên
 * địa điểm. Không log nội dung.
 */
@Singleton
class WeatherNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** LG: chữ của thông báo và tên kênh theo ngôn ngữ app (API < 33 context ứng dụng theo ngôn ngữ máy). */
    private val strings: Context get() = AppLocaleContext.wrap(context)

    private val manager: NotificationManager get() = context.getSystemService(NotificationManager::class.java)

    /** Idempotent. Mức quan trọng không đổi được bằng code sau khi kênh đã tạo (chỉ người dùng đổi): đổi [CHANNEL_IMPORTANCE] cần đổi id kênh. */
    fun ensureChannels() {
        val nm = manager
        nm.createNotificationChannelGroup(NotificationChannelGroup(GROUP_ID, strings.getString(R.string.wn_channel_group)))
        for (kind in WeatherNotificationKind.entries) {
            val channel = NotificationChannel(channelId(kind), strings.getString(channelName(kind)), CHANNEL_IMPORTANCE)
            channel.group = GROUP_ID
            nm.createNotificationChannel(channel)
        }
    }

    /** Thông báo của app đang bật VÀ kênh của [kind] không bị chặn. Gọi TRƯỚC khi tải để không tốn lệnh API cho thông báo không hiện được. */
    fun canPost(kind: WeatherNotificationKind): Boolean {
        ensureChannels()
        val nm = manager
        if (!nm.areNotificationsEnabled()) return false
        val channel = nm.getNotificationChannel(channelId(kind))
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(summary: ForecastSummary, unit: TemperatureUnit) {
        ensureChannels()
        val kind = summary.kind
        val symbol = strings.getString(
            if (unit == TemperatureUnit.FAHRENHEIT) R.string.settings_weather_unit_value_f else R.string.settings_weather_unit_value_c
        )
        val title = strings.getString(
            if (kind == WeatherNotificationKind.DAILY) R.string.wn_title_today else R.string.wn_title_tomorrow,
            summary.placeName
        )
        val text = strings.getString(
            R.string.wn_text,
            strings.getString(conditionText(summary.condition)),
            unit.display(summary.highC),
            unit.display(summary.lowC),
            symbol
        )
        val start = summary.precipStartMinute
        val end = summary.precipEndMinute
        val big = if (start != null && end != null) {
            text + "\n" + strings.getString(R.string.wn_precip, clock(start), clock(end))
        } else {
            text
        }
        val publicVersion = Notification.Builder(context, channelId(kind))
            .setSmallIcon(R.drawable.ic_stat_weather)
            .setContentTitle(strings.getString(R.string.wn_public_title))
            .setContentText(
                strings.getString(
                    if (kind == WeatherNotificationKind.DAILY) R.string.wn_public_today else R.string.wn_public_tomorrow
                )
            )
            .build()
        val notification = Notification.Builder(context, channelId(kind))
            .setSmallIcon(R.drawable.ic_stat_weather)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(big))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(homeIntent(kind))
            .build()
        manager.notify(NOTIFICATION_ID_BASE + kind.ordinal, notification) // id cố định mỗi loại: đăng lại thì thay bản cũ
    }

    /** Mở Home như nhấn nút Home (`singleTask`); Home tự đóng lớp tạm (HB). Không thêm extra: không sửa HomeActivity (độc quyền G3c). */
    private fun homeIntent(kind: WeatherNotificationKind): PendingIntent {
        val intent = Intent(context, HomeActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_BASE + kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun clock(minuteOfDay: Int): String {
        val m = minuteOfDay % (24 * 60)
        return String.format(java.util.Locale.ROOT, "%02d:%02d", m / 60, m % 60)
    }

    private fun conditionText(c: WeatherCondition): Int = when (c) {
        WeatherCondition.CLEAR -> R.string.weather_cond_clear
        WeatherCondition.PARTLY_CLOUDY -> R.string.weather_cond_partly_cloudy
        WeatherCondition.CLOUDY -> R.string.weather_cond_cloudy
        WeatherCondition.RAIN -> R.string.weather_cond_rain
        WeatherCondition.THUNDERSTORM -> R.string.weather_cond_thunderstorm
        WeatherCondition.SNOW -> R.string.weather_cond_snow
        WeatherCondition.FOG -> R.string.weather_cond_fog
        WeatherCondition.UNKNOWN -> R.string.weather_cond_unknown
    }

    private fun channelId(kind: WeatherNotificationKind) = when (kind) {
        WeatherNotificationKind.DAILY -> "weather_daily"
        WeatherNotificationKind.TOMORROW -> "weather_tomorrow"
    }

    private fun channelName(kind: WeatherNotificationKind) = when (kind) {
        WeatherNotificationKind.DAILY -> R.string.wn_channel_daily
        WeatherNotificationKind.TOMORROW -> R.string.wn_channel_tomorrow
    }

    companion object {
        /** Mức quan trọng của kênh (chủ dự án chốt 2026-11-13: LOW = im lặng). MỘT chỗ để đổi. */
        const val CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_LOW

        private const val GROUP_ID = "weather"
        private const val NOTIFICATION_ID_BASE = 7300
    }
}
