package gambi.global.os26launcher.core.glass.compose

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import gambi.global.os26launcher.domain.model.GlassLook
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import gambi.global.os26launcher.core.bitmap.IconShape
import gambi.global.os26launcher.core.glass.GlassTuning
import kotlin.math.max

/**
 * LI (chủ dự án 2026-11-25: kính chụp thành ảnh trông như nền đứng yên khi lướt trang) — ảnh nền ĐÃ MỜ SẴN cho kính tự vẽ sau icon.
 *
 * Dùng chính bản mờ nhỏ của [FrostedBackdrop] (mờ một lần ngoài main thread theo thanh "Độ mờ"), phủ như `drawFrosted` (Crop, căn giữa) trong
 * khung của node lớp nền mờ ([iconGlassWallpaperOrigin]). Các tấm kính lấy mẫu nó qua một [BitmapShader] dùng chung.
 */
@Stable
class IconGlassWallpaper internal constructor(private val frosted: FrostedBackdrop, private val source: State<Bitmap?>) {
    /** State: ô vẽ trước khi lớp nền được đặt chỗ thì vẽ lại khi có (không thì ô đứng yên sẽ không bao giờ có kính). */
    internal var coords: LayoutCoordinates? by mutableStateOf(null)

    /** Đã có bản mờ và ảnh nền gốc (đọc trong pha dựng: ô dựng lại một lần khi bản mờ tới / mất). */
    val ready: Boolean get() = frosted.small.value != null && source.value != null

    private var cachedImage: Any? = null
    private var cachedW = 0
    private var cachedH = 0
    private var shader: BitmapShader? = null

    /** Shader ảnh nền mờ theo toạ độ của node lớp nền; null = chưa sẵn. Gọi trong pha vẽ (đọc state bản mờ → vẽ lại khi nó đổi). */
    internal fun inputShader(): BitmapShader? {
        val img = frosted.small.value ?: return null
        val src = source.value ?: return null
        val c = coords ?: return null
        val w = c.size.width
        val h = c.size.height
        if (w <= 0 || h <= 0 || src.width <= 0 || src.height <= 0) return null
        if (img === cachedImage && w == cachedW && h == cachedH) return shader
        // Như drawFrosted: phủ kín (Crop) bitmap gốc vào khung, căn giữa; bản nhỏ phủ toàn bộ bitmap gốc.
        val scale = max(w / src.width.toFloat(), h / src.height.toFloat())
        val dw = src.width * scale
        val dh = src.height * scale
        val bitmap = img.asAndroidBitmap()
        val m = Matrix().apply {
            setScale(dw / bitmap.width, dh / bitmap.height)
            postTranslate((w - dw) / 2f, (h - dh) / 2f)
        }
        shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(m)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) filterMode = BitmapShader.FILTER_MODE_LINEAR
        }
        cachedImage = img
        cachedW = w
        cachedH = h
        return shader
    }
}

@Composable
fun rememberIconGlassWallpaper(frosted: FrostedBackdrop, source: Bitmap?): IconGlassWallpaper {
    val sourceState = rememberUpdatedState(source)
    return remember(frosted) { IconGlassWallpaper(frosted, sourceState) }
}

/** Đặt trên node lớp nền mờ ([FrostedWallpaperLayer]): gốc toạ độ + cỡ khung mà bản mờ phủ vào. */
fun Modifier.iconGlassWallpaperOrigin(wall: IconGlassWallpaper): Modifier = onPlaced { wall.coords = it }

/** `null` = không có (màn khác Home): tấm kính icon dùng ảnh chụp ([CachedIconGlassPlate]). */
val LocalIconGlassWallpaper: ProvidableCompositionLocal<IconGlassWallpaper?> = compositionLocalOf { null }

/**
 * LI — tấm liquid glass sau icon, tự vẽ bằng MỘT lệnh `drawPath` với [RuntimeShader]: lấy mẫu ảnh nền mờ sẵn ([IconGlassWallpaper]) ngay dưới
 * icon, khúc xạ ở mép (cùng công thức SDF bo góc + `circleMap` với `lens` của thư viện Backdrop, Apache 2.0) và tăng bão hòa như `vibrancy`.
 *
 * Vì sao không dùng `drawBackdrop`: mỗi tấm của thư viện là một lớp offscreen + RenderEffect chạy lại mỗi khung khi trang trượt. Đo 2026-11-25
 * (bản `perf`, SM-A176B, vuốt trang 1 ↔ 2): blur + lens Janky 80 %, chỉ lens trên nền mờ sẵn vẫn 40 %. Ở đây shader chạy thẳng khi vẽ ô (như
 * tô một gradient), không lớp phụ. Vị trí lấy mẫu cập nhật qua `onGloballyPositioned`, nên kính trôi đúng trên nền khi lướt trang.
 *
 * Lúc trang lướt (`IconGlassHost.moving`): tấm của từng ô KHÔNG vẽ, không theo dõi vị trí; [IconGlassPageLayer] của trang vẽ thay mọi tấm.
 * Đo 2026-11-26: tấm từng ô theo vị trí mỗi khung (kể cả không vẽ gì) tốn p50 ~24 ms so với 12 ms: chi phí là đánh dấu vẽ lại ~24 node mỗi khung.
 *
 * Giới hạn: lúc Home trượt theo Today / Thư viện (dịch bằng graphicsLayer, không đặt lại vị trí) điểm lấy mẫu đứng ở chỗ cũ tới khi dừng.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun IconGlassShaderPlate(sizePx: Int, corner: Int, wall: IconGlassWallpaper, modifier: Modifier = Modifier) {
    val painter = rememberIconGlassPainter(sizePx, corner)
    val host = LocalIconGlassHost.current
    // origin là state chỉ để vẽ lại khi ô dời chỗ trên màn; lúc vẽ luôn tính lại từ toạ độ hiện tại.
    var origin by remember { mutableStateOf(Offset.Unspecified) }
    val self = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // Trang ngoài màn (pager dựng sẵn mọi trang, HomeScreen không cấp LocalDsBackdrop cho trang ngoài màn): không theo dõi vị trí.
    val onScreen by rememberUpdatedState(LocalDsBackdrop.current != null)
    Box(
        modifier
            .onGloballyPositioned { c ->
                self[0] = c
                if (onScreen && host?.moving?.value != true) origin = positionOnWallpaper(wall, c)
            }
            .drawBehind {
                if (host?.moving?.value == true) return@drawBehind // lớp của trang vẽ thay
                origin // đọc để vẽ lại khi ô dời chỗ
                val o = self[0]?.let { positionOnWallpaper(wall, it) } ?: return@drawBehind
                painter.draw(this, wall, o)
            }
    )
}

/**
 * LI — lớp kính của cả trang lúc trang lướt: vẽ tấm kính sau mọi icon ở [plates] (góc trên-trái icon, toạ độ trong trang) bằng một node, vị
 * trí trang theo dõi bằng MỘT `onGloballyPositioned`. Ngoài lúc lướt không vẽ gì (tấm của từng ô vẽ). Đặt phía sau các ô của trang.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun IconGlassPageLayer(sizePx: Int, corner: Int, plates: () -> List<Offset>, modifier: Modifier = Modifier) {
    val wall = LocalIconGlassWallpaper.current
    val host = LocalIconGlassHost.current
    if (wall == null || host == null || !wall.ready) return
    val painter = rememberIconGlassPainter(sizePx, corner)
    var origin by remember { mutableStateOf(Offset.Unspecified) }
    val self = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        modifier
            .onGloballyPositioned { c ->
                self[0] = c
                if (host.moving.value) origin = positionOnWallpaper(wall, c)
            }
            .drawBehind {
                if (!host.moving.value) return@drawBehind
                origin
                val page = self[0]?.let { positionOnWallpaper(wall, it) } ?: return@drawBehind
                for (p in plates()) {
                    translate(p.x, p.y) { painter.draw(this, wall, page + p) }
                }
            }
    )
}

/** Shader + hình của tấm kính một cỡ icon; dùng chung cho tấm từng ô và lớp của trang. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class IconGlassPainter(private val sizePx: Int, private val iconShape: IconShape, private val look: State<GlassLook>) {
    private val shader = RuntimeShader(ICON_GLASS_AGSL)
    private val brush = ShaderBrush(shader)
    private val path = iconShape.path(sizePx).asComposePath()

    /** Vẽ tấm ở gốc của [scope]; [origin] = góc trên-trái tấm trong toạ độ lớp nền mờ. */
    fun draw(scope: DrawScope, wall: IconGlassWallpaper, origin: Offset) {
        val input = wall.inputShader() ?: return
        if (!origin.isSpecified || sizePx <= 0) return
        val radius = (iconShape.cornerRatio * sizePx).coerceAtMost(sizePx / 2f)
        val (height, amount) = GlassTuning.backdropLensDp(look.value)
        val density = scope.density
        shader.setInputShader("wall", input)
        shader.setFloatUniform("size", sizePx.toFloat(), sizePx.toFloat())
        shader.setFloatUniform("radius", radius)
        // Như lens của thư viện: độ cao khúc xạ ≤ bán kính góc; thu nhỏ như ô thư mục cùng cỡ.
        shader.setFloatUniform("refractionHeight", minOf(height * density * ICON_LENS_SCALE, radius).coerceAtLeast(1f))
        shader.setFloatUniform("refractionAmount", amount * density * ICON_LENS_SCALE)
        shader.setFloatUniform("origin", origin.x, origin.y)
        shader.setFloatUniform("saturation", VIBRANCY_SATURATION)
        scope.drawPath(path, brush)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun rememberIconGlassPainter(sizePx: Int, corner: Int): IconGlassPainter {
    val look = LocalGlassLookOverride.current ?: rememberGlassLook()
    return remember(sizePx, corner, look) { IconGlassPainter(sizePx, IconShape.forCorner(corner), look) }
}

private fun positionOnWallpaper(wall: IconGlassWallpaper, c: LayoutCoordinates): Offset {
    val w = wall.coords // đọc state: lớp nền đặt chỗ sau thì ô vẽ lại
    return if (w != null && w.isAttached && c.isAttached) w.localPositionOf(c, Offset.Zero) else Offset.Unspecified
}

/** Như `vibrancy()` của Backdrop (bão hòa 1,5). */
private const val VIBRANCY_SATURATION = 1.5f

/** Như `FOLDER_LENS_SCALE` của ô thư mục (cùng cỡ icon). */
private const val ICON_LENS_SCALE = 0.6f

private const val ICON_GLASS_AGSL = """
uniform shader wall;
uniform float2 size;
uniform float radius;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float2 origin;
uniform float saturation;

float sdRoundedRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - (halfSize - float2(r));
    return length(max(q, 0.0)) - r + min(max(q.x, q.y), 0.0);
}

float2 gradSdRoundedRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - (halfSize - float2(r));
    if (q.x >= 0.0 || q.y >= 0.0) {
        return sign(p) * normalize(max(q, 0.0) + float2(1e-4));
    }
    float gx = step(q.y, q.x);
    return sign(p) * float2(gx, 1.0 - gx);
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 c = coord - halfSize;
    float sd = sdRoundedRect(c, halfSize, radius);
    float2 p = coord;
    if (-sd < refractionHeight) {
        // Dải mép: lấy mẫu dời vào trong theo pháp tuyến, mạnh nhất ở sát mép (circleMap), như lens của Backdrop.
        float d = circleMap(1.0 - max(-sd, 0.0) / refractionHeight) * refractionAmount;
        float gr = min(radius * 1.5, min(halfSize.x, halfSize.y));
        p = coord - d * gradSdRoundedRect(c, halfSize, gr);
    }
    half4 col = wall.eval(origin + p);
    half l = dot(col.rgb, half3(0.213, 0.715, 0.072));
    col.rgb = clamp(mix(half3(l), col.rgb, half(saturation)), 0.0, 1.0);
    return col;
}
"""
