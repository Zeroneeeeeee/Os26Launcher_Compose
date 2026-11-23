package gambi.global.os26launcher.ui.weather.notify

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationConfig
import gambi.global.os26launcher.domain.weather.WeatherNotificationIntents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * WN Task 3/4/fix — job THỬ LẠI khi lúc báo thức nổ chưa có dữ liệu dùng được (không cache, chưa có mạng): receiver xếp job này (cần mạng, hạn chót 20
 * phút). Đo được trên Live 4: job chạy khi màn hình tắt có thể bị hoãn tới lúc bật màn hình, nên đường chính là receiver đăng ngay trong `goAsync`
 * (plan D2, phương án c), job chỉ là đường lùi. Dùng cùng [WeatherNotificationDispatcher]; chờ dữ liệu tối đa
 * [WeatherNotificationConfig.JOB_FETCH_TIMEOUT_MS]. `onStopJob` hủy coroutine và KHÔNG xin xếp lại. Log: kết quả và mã loại.
 */
@AndroidEntryPoint
class WeatherNotificationJobService : JobService() {

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    @Inject
    lateinit var dispatcher: WeatherNotificationDispatcher

    private val running = ConcurrentHashMap<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        val kind = WeatherNotificationIntents.kindOf(params.extras.getString(WeatherNotificationIntents.EXTRA_KIND)) ?: return false
        val force = params.extras.getBoolean(WeatherNotificationIntents.EXTRA_FORCE, false)
        val job = appScope.launch(start = CoroutineStart.LAZY) {
            try {
                Log.i(TAG, "job:${kind.name}")
                val outcome = dispatcher.run(kind, force, WeatherNotificationConfig.JOB_FETCH_TIMEOUT_MS)
                Log.i(TAG, "result:${kind.name}:${outcome.name.lowercase()} (job)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "job failed: ${e.javaClass.simpleName}") // chỉ tên lớp: thông điệp có thể chứa URL/khóa
            } finally {
                if (running.remove(params.jobId) != null) jobFinished(params, false) // đã bị onStopJob hủy thì không báo xong lần nữa
            }
        }
        running[params.jobId] = job
        job.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return false
    }

    private companion object {
        const val TAG = "WN"
    }
}
