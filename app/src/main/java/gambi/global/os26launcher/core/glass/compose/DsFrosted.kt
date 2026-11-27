package gambi.global.os26launcher.core.glass.compose

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import gambi.global.os26launcher.core.glass.BlurredBackdropCache
import gambi.global.os26launcher.core.glass.GlassEnvironment
import gambi.global.os26launcher.core.glass.GlassRole
import gambi.global.os26launcher.core.glass.GlassStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import gambi.global.os26launcher.core.glass.smoothBlurred
import androidx.compose.runtime.SideEffect
import android.os.SystemClock

/**
 * PG3 — kính mờ TĨNH (bản Compose của kính tại chỗ `BackdropBlurDrawable` bên XML): ảnh nền đã làm mờ MỘT lần ngoài main thread (bản nhỏ của
 * [BlurredBackdropCache], độ mờ theo thanh "Độ mờ"), ghi vào một [LayerBackdrop] riêng. Bề mặt kính mờ vẽ lát cắt của lớp này bằng
 * `drawPlainBackdrop(..., effects = {})`: không RenderEffect (không `blur`/`lens`/`vibrancy` mỗi khung) nên rẻ, chạy cả dưới API 31 (thư viện vẽ
 * nền không hiệu ứng), và thư viện tự tính chỗ lấy mẫu theo toạ độ (lướt trang, Today / Thư viện trượt, ảnh kéo).
 *
 * Cách dùng: [rememberFrostedBackdrop] ở chỗ dựng ảnh nền, [FrostedWallpaperLayer] đặt CÙNG chỗ và cùng cỡ với ảnh nền, cung cấp
 * [FrostedBackdrop.backdropOrNull] qua [LocalDsFrostedBackdrop]; bề mặt dùng [dsFrosted] (ô thư mục, pill) hoặc `dsGlass` (CARD/SHEET dưới
 * API 31). Lớp nền mờ không vẽ gì ra màn: nội dung của node rỗng, bản mờ chỉ được ghi vào lớp (`onDraw` của [rememberLayerBackdrop]).
 */
@Stable
class FrostedBackdrop internal constructor(
    internal val layer: LayerBackdrop,
    internal val small: MutableState<ImageBitmap?>
) {
    /** Lớp nền mờ khi bản mờ đã dựng xong; null = chưa có (đang dựng, ảnh nền là màu / preset, vừa bị `trim`): bề mặt vẽ màu phẳng. */
    val backdropOrNull: Backdrop? get() = if (small.value != null) layer else null
}

/**
 * PG3: lớp nền mờ tĩnh của màn đang vẽ ([FrostedBackdrop.backdropOrNull]); null = không có, bề mặt kính mờ vẽ màu phẳng của nó.
 * `compositionLocalOf` (không static): giá trị đổi khi bản mờ dựng xong thì chỉ nơi đọc dựng lại.
 */
val LocalDsFrostedBackdrop: ProvidableCompositionLocal<Backdrop?> = compositionLocalOf { null }

/** Hiệu ứng của kính mờ tĩnh: không có (bản mờ đã làm sẵn). Hằng để node của thư viện không bị cập nhật mỗi lần dựng. */
val FrostedNoEffects: BackdropEffectScope.() -> Unit = {}

/** [source]: bitmap ảnh nền gốc (null = màu / preset). Giữ trạng thái bản mờ; đọc trong pha vẽ của lớp nên lớp tự ghi lại khi bản mờ tới. */
@Composable
fun rememberFrostedBackdrop(source: Bitmap?): FrostedBackdrop {
    val small = remember { mutableStateOf<ImageBitmap?>(null) }
    val sourceState = rememberUpdatedState(source)
    val onDraw: ContentDrawScope.() -> Unit = remember(small, sourceState) {
        { drawFrosted(sourceState.value, small.value) }
    }
    val layer = rememberLayerBackdrop(onDraw = onDraw)
    return remember(layer, small) { FrostedBackdrop(layer, small) }
}

/**
 * Node ghi lớp nền mờ: đặt CÙNG chỗ, cùng cỡ với ảnh nền ([modifier] thường là `fillMaxSize()`), trước ảnh nền trong cùng Box. Xin bản mờ của
 * [source] theo cỡ node (FX8: cỡ đích theo cỡ view ảnh nền). [cache] null = bộ đệm dùng chung của ảnh nền (`GlassEnvironment.blurCache`).
 */
@Composable
fun FrostedWallpaperLayer(frosted: FrostedBackdrop, source: Bitmap?, modifier: Modifier = Modifier, cache: BlurredBackdropCache? = null) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val small = rememberSmallBlur(source, size, cache)
    LaunchedEffect(small) { frosted.small.value = small }
    // Đọc bản mờ trong pha vẽ của CÙNG node: bản mờ tới thì node vẽ lại, và lớp (ghi trong `draw` của `layerBackdrop`) được ghi lại theo.
    Box(modifier.onSizeChanged { size = it }.drawBehind { frosted.small.value }.layerBackdrop(frosted.layer))
}

/**
 * PG3 Task 5: ảnh nền đã mờ, VẼ RA MÀN (nền Today / Thư viện dưới API 31, thay bản thu nhỏ `shrinkForBlur` bị lộ khối khi phóng). Phủ như
 * `Image(ContentScale.Crop)` của ảnh nền gốc. Chưa có bản mờ thì không vẽ gì (nền phẳng phía sau hiện ra).
 */
@Composable
fun FrostedWallpaperImage(source: Bitmap?, modifier: Modifier = Modifier, cache: BlurredBackdropCache? = null) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val small = rememberSmallBlur(source, size, cache) // HH: [cache] riêng (vd màn chào, mờ mạnh hơn "Độ mờ" của kính); null = của ảnh nền
    val sourceState = rememberUpdatedState(source)
    Box(modifier.onSizeChanged { size = it }.drawBehind { drawFrosted(sourceState.value, small) })
}

/**
 * Bản mờ nhỏ của [source] từ [cache] (null = bộ đệm của ảnh nền); cập nhật khi dựng xong, bị `trim`, ảnh nền đổi, "Độ mờ" đổi. [size] là cỡ vùng vẽ
 * (chỉ để biết đã đo xong); cỡ đích xin bộ đệm là cỡ CỬA SỔ, chung cho mọi nơi dùng (Home, Today, Thư viện): bộ đệm chỉ giữ MỘT cỡ đích, hai nơi
 * xin hai cỡ khác nhau sẽ làm nó dựng lại qua lại mãi. Bản nhỏ phủ toàn bộ bitmap gốc nên vẽ vào vùng cỡ nào cũng đúng hình học ([drawFrosted]).
 */
@Composable
private fun rememberSmallBlur(source: Bitmap?, size: IntSize, cache: BlurredBackdropCache?): ImageBitmap? {
    val env = GlassEnvironment.from(LocalContext.current)
    val view = LocalView.current
    val blurCache = cache ?: env.blurCache
    val look by rememberGlassLook() // "Độ mờ" đổi: cache bỏ bản cũ (GlassEnvironment.apply) -> xin lại
    var tick by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<ImageBitmap?>(null) }
    val listener = remember { FrostListener() }
    listener.onChange = { tick++ }
    DisposableEffect(blurCache, listener) {
        blurCache.register(listener)
        onDispose { blurCache.cancel(listener) }
    }
    LaunchedEffect(blurCache, source, size, look.blur, tick) {
        val w = view.width.takeIf { it > 0 } ?: size.width
        val h = view.height.takeIf { it > 0 } ?: size.height
        val bitmap = if (source != null && size.width > 0 && size.height > 0 && w > 0 && h > 0) {
            blurCache.smallOrRequest(source, w, h, listener)
        } else {
            null
        }
        if (bitmap !== listener.lastBitmap) {
            listener.lastBitmap = bitmap
            result = bitmap?.asImageBitmap()
        }
    }
    return result
}

/** Nghe [BlurredBackdropCache] (cache giữ tham chiếu YẾU: giữ đối tượng này trong `remember`). */
private class FrostListener : BlurredBackdropCache.SmallListener {
    var onChange: () -> Unit = {}
    var lastBitmap: Bitmap? = null

    override fun onSmallReady() = onChange()

    override fun onSmallDropped() = onChange()
}

/**
 * PG3 Task 2 Step 3 (Q3): ảnh chụp MỘT lần của [backdrop] (lớp Home: ảnh nền + lưới + dock) khi [capture] bật, giữ tới khi [keep] tắt. Dùng cho
 * bảng thư mục dưới API 31 (kính thật không chạy ở đó): chụp xong được làm mờ như ảnh nền ([FrostedWallpaperLayer] với bộ đệm riêng), nên
 * bảng vẫn thấy icon phía sau đã mờ. Chờ hai khung để lớp kịp được ghi sau khi bật; ảnh HARDWARE được chép sang ARGB_8888 ngoài main thread
 * (bộ đệm mờ cần đọc điểm ảnh). Lỗi (lớp chưa ghi, hết bộ nhớ) thì trả null: bảng dùng lớp nền mờ của ảnh nền.
 */
@Composable
fun rememberLayerSnapshot(backdrop: LayerBackdrop, capture: Boolean, keep: Boolean): Bitmap? {
    var snapshot by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(backdrop, capture) {
        if (!capture) return@LaunchedEffect
        withFrameNanos {}
        withFrameNanos {}
        val shot = try {
            val image = backdrop.graphicsLayer.toImageBitmap()
            withContext(Dispatchers.Default) {
                val bitmap = image.asAndroidBitmap()
                if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
            }
        } catch (e: Throwable) {
            Log.w(TAG, "layer snapshot failed", e)
            null
        }
        if (shot != null) snapshot = shot
    }
    LaunchedEffect(keep) { if (!keep) snapshot = null }
    return snapshot
}

/**
 * FX (B16 tiếp, chủ dự án 2026-11-27): ảnh chụp lớp Home dưới API 31 đã làm mờ SẴN, nhỏ ([SNAPSHOT_SCALE] = 1/8 cỡ chụp), trả về khi xong.
 * Khác [rememberLayerSnapshot] + [FrostedWallpaperLayer]: chép HARDWARE → ARGB, thu nhỏ và làm mờ trong CÙNG một lần chạy ở luồng nền (không qua
 * [BlurredBackdropCache], không chờ thêm khung để báo bộ đệm), để ảnh mờ có icon tới kịp lúc bảng thư mục đang phóng. [radiusPx]: bán kính box
 * blur ở cỡ 1/4 (như kính mờ tĩnh); ở cỡ 1/8 dùng một nửa. Chụp khi [capture] bật, giữ tới khi [keep] tắt; lỗi thì trả null.
 */
@Composable
fun rememberBlurredLayerSnapshot(backdrop: LayerBackdrop, capture: Boolean, keep: Boolean, radiusPx: Int): Bitmap? {
    var snapshot by remember { mutableStateOf<Bitmap?>(null) }
    val radius = rememberUpdatedState(radiusPx)
    LaunchedEffect(backdrop, capture) {
        if (!capture) return@LaunchedEffect
        FolderBlurTrace.start()
        withFrameNanos {}
        withFrameNanos {}
        FolderBlurTrace.log("frames")
        val shot = try {
            val image = backdrop.graphicsLayer.toImageBitmap()
            FolderBlurTrace.log("readback ${image.width}x${image.height}")
            val r = ((radius.value + 1) / 2).coerceAtLeast(1)
            withContext(Dispatchers.Default) {
                val full = image.asAndroidBitmap()
                val soft = if (full.config == Bitmap.Config.HARDWARE) full.copy(Bitmap.Config.ARGB_8888, false) else full
                FolderBlurTrace.log("copy ${full.config}")
                val w = (soft.width / SNAPSHOT_SCALE).coerceAtLeast(1)
                val h = (soft.height / SNAPSHOT_SCALE).coerceAtLeast(1)
                smoothBlurred(soft, w, h, r).also {
                    if (soft !== full) soft.recycle()
                    FolderBlurTrace.log("blur ${w}x$h r=$r")
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "blurred layer snapshot failed", e)
            null
        }
        if (shot != null) snapshot = shot
    }
    LaunchedEffect(keep) { if (!keep) snapshot = null }
    return snapshot
}

/**
 * FX: ghi ảnh chụp đã mờ [small] ([rememberBlurredLayerSnapshot]) vào lớp [frosted] (tạo bằng `rememberFrostedBackdrop(small)`), phủ toàn
 * [modifier]. Không vẽ gì ra màn. Gán bản mờ ngay sau lần dựng (SideEffect), không chờ thêm khung.
 */
@Composable
fun FrostedSnapshotLayer(frosted: FrostedBackdrop, small: Bitmap?, modifier: Modifier = Modifier) {
    val image = remember(small) { small?.asImageBitmap() }
    SideEffect { frosted.small.value = image }
    Box(modifier.drawBehind { frosted.small.value }.layerBackdrop(frosted.layer))
}

/** FX — log ĐO TẠM (tag `FolderBlur`) mốc thời gian ảnh mờ sau bảng thư mục dưới API 31; mốc tính từ lúc bắt đầu chụp. Gỡ khi chốt. */
object FolderBlurTrace {
    @Volatile
    private var t0 = 0L

    fun start() {
        t0 = SystemClock.uptimeMillis()
        Log.i(TRACE_TAG, "open")
    }

    fun log(step: String) {
        if (t0 == 0L) return
        Log.i(TRACE_TAG, "+${SystemClock.uptimeMillis() - t0}ms $step")
    }
}

private const val TRACE_TAG = "FolderBlur"

/** Ảnh chụp sau bảng thư mục thu về 1/8 cạnh trước khi làm mờ (chủ dự án chọn hướng A, 2026-11-27). */
private const val SNAPSHOT_SCALE = 8

/**
 * PG3 Task 2 — bề mặt kính mờ tĩnh hình [shape] (ô thư mục, pill), màu theo vai trò [role] (`GlassStyle.forRole`, độ bóng theo thông số kính):
 * lát cắt nền mờ ([backdrop], null = [LocalDsFrostedBackdrop]) + lớp tint + viền sáng mảnh, như `BackdropBlurDrawable`. Không có nền mờ thì
 * vẽ màu phẳng [GlassStyle.flat] (= màu nền phẳng cũ của phần tử) + viền. Nội dung bên trong bị cắt theo [shape].
 */
fun Modifier.dsFrosted(role: GlassRole, shape: Shape, backdrop: Backdrop? = null): Modifier = composed {
    val context = LocalContext.current
    val source = backdrop ?: LocalDsFrostedBackdrop.current
    val look = (LocalGlassLookOverride.current ?: rememberGlassLook()).value
    val style = remember(context, role, look) { GlassStyle.forRole(context, role).tuned(look) }
    val tint = Color(style.tint)
    val flat = Color(style.flat)
    val border = Color(style.border)
    val hasBorder = border.alpha > 0f
    val strokePx = context.resources.displayMetrics.density * BORDER_DP
    val outline = remember(shape) { OutlineCache(shape) }
    val shapeProvider = remember(shape) { { shape } }
    if (source != null) {
        Modifier.drawPlainBackdrop(
            backdrop = source,
            shape = shapeProvider,
            effects = FrostedNoEffects,
            onDrawSurface = {
                drawRect(tint)
                if (hasBorder) drawOutline(outline.get(this), border, style = Stroke(strokePx * 2f)) // nửa ngoài bị cắt theo hình
            }
        )
    } else {
        Modifier.clip(shape).drawBehind {
            drawRect(flat)
            if (hasBorder) drawOutline(outline.get(this), border, style = Stroke(strokePx * 2f))
        }
    }
}

/** Outline của [shape] theo cỡ vùng vẽ, dựng lại chỉ khi cỡ đổi (không cấp phát mỗi khung). */
private class OutlineCache(private val shape: Shape) {
    private var size = Size.Unspecified
    private var outline: Outline? = null

    fun get(scope: DrawScope): Outline {
        val o = outline
        if (o != null && size == scope.size) return o
        return shape.createOutline(scope.size, scope.layoutDirection, scope).also {
            outline = it
            size = scope.size
        }
    }
}

/**
 * Vẽ bản mờ nhỏ [small] phủ đúng như ảnh nền gốc [source] được vẽ (`Image(ContentScale.Crop)`, căn giữa): bản nhỏ phủ TOÀN BỘ bitmap gốc
 * nên ánh xạ qua cỡ intrinsic của bitmap gốc, không giả định cùng tỉ lệ với vùng vẽ (như `BackdropBlurDrawable.prepareShader`, FX8).
 * Lọc song tuyến khi phóng (bản nhỏ đã mờ thật nên không lộ khối).
 */
private fun DrawScope.drawFrosted(source: Bitmap?, small: ImageBitmap?) {
    val src = source ?: return
    val img = small ?: return
    if (size.width <= 0f || size.height <= 0f || src.width <= 0 || src.height <= 0) return
    val dw = src.width.toFloat()
    val dh = src.height.toFloat()
    val scale = max(size.width / dw, size.height / dh)
    val w = dw * scale
    val h = dh * scale
    drawImage(
        img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        filterQuality = FilterQuality.Low
    )
}

private const val TAG = "DsFrosted"
private const val BORDER_DP = 1f
