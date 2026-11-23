package gambi.global.os26launcher.data.weather.notify

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationConfig
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationKind
import gambi.global.os26launcher.domain.weather.WeatherNotificationIntents
import gambi.global.os26launcher.domain.weather.WeatherNotificationPlanner
import gambi.global.os26launcher.domain.weather.WeatherNotificationScheduler
import gambi.global.os26launcher.domain.weather.WeatherPreferencesRepository
import gambi.global.os26launcher.domain.weather.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WN Task 3 — lịch bằng `AlarmManager` (plan D1, phương án B): MỘT báo thức mỗi loại, `RTC_WAKEUP`, `setAndAllowWhileIdle` (không chính xác, nổ cả khi Doze,
 * từ Android 12 có thể trễ tới ~1 giờ, đọc tài liệu). Hằng [WeatherNotificationConfig.USE_EXACT_ALARMS] đổi sang báo thức chính xác (cần thêm quyền).
 * `PendingIntent` tường minh theo gói (`setPackage`), `FLAG_IMMUTABLE`, request code theo loại: đặt lại thì thay, không nhân đôi.
 * Không log giờ nổ cụ thể ngoài tên loại (không có dữ liệu cá nhân nào ở đây, nhưng giữ log ít).
 */
@Singleton
class AlarmWeatherNotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: WeatherPreferencesRepository,
    private val weather: WeatherRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : WeatherNotificationScheduler {

    private val alarmManager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    override suspend fun rearm() = guarded {
        val settings = prefs.notificationSettings()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        for (kind in WeatherNotificationKind.entries) {
            if (settings.isEnabled(kind) && weather.hasKey) {
                armAt(kind, WeatherNotificationPlanner.nextTrigger(kind, now, zone, prefs.lastShownDay(kind)))
            } else {
                cancel(kind)
            }
        }
    }

    override suspend fun rearmAfterFire(kind: WeatherNotificationKind) = guarded {
        if (!prefs.notificationSettings().isEnabled(kind) || !weather.hasKey) {
            cancel(kind)
            return@guarded
        }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        // Coi như hôm nay đã xử lý: lần kế là mục tiêu NGÀY MAI (nếu để lastShownDay thật thì trong ân hạn nó trả "ngay" và báo thức nổ lặp).
        armAt(kind, WeatherNotificationPlanner.nextTrigger(kind, now, zone, WeatherNotificationPlanner.dayKey(now, zone)))
    }

    /**
     * Đặt báo thức của [kind] nổ lúc [triggerMillis] (epoch ms; quá khứ thì nổ ngay). Công khai để công cụ gỡ lỗi của bản debug dời giờ nổ
     * (`WN_DEBUG_SHIFT`); mã sản xuất chỉ đi qua [rearm]/[rearmAfterFire].
     */
    @SuppressLint("ScheduleExactAlarm") // chỉ chạy khi USE_EXACT_ALARMS=true và đã có quyền (canScheduleExactAlarms)
    fun armAt(kind: WeatherNotificationKind, triggerMillis: Long) {
        val pi = pendingIntent(kind)
        val am = alarmManager
        val exact = WeatherNotificationConfig.USE_EXACT_ALARMS &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms())
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        }
    }

    private fun cancel(kind: WeatherNotificationKind) {
        alarmManager.cancel(pendingIntent(kind))
    }

    private fun pendingIntent(kind: WeatherNotificationKind): PendingIntent {
        val intent = Intent(WeatherNotificationIntents.ACTION_FIRE)
            .setPackage(context.packageName) // tường minh theo gói: receiver không xuất ra ngoài vẫn nhận được
            .putExtra(WeatherNotificationIntents.EXTRA_KIND, kind.name)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Hợp đồng: không ném (lỗi lạ chỉ log tên lớp); chạy trên IO. */
    private suspend inline fun guarded(crossinline block: suspend () -> Unit) {
        try {
            withContext(io) { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "schedule failed: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "WN"
        const val REQUEST_CODE_BASE = 7100
    }
}
