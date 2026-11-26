package gambi.global.os26launcher.ui.home

import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import gambi.global.os26launcher.core.glass.GlassEnvironment
import gambi.global.os26launcher.core.perf.PerfSwitches
import gambi.global.os26launcher.domain.model.HomeBackdrop
import gambi.global.os26launcher.domain.repository.BackdropRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HW — làm nóng màn Home khi đang ở Cài đặt, để bấm "Trải nghiệm" / "Khám phá ›" ([HomeEntry]) không còn khựng và đen màn.
 *
 * Đo trước HW (bản `perf`, Samsung A17, 2026-11-26, mở app từ icon rồi bấm "Trải nghiệm"): `HomeActivity` dựng mới giải mã ảnh nền từ
 * đĩa ~550 ms (lưới hiện trên nền tối suốt lúc đó), ảnh nền tới thì một khung ~500 ms, rồi bản mờ của kính dựng xong thêm ~330 ms sau và
 * thêm một khung giật. Ở đây giải mã sẵn ảnh nền đúng cỡ Home (ô nhớ của [BackdropRepository.loadHomeBackdrop]) và dựng sẵn bản mờ
 * ([GlassEnvironment.blurCache], khóa theo đúng bitmap đó), nên Home dựng mới có cả hai ngay từ khung đầu.
 *
 * Gọi ở `onResume` của trang chủ Cài đặt; chạy SAU khung kế tiếp để không tranh với khung đầu của Cài đặt. Đã có sẵn (Home vừa dùng, lần
 * resume trước đã làm) thì gần như không tốn gì.
 */
@Singleton
class HomeWarmup @Inject constructor(
    private val backdrop: BackdropRepository,
    private val glass: GlassEnvironment
) {
    fun warmAfterNextFrame(activity: ComponentActivity) {
        if (PerfSwitches.noHomeWarmup) return
        Choreographer.getInstance().postFrameCallback {
            activity.window.decorView.post { warm(activity) }
        }
    }

    private fun warm(activity: ComponentActivity) {
        // Đã rời màn (hoặc chia đôi màn hình: cỡ cửa sổ Home sẽ khác, ô nhớ không trúng) thì thôi.
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || activity.isInMultiWindowMode) return
        val (w, h) = windowSize(activity)
        if (w <= 0 || h <= 0) return
        activity.lifecycleScope.launch {
            val ready = try {
                backdrop.loadHomeBackdrop(w, h) as? HomeBackdrop.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "warm backdrop failed ${e.javaClass.name}")
                null
            } ?: return@launch
            // Độ mờ ("Độ mờ" của kính) phải là giá trị đã lưu trước khi dựng: đổi độ mờ sau đó làm bộ đệm bỏ bản vừa dựng.
            glass.readConfig()
            glass.blurCache.prewarm(ready.bitmap, w, h)
        }
    }

    companion object {
        private const val TAG = "HomeWarmup"

        /**
         * Cỡ cửa sổ toàn màn, bằng cỡ gốc Compose của Home (tràn viền) và cỡ `LocalView` mà kính mờ dùng để xin bản mờ: lệch cỡ thì ô nhớ
         * không trúng và Home tự giải mã như trước, không sai hình. `HomeActivity` cũng dùng để gắn sẵn ảnh nền trước lần dựng đầu.
         */
        fun windowSize(activity: ComponentActivity): Pair<Int, Int> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = activity.windowManager.currentWindowMetrics.bounds
                return b.width() to b.height()
            }
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getRealMetrics(dm)
            return dm.widthPixels to dm.heightPixels
        }
    }
}
