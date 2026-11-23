package gambi.global.os26launcher.ui.weather.notify

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationConfig
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationKind
import java.util.TimeZone
import gambi.global.os26launcher.domain.weather.WeatherNotificationIntents
import gambi.global.os26launcher.domain.weather.WeatherNotificationScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * WN Task 3 — điểm vào của lịch thông báo thời tiết (plan D1/D2/D7). Một receiver nhận:
 * - [WeatherNotificationIntents.ACTION_FIRE] (báo thức nổ): đặt báo thức kế tiếp TRƯỚC, rồi xếp job lấy dữ liệu ([enqueueJob]);
 * - `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`: đặt lại toàn bộ lịch.
 * `onReceive` chạy trên luồng chính và phải trả về nhanh: việc ghi lịch chạy ở `ApplicationScope` với `goAsync()` (xong dưới 10 giây, đọc tài liệu);
 * việc dài (mạng) nằm ở [WeatherNotificationJobService]. Log chỉ có tên hành động rút gọn và mã loại.
 */
class WeatherNotificationReceiver : BroadcastReceiver() {

    /** Lấy phụ thuộc bằng EntryPoint: Kotlin không gọi được `super.onReceive` của receiver Hilt (abstract) nên không dùng `@AndroidEntryPoint` ở đây. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun notificationScheduler(): WeatherNotificationScheduler

        @ApplicationScope
        fun notificationAppScope(): CoroutineScope

        fun notificationDispatcher(): WeatherNotificationDispatcher
    }

    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val scheduler = deps.notificationScheduler()
        val appScope = deps.notificationAppScope()
        val dispatcher = deps.notificationDispatcher()
        val action = intent.action ?: return
        val kind = WeatherNotificationIntents.kindOf(intent.getStringExtra(WeatherNotificationIntents.EXTRA_KIND))
        val pending = goAsync()
        val appContext = context.applicationContext
        Log.i(TAG, "recv:${action.substringAfterLast('.')}${kind?.let { ":" + it.name } ?: ""}")
        appScope.launch {
            try {
                when {
                    action == WeatherNotificationIntents.ACTION_FIRE && kind != null -> {
                        // Đường chính (plan D2c): đăng NGAY trong goAsync (cache, hoặc một lần tải chờ tối đa vài giây). Job bị hoãn lúc màn hình tắt
                        // (đo được trên Live 4), nên chỉ xếp job làm đường lùi khi chưa có dữ liệu dùng được.
                        val force = intent.getBooleanExtra(WeatherNotificationIntents.EXTRA_FORCE, false)
                        scheduler.rearmAfterFire(kind)
                        val outcome = dispatcher.run(kind, force, WeatherNotificationConfig.RECEIVER_FETCH_TIMEOUT_MS)
                        Log.i(TAG, "result:${kind.name}:${outcome.name.lowercase()}")
                        if (outcome == WeatherNotificationDispatcher.Outcome.RETRY) enqueueJob(appContext, kind, force)
                    }

                    action in REARM_ACTIONS -> {
                        // Tiến trình có thể còn giữ múi giờ cũ nếu hệ thống chưa cập nhật mặc định của JVM (không chắc, chưa đo): đọc lại cho chắc.
                        if (action == Intent.ACTION_TIMEZONE_CHANGED) TimeZone.setDefault(null)
                        scheduler.rearm()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "receive failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "WN"

        /** Job id = cơ số + thứ tự loại (khác request code của báo thức cho dễ đọc `dumpsys`). */
        const val JOB_ID_BASE = 7200

        /** Hạn chót của job: hết hạn thì chạy dù chưa có mạng (dùng cache). */
        private const val JOB_DEADLINE_MS = 20 * 60_000L

        private val REARM_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, // "android.intent.action.TIME_SET"
            Intent.ACTION_TIMEZONE_CHANGED
        )

        /** Xếp job lấy dữ liệu cho [kind]: có mạng thì chạy, không thì chờ tới hạn chót. Cùng id thì thay job cũ. */
        fun enqueueJob(context: Context, kind: WeatherNotificationKind, force: Boolean = false) {
            val extras = PersistableBundle().apply {
                putString(WeatherNotificationIntents.EXTRA_KIND, kind.name)
                putBoolean(WeatherNotificationIntents.EXTRA_FORCE, force)
            }
            val info = JobInfo.Builder(JOB_ID_BASE + kind.ordinal, ComponentName(context, WeatherNotificationJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(JOB_DEADLINE_MS)
                .setExtras(extras)
                .build()
            val result = context.getSystemService(JobScheduler::class.java).schedule(info)
            if (result != JobScheduler.RESULT_SUCCESS) Log.w(TAG, "job schedule failed: ${kind.name}")
        }
    }
}
