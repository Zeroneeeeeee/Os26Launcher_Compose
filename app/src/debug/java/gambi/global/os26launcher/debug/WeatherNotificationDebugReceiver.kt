package gambi.global.os26launcher.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.data.weather.notify.AlarmWeatherNotificationScheduler
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationKind
import gambi.global.os26launcher.domain.weather.WeatherNotificationIntents
import gambi.global.os26launcher.domain.weather.WeatherNotificationPlanner
import gambi.global.os26launcher.domain.weather.WeatherPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * WN — CHỈ BẢN DEBUG (src/debug, không vào release). Công cụ để điều phối kiểm lịch thông báo mà không đợi 07:00/21:00; bảo vệ bằng
 * `android:permission=DUMP` như CP. Mọi lệnh có `--es kind DAILY|TOMORROW` (trừ STATE). Kết quả in ở logcat tag `WN` (chỉ tên loại, số, giờ nổ; không dữ liệu cá nhân).
 *
 * - `WN_DEBUG_FIRE`: chạy đúng đường báo thức nổ NGAY (gửi `WN_FIRE` thật tới receiver sản xuất: đặt báo thức kế, xếp job).
 * - `WN_DEBUG_SHIFT --ei minutes N`: dời báo thức của loại đó sang now+N phút (báo thức nổ thật qua AlarmManager). Lưu ý: mở lại app hoặc
 *   sự kiện đặt-lại-lịch khác sẽ đặt lại về 07:00/21:00; muốn giữ thì đừng mở app trong lúc chờ.
 * - `WN_DEBUG_ENABLE --ez enabled true|false`: bật/tắt công tắc (Task 5 chưa có UI) rồi đặt lại lịch.
 * - `WN_DEBUG_STATE`: log công tắc, ngày đã đăng, và giờ nổ SẼ được đặt theo planner (không đọc lại AlarmManager: dùng `dumpsys alarm` cho việc đó).
 */
class WeatherNotificationDebugReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun alarmScheduler(): AlarmWeatherNotificationScheduler

        fun debugPrefs(): WeatherPreferencesRepository

        @ApplicationScope
        fun debugAppScope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val scheduler = deps.alarmScheduler()
        val prefs = deps.debugPrefs()
        val appScope = deps.debugAppScope()
        val action = intent.action ?: return
        val kind = WeatherNotificationIntents.kindOf(intent.getStringExtra(WeatherNotificationIntents.EXTRA_KIND))
        val pending = goAsync()
        val appContext = context.applicationContext
        appScope.launch {
            try {
                when (action) {
                    ACTION_FIRE -> if (kind != null) {
                        appContext.sendBroadcast(
                            Intent(WeatherNotificationIntents.ACTION_FIRE).setPackage(appContext.packageName)
                                .putExtra(WeatherNotificationIntents.EXTRA_KIND, kind.name)
                                // Mặc định ép đăng (bỏ kiểm giờ/ngày đã đăng) để thử bất kỳ lúc nào; `--ez force false` để thử đúng đường sản xuất.
                                .putExtra(WeatherNotificationIntents.EXTRA_FORCE, intent.getBooleanExtra("force", true))
                        )
                        Log.i(TAG, "debug fire sent: ${kind.name}")
                    }

                    ACTION_SHIFT -> if (kind != null) {
                        val minutes = intent.getIntExtra("minutes", 1)
                        scheduler.armAt(kind, System.currentTimeMillis() + minutes * 60_000L)
                        Log.i(TAG, "debug shift: ${kind.name} +${minutes}min")
                    }

                    ACTION_ENABLE -> if (kind != null) {
                        val enabled = intent.getBooleanExtra("enabled", true)
                        prefs.setNotificationEnabled(kind, enabled)
                        scheduler.rearm()
                        Log.i(TAG, "debug enable: ${kind.name}=$enabled")
                    }

                    ACTION_STATE -> logState(prefs)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "debug failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun logState(prefs: WeatherPreferencesRepository) {
        val s = prefs.notificationSettings()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        Log.i(
            TAG,
            "state: daily=${s.daily} tomorrow=${s.tomorrow} zone=${zone.id} jvmDefault=${java.util.TimeZone.getDefault().id} " +
                "nowLocal=${fmt.format(Instant.ofEpochMilli(now).atZone(zone))}"
        )
        for (kind in WeatherNotificationKind.entries) {
            val shown = prefs.lastShownDay(kind)
            val next = WeatherNotificationPlanner.nextTrigger(kind, now, zone, shown)
            Log.i(TAG, "state: ${kind.name} zone=${zone.id} shown=$shown next=${fmt.format(Instant.ofEpochMilli(next).atZone(zone))}")
        }
    }

    private companion object {
        const val TAG = "WN"
        const val ACTION_FIRE = "gambi.global.os26launcher.WN_DEBUG_FIRE"
        const val ACTION_SHIFT = "gambi.global.os26launcher.WN_DEBUG_SHIFT"
        const val ACTION_ENABLE = "gambi.global.os26launcher.WN_DEBUG_ENABLE"
        const val ACTION_STATE = "gambi.global.os26launcher.WN_DEBUG_STATE"
    }
}
