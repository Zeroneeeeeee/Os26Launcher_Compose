package gambi.global.os26launcher.ui.home.compose

import android.graphics.Bitmap
import android.graphics.drawable.Animatable2
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.glass.BlurredBackdropCache
import gambi.global.os26launcher.core.glass.compose.FrostedWallpaperImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * HH — màn "hello" khi mở Home bằng "Trải nghiệm" / "Khám phá ›" ([gambi.global.os26launcher.ui.home.HomeEntry]). Phủ kín Home (chặn chạm)
 * trong lúc Home dựng và nạp icon / ảnh nền phía dưới, nên người dùng không thấy lưới hiện dần.
 *
 * Màn này chạy CÙNG LÚC với lúc Home dựng (khung main thread 100 ms ở bản `perf`, 1–1,5 s ở bản debug), nên phần chuyển động không được
 * phụ thuộc main thread:
 * - Chữ: "hello" viết tay, nét vẽ dần, là `AnimatedVectorDrawable` (`R.drawable.avd_hello`, trimPathEnd) trong một ImageView: AVD chạy trên
 *   RenderThread, main thread bận vẫn mượt. (Bản đầu vẽ bằng Compose Canvas + Animatable: đứng hình theo các khung dựng Home.)
 * - Nền: ảnh nền Home đã làm mờ SẴN ([FrostedWallpaperImage], bản mờ của `GlassEnvironment.blurCache` mà Cài đặt đã làm nóng) + lớp tối nhẹ;
 *   không `Modifier.blur` (làm mờ lại cả màn mỗi khung trên GPU). Chưa có bản mờ thì `ds_bg_base`.
 * - Thoát: khi Home đã hiện đủ ([homeReady]) VÀ đã đủ [HELLO_MIN_MS] thì cả lớp mờ đi, chữ phóng nhẹ (main thread, lúc đó Home đã dựng xong).
 *   Home chậm quá [HELLO_MAX_MS] thì vẫn mờ đi. Xong gọi [onFinished] (gỡ lớp).
 */
@Composable
fun HomeHello(
    backdrop: Bitmap?,
    homeReady: () -> Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Gọi khi chữ viết xong: từ đây Home phía dưới phải được vẽ lại (trước đó Activity ẩn hẳn nó để RenderThread rảnh cho nét viết). Vẽ lại
     * trong nhịp dừng trước khi mờ đi, để lần vẽ đầu của Home (tải texture icon, ảnh nền) không rơi vào khung đầu của hiệu ứng mờ.
     */
    onUncover: () -> Unit = {}
) {
    val exit = remember { Animatable(0f) } // 0..1: mờ đi
    val ready by rememberUpdatedState(homeReady)
    val finished by rememberUpdatedState(onFinished)
    val uncover by rememberUpdatedState(onUncover)
    LaunchedEffect(Unit) {
        val t0 = SystemClock.uptimeMillis()
        delay(HELLO_WRITE_MS)
        uncover()
        delay(HELLO_MIN_MS - HELLO_WRITE_MS)
        withTimeoutOrNull(HELLO_MAX_MS - HELLO_MIN_MS) {
            snapshotFlow { ready() }.first { it }
            // Chờ main thread RẢNH rồi mới mờ đi (hiệu ứng mờ chạy trên main thread): [HELLO_QUIET_FRAMES] khung liền nhau, mỗi khung cách
            // khung trước ≤ [HELLO_QUIET_FRAME_NS]. Đo A17 bản debug 2026-11-26: Home "đủ" rồi mà icon các trang khác còn về tiếp, mỗi đợt
            // dựng lại hàng loạt ô (khung 170–1073 ms) tới ~3,7 s; mờ theo giờ cố định thì rơi đúng vào đó.
            var quiet = 0
            var last = withFrameNanos { it }
            while (quiet < HELLO_QUIET_FRAMES) {
                val now = withFrameNanos { it }
                quiet = if (now - last <= HELLO_QUIET_FRAME_NS) quiet + 1 else 0
                last = now
            }
        }
        Log.d(TAG, "exit after ${SystemClock.uptimeMillis() - t0} ms, ready=${ready()}")
        exit.animateTo(1f, tween(HELLO_EXIT_MS, easing = FastOutSlowInEasing))
        finished()
    }
    val label = stringResource(R.string.home_hello)

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(colorResource(R.color.ds_bg_base))
            // Chặn chạm xuống Home đang ẩn phía dưới (hit test dừng ở anh em trên cùng).
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .semantics { contentDescription = label }
    ) {
        FrostedWallpaperImage(backdrop, Modifier.fillMaxSize(), cache = HelloBlur.cache)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = HELLO_SCRIM)))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = min(maxWidth * HELLO_WIDTH_FRACTION, HELLO_MAX_WIDTH)
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(context.getDrawable(R.drawable.avd_hello))
                        // Chạy sau khi gắn vào cửa sổ: AVD vẽ trên canvas phần cứng thì chạy ở RenderThread.
                        post { (drawable as? Animatable2)?.start() }
                    }
                },
                modifier = Modifier
                    .align(HELLO_ALIGNMENT)
                    .size(w, w * HELLO_ASPECT)
                    .graphicsLayer {
                        val s = 1f + HELLO_EXIT_SCALE * exit.value
                        scaleX = s
                        scaleY = s
                    }
            )
        }
    }
}

private const val TAG = "HomeHello"

/**
 * Bản mờ ảnh nền RIÊNG của màn chào: mờ mạnh (như màn chào kiểu iOS), không theo thanh "Độ mờ" của kính (đo A17 2026-11-26: bản của kính ở mức
 * mặc định gần như vẫn nét). Một bộ đệm cho cả process; [gambi.global.os26launcher.ui.home.HomeWarmup] làm nóng ở Cài đặt nên màn chào có ngay nền mờ ở khung đầu.
 */
internal object HelloBlur {
    val cache: BlurredBackdropCache by lazy { BlurredBackdropCache().apply { blurRadiusPx = HELLO_BLUR_RADIUS_PX } }
}

/** Bán kính box blur (3 lượt) trên bản nhỏ 25 % cỡ màn: 10 px nhỏ ≈ 40 px màn. */
private const val HELLO_BLUR_RADIUS_PX = 10
private const val HELLO_ASPECT = 200f / 440f // khung của avd_hello (viewport 440 × 200)
private const val HELLO_WIDTH_FRACTION = 0.68f
private val HELLO_MAX_WIDTH = 340.dp
private val HELLO_ALIGNMENT = BiasAlignment(0f, -0.08f) // hơi cao hơn tâm màn

private const val HELLO_SCRIM = 0.25f

/** = startOffset + duration của objectAnimator trong `avd_hello.xml`. */
private const val HELLO_WRITE_MS = 150L + 1800L
private const val HELLO_EXIT_MS = 500
private const val HELLO_EXIT_SCALE = 0.06f

/** Hiện ít nhất chừng này (kể cả Home đã sẵn): viết xong chữ rồi dừng một nhịp. */
private const val HELLO_MIN_MS = HELLO_WRITE_MS + 450L

/** Home chưa sẵn / chưa rảnh sau chừng này thì vẫn mờ đi. */
private const val HELLO_MAX_MS = 5000L

private const val HELLO_QUIET_FRAMES = 6
private const val HELLO_QUIET_FRAME_NS = 34_000_000L // ~2 vsync ở 60 Hz
