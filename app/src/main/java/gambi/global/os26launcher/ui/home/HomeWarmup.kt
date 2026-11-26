package gambi.global.os26launcher.ui.home

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.core.glass.GlassEnvironment
import gambi.global.os26launcher.core.perf.PerfSwitches
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.ui.home.compose.HelloBlur
import gambi.global.os26launcher.domain.model.HomeBackdrop
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.repository.BackdropRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
 * Icon: nạp sẵn vào cache RAM các icon màn đầu của Home (trang hiện + dock; Home ghi danh sách qua [rememberFirstScreenIcons]), để Home dựng
 * mới hiện đủ icon ngay khung đầu thay vì từng icon một.
 *
 * Gọi ở `onResume` của trang chủ Cài đặt; chạy SAU khung kế tiếp để không tranh với khung đầu của Cài đặt. Đã có sẵn (Home vừa dùng, lần
 * resume trước đã làm) thì gần như không tốn gì.
 */
@Singleton
class HomeWarmup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backdrop: BackdropRepository,
    private val glass: GlassEnvironment,
    private val apps: AppRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope
) {
    // Bản mã hóa lần ghi gần nhất trong process: Home dựng lại với cùng màn đầu thì không ghi lại.
    @Volatile
    private var lastSavedIcons: String? = null

    fun warmAfterNextFrame(activity: ComponentActivity) {
        if (PerfSwitches.noHomeWarmup) return
        Choreographer.getInstance().postFrameCallback {
            activity.window.decorView.post {
                warmIcons(activity)
                warm(activity)
            }
        }
    }

    /** Home gọi lúc vừa dựng với icon (khóa, cỡ px) màn đầu; ghi nền vào SharedPreferences cho lần sau (kể cả process mới). */
    fun rememberFirstScreenIcons(icons: List<Pair<ComponentKey, Int>>) {
        val encoded = icons.joinToString(LINE_SEP) { (k, px) -> listOf(k.userSerial, px, k.packageName, k.className).joinToString(FIELD_SEP) }
        if (encoded == lastSavedIcons) return
        lastSavedIcons = encoded
        appScope.launch(io) {
            try {
                prefs().edit().putString(KEY_ICONS, encoded).apply()
            } catch (e: Exception) {
                Log.w(TAG, "save first-screen icons failed ${e.javaClass.name}")
            }
        }
    }

    private fun warmIcons(activity: ComponentActivity) {
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        activity.lifecycleScope.launch {
            val wanted = withContext(io) { readIcons() }
            if (wanted.isEmpty()) return@launch
            // Khóa cache icon gồm dấu APK (sourceDir), chỉ có sau khi danh sách app đã nạp: nạp trước đó là sai khóa, Home không trúng.
            withTimeoutOrNull(APPS_WAIT_MS) { apps.apps.first { it.isNotEmpty() } } ?: return@launch
            // AppRepository.icon chạy trên luồng IO, lỗi trả null (không ném): app đã gỡ thì bỏ qua. Đã trong RAM thì gần như không tốn gì.
            wanted.map { (key, px) -> async { apps.icon(key, px) } }.awaitAll()
        }
    }

    private fun readIcons(): List<Pair<ComponentKey, Int>> = try {
        prefs().getString(KEY_ICONS, null).orEmpty().split(LINE_SEP).mapNotNull { line ->
            val f = line.split(FIELD_SEP)
            if (f.size != 4) return@mapNotNull null
            val user = f[0].toLongOrNull() ?: return@mapNotNull null
            val px = f[1].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            ComponentKey(f[2], f[3], user) to px
        }
    } catch (e: Exception) {
        Log.w(TAG, "read first-screen icons failed ${e.javaClass.name}")
        emptyList()
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
            HelloBlur.cache.prewarm(ready.bitmap, w, h) // HH: nền mờ của màn chào
        }
    }

    companion object {
        private const val TAG = "HomeWarmup"
        private const val PREFS_NAME = "home_warmup"
        private const val KEY_ICONS = "first_screen_icons"
        private const val LINE_SEP = "\n"
        private const val FIELD_SEP = "\t"

        /** Chờ danh sách app nạp xong tối đa chừng này rồi thôi (không nạp icon sai khóa). */
        private const val APPS_WAIT_MS = 5000L

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
