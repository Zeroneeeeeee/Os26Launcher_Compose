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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
 *   Kiểu chữ kính (nhiều lớp nét chồng nhau, xem `avd_hello.xml`).
 * - Home lâu sẵn: viết xong giữ [HELLO_HOLD_MS] rồi đổi lần lượt sang lời chào các thứ tiếng ([HELLO_GREETINGS], kiểu kính [GlassGreeting],
 *   quét hiện từ trái sang), mỗi câu giữ [HELLO_HOLD_MS]. Phần đổi chữ chạy main thread (lúc này Home thường đã qua đoạn dựng nặng).
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
    val greetings = remember(label) { helloGreetings(label) }

    // Đổi chữ: "hello" viết tay xong thì dừng [HELLO_HOLD_MS], mờ đi, hiện lời chào kế (tiếng của máy trước rồi các tiếng khác), lặp vòng tới
    // khi lớp mờ đi ở LaunchedEffect trên (Home lâu sẵn mới thấy). Phần này chạy main thread: lúc đó Home thường đã dựng xong phần nặng.
    var word by remember { mutableIntStateOf(-1) } // -1 = "hello" viết tay (AVD), còn lại = chỉ số trong [greetings]
    val wordIn = remember { Animatable(1f) } // 0..1: độ hiện của chữ đang có
    val reveal = remember { Animatable(1f) } // 0..1: quét hiện chữ từ trái sang
    LaunchedEffect(greetings) {
        delay(HELLO_WRITE_MS + HELLO_HOLD_MS)
        var i = 0
        while (true) {
            wordIn.animateTo(0f, tween(HELLO_WORD_OUT_MS, easing = FastOutSlowInEasing))
            word = i % greetings.size
            reveal.snapTo(0f)
            wordIn.snapTo(1f)
            reveal.animateTo(1f, tween(HELLO_WORD_IN_MS, easing = FastOutSlowInEasing))
            delay(HELLO_HOLD_MS)
            i++
        }
    }

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
            val wordModifier = Modifier
                .align(HELLO_ALIGNMENT)
                .size(w, w * HELLO_ASPECT)
                .graphicsLayer {
                    // Lúc chữ mờ đi để đổi chữ: phóng nhẹ thêm.
                    val s = 1f + HELLO_EXIT_SCALE * exit.value + HELLO_WORD_OUT_SCALE * (1f - wordIn.value)
                    scaleX = s
                    scaleY = s
                    alpha = wordIn.value
                }
            if (word < 0) {
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
                    modifier = wordModifier
                )
            } else {
                GlassGreeting(greetings[word], reveal = { reveal.value }, modifier = wordModifier)
            }
        }
    }
}

private const val TAG = "HomeHello"

/**
 * Lời chào các thứ tiếng xoay vòng sau "hello" viết tay (không dịch: mỗi câu đúng tiếng của nó). Câu tiếng của máy ([R.string.home_hello])
 * đứng đầu; bỏ "Hello" vì chữ viết tay đã là nó.
 */
private val HELLO_GREETINGS = listOf(
    "Xin chào", "Hola", "Bonjour", "こんにちは", "안녕하세요", "Olá", "नमस्ते", "สวัสดี", "Ciao", "Halo", "你好", "Hallo"
)

private fun helloGreetings(local: String): List<String> =
    (listOf(local) + HELLO_GREETINGS).distinct().filterNot { it.equals("hello", ignoreCase = true) }

private val GREETING_FONT = FontFamily(Font(R.font.be_vietnam_pro_bold, FontWeight.Bold))

/**
 * Một lớp của kiểu ống kính, cùng đơn vị với viewport 440 × 200 của `avd_hello.xml` (nét AVD [width] ↔ chữ nở thêm ([width] −
 * [GREETING_BASE_STROKE]) / 2 mỗi phía). [band] > 0: chỉ giữ dải mép TRÊN dày [band]; < 0: dải mép DƯỚI; 0: cả chữ.
 */
private class GlassLayer(val color: Color, val width: Float, val dx: Float, val dy: Float, val band: Float = 0f)

/**
 * Khớp từng lớp (thứ tự, màu, độ dày, độ lệch) với `avd_hello.xml`: shadow ×3, rim, body, core0..8 (lõi sáng chuyển mịn), specular, glint.
 * specular / glint của AVD là nét mảnh lệch lên / xuống; ở chữ gõ là dải mép trên / dưới của thân.
 */
private val GLASS_LAYERS = listOf(
    GlassLayer(Color(0x0B000000), 28f, 0f, 8f),
    GlassLayer(Color(0x0E000000), 24f, 0f, 7f),
    GlassLayer(Color(0x12000000), 20f, 0f, 6f),
    GlassLayer(Color(0x2E2A2226), 21f, 0f, 0.5f),
    GlassLayer(Color(0x1FCFC7C8), 20f, 0f, 0f)
) + List(9) { i -> GlassLayer(Color(0x0BFFFFFF), 20f - 2f * i, -0.07f * i, -0.55f * i) } + listOf(
    GlassLayer(Color(0x59FFFFFF), 20f, -0.6f, 0f, band = 2.2f),
    GlassLayer(Color(0x1FFFFFFF), 20f, 0.3f, 0f, band = -1.8f)
)

/** Độ dày nét chữ Be Vietnam Pro Bold ở cỡ [GREETING_HEIGHT_FRACTION], theo đơn vị viewport AVD (ước lượng). */
private const val GREETING_BASE_STROKE = 16f

/**
 * Một lời chào kiểu ống kính mờ như chữ "hello" viết tay: chữ gõ (Bold) nở / co bằng viền (Stroke) thay cho các nét đồng tâm của AVD,
 * xem [GLASS_LAYERS]. [reveal] 0..1 quét hiện chữ từ trái sang (mép mềm); đọc lúc vẽ nên mỗi khung chỉ vẽ lại, không đo lại chữ.
 */
@Composable
private fun GlassGreeting(text: String, reveal: () -> Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Spacer(
        modifier.drawWithCache {
            val layout = measureGreeting(measurer, text, size)
            val v = size.width / HELLO_VIEWPORT_W // 1 đơn vị viewport AVD
            val o = Offset((size.width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f)
            val bounds = Rect(Offset.Zero, size)
            val feather = size.width * GREETING_REVEAL_FEATHER
            onDrawBehind {
                val r = reveal()
                if (r <= 0f) return@onDrawBehind
                val canvas = drawContext.canvas
                canvas.saveLayer(bounds, Paint()) // lớp ngoài: cho mặt nạ quét
                for (l in GLASS_LAYERS) {
                    val grow = (l.width - GREETING_BASE_STROKE) / 2f * v
                    val at = o + Offset(l.dx * v, l.dy * v)
                    if (l.band == 0f) {
                        glyphs(layout, l.color, at, grow, bounds)
                    } else {
                        canvas.saveLayer(bounds, Paint().apply { alpha = l.color.alpha })
                        glyphs(layout, l.color.copy(alpha = 1f), at, grow, bounds)
                        glyphs(layout, Color.Black, at + Offset(0f, l.band * v), grow, bounds, BlendMode.DstOut)
                        canvas.restore()
                    }
                }
                if (r < 1f) {
                    val edge = r * (size.width + feather)
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = edge - feather, endX = edge),
                        blendMode = BlendMode.DstIn
                    )
                }
                canvas.restore()
            }
        }
    )
}

/** Chữ vừa khung: cỡ theo chiều cao khung, chữ dài quá bề ngang thì thu lại. */
private fun CacheDrawScope.measureGreeting(measurer: TextMeasurer, text: String, size: Size): TextLayoutResult {
    fun measure(fontPx: Float) = measurer.measure(
        text,
        TextStyle(fontFamily = GREETING_FONT, fontWeight = FontWeight.Bold, fontSize = fontPx.toSp()),
        softWrap = false,
        maxLines = 1
    )
    val base = size.height * GREETING_HEIGHT_FRACTION
    val first = measure(base)
    val maxW = size.width * GREETING_MAX_WIDTH_FRACTION
    return if (first.size.width <= maxW) first else measure(base * maxW / first.size.width)
}

/**
 * Chữ [layout] tô đặc màu [color], nở ra [grow] px mỗi phía (> 0: tô + viền; < 0: co lại = tô rồi trừ viền). Vẽ đặc trong một lớp rồi mới
 * áp độ trong của [color], để chỗ tô và viền chồng nhau không đậm gấp đôi. [blendMode] = cách ghép cả lớp xuống dưới.
 */
private fun DrawScope.glyphs(
    layout: TextLayoutResult,
    color: Color,
    o: Offset,
    grow: Float,
    bounds: Rect,
    blendMode: BlendMode = BlendMode.SrcOver
) {
    val canvas = drawContext.canvas
    canvas.saveLayer(bounds, Paint().apply { alpha = color.alpha; this.blendMode = blendMode })
    val solid = color.copy(alpha = 1f)
    // drawStyle luôn ghi rõ: để null thì drawText giữ kiểu (Stroke) của lần vẽ trước trên cùng layout -> "tô" thành viền rỗng.
    drawText(layout, solid, o, drawStyle = Fill)
    if (grow > 0f) {
        drawText(layout, solid, o, drawStyle = Stroke(2f * grow, join = StrokeJoin.Round))
    } else if (grow < 0f) {
        drawText(layout, Color.Black, o, drawStyle = Stroke(-2f * grow, join = StrokeJoin.Round), blendMode = BlendMode.DstOut)
    }
    canvas.restore()
}

private const val HELLO_VIEWPORT_W = 440f
private const val GREETING_HEIGHT_FRACTION = 0.42f
private const val GREETING_MAX_WIDTH_FRACTION = 0.92f
private const val GREETING_REVEAL_FEATHER = 0.3f

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

/** Viết / hiện xong một lời chào thì giữ chừng này rồi đổi sang lời chào kế. */
private const val HELLO_HOLD_MS = 2000L
private const val HELLO_WORD_OUT_MS = 350
private const val HELLO_WORD_IN_MS = 900
private const val HELLO_WORD_OUT_SCALE = 0.04f

/** Hiện ít nhất chừng này (kể cả Home đã sẵn): viết xong chữ rồi dừng một nhịp. */
private const val HELLO_MIN_MS = HELLO_WRITE_MS + 450L

/** Home chưa sẵn / chưa rảnh sau chừng này thì vẫn mờ đi. */
private const val HELLO_MAX_MS = 5000L

private const val HELLO_QUIET_FRAMES = 6
private const val HELLO_QUIET_FRAME_NS = 34_000_000L // ~2 vsync ở 60 Hz
