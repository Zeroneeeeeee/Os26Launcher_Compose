package gambi.global.os26launcher.core.glass.compose

import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import gambi.global.os26launcher.core.perf.PerfSwitches
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import gambi.global.os26launcher.core.bitmap.IconShape
import gambi.global.os26launcher.core.glass.GlassTuning

/**
 * LI (2026-11-25, chủ dự án: "backdrop của app icon cũng có chế độ liquid glass") — kính thật sau icon app kiểu Kính.
 *
 * Giá trị = thanh "Bo góc" (0..100) mà bitmap icon đã cắt theo (`IconStyleKey.corner`); `null` = không vẽ kính sau icon (kiểu icon khác Kính,
 * hoặc đang dùng gói icon: icon gói đục, kính phía sau không lộ ra mà vẫn tốn). HomeActivity đặt một lần sau khi đọc kiểu icon (đổi kiểu thì
 * Activity dựng lại, RecreateGuard). `compositionLocalOf`: giá trị đổi null → số lúc khởi động chỉ dựng lại các ô đọc nó, không cả Home.
 */
val LocalIconGlassCorner: ProvidableCompositionLocal<Int?> = compositionLocalOf { null }

/**
 * Tấm liquid glass sau icon cỡ [sizePx]: lấy mẫu [LocalDsBackdrop] (ảnh nền), `vibrancy` → `blur` (thanh "Độ mờ") → `lens` (khúc xạ mép, thu
 * nhỏ như ô thư mục cùng cỡ). Không lớp bề mặt, không viền sáng của thư viện: bitmap kiểu Kính đã nướng sẵn lòng trắng nhạt + viền SweepGradient
 * (`IconRenderer.glassContent`), vẽ đè lên tấm này.
 *
 * Cắt theo đúng siêu elip của icon ([IconShape.forCorner]): kính bo tròn thường cùng bán kính lộ ra một mẩu ở 4 góc ngoài hình icon.
 *
 * Không có nền (trang ngoài màn, API < 31): không vẽ gì, icon chỉ còn tấm nướng sẵn như trước.
 */
fun Modifier.iconGlassPlate(sizePx: Int, corner: Int): Modifier = composed {
    val source = LocalDsBackdrop.current
    if (source == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S || sizePx <= 0) return@composed Modifier
    iconGlass(source, sizePx, corner)
}

@Composable
private fun Modifier.iconGlass(source: Backdrop, sizePx: Int, corner: Int): Modifier {
    val pinned = (source as? PagePinnedBackdrop)?.let { p -> remember(p) { PinnedTileBackdrop(p) } }
    val lookState = LocalGlassLookOverride.current ?: rememberGlassLook()
    val iconShape = remember(corner) { IconShape.forCorner(corner) }
    val clipShape: Shape = remember(iconShape, sizePx) { iconClipShape(iconShape) }
    // Node của thư viện giữ lambda của lần dựng đầu (xem dsGlass): đọc bán kính qua state để đổi cỡ lưới vẫn đúng.
    val radius by rememberUpdatedState((iconShape.cornerRatio * sizePx).coerceAtMost(sizePx / 2f))
    return this
        .then(if (pinned != null) Modifier.onPlaced { pinned.place(it) } else Modifier)
        .clip(clipShape)
        .drawBackdrop(
            backdrop = pinned ?: source,
            shape = { RoundedCornerShape(radius) },
            effects = {
                val l = lookState.value
                vibrancy()
                blur(GlassTuning.backdropBlurDp(l).dp.toPx())
                val (height, amount) = GlassTuning.backdropLensDp(l)
                // Thư viện yêu cầu độ cao khúc xạ ≤ bán kính góc.
                lens(minOf(height.dp.toPx() * ICON_LENS_SCALE, radius), amount.dp.toPx() * ICON_LENS_SCALE)
            },
            highlight = null,
            shadow = null
        )
}

/**
 * LI — chủ của các tấm kính icon trong một vùng (một trang lưới, dock): gốc toạ độ để biết icon dời chỗ trong vùng (không tính phép trượt
 * của cả trang) và lúc nào vùng đứng yên trên ảnh nền ([settled]) để chụp kính. [cacheKey] = danh tính ảnh nền: đổi thì chụp lại.
 */
@Stable
class IconGlassHost(val settled: State<Boolean>) {
    var cacheKey: Any? by mutableStateOf(null)
    internal var origin: LayoutCoordinates? = null
}

/** Đặt trên gốc vùng của [host] (khung trang / dock), SAU phép trượt của trang để vị trí icon tính trong vùng. */
fun Modifier.iconGlassOrigin(host: IconGlassHost): Modifier = onPlaced { host.origin = it }

/** `null` = không có chủ (ảnh kéo đang bay): [CachedIconGlassPlate] vẽ kính sống. */
val LocalIconGlassHost: ProvidableCompositionLocal<IconGlassHost?> = compositionLocalOf { null }

/**
 * LI (đo 2026-11-25, bản `perf`, SM-A176B, vuốt trang 1 ↔ 2): kính sống sau mọi icon chạy RenderEffect (blur + lens) cho từng icon mỗi khung
 * khi trang trượt: Janky 80 %, p50 69 ms, so với 2,2 %, p50 12 ms khi tắt. Như `CachedGlass` của dock: kính sống chỉ vẽ khi vùng ĐỨNG YÊN
 * ([IconGlassHost.settled]) và ảnh chụp đã cũ, sau 3 khung chụp lớp thành ảnh; còn lại (trượt trang, rung edit mode, trang ngoài màn) chỉ vẽ
 * ảnh đã chụp. Ảnh cũ (icon vừa dời chỗ) vẫn vẽ tới khi vùng đứng yên và chụp lại.
 *
 * Vùng chưa từng chụp mà đang trượt (lần đầu vuốt tới trang): vẽ kính sống, tốn một lần mỗi trang.
 * Không có chủ ([LocalIconGlassHost] null): kính sống như [iconGlassPlate].
 */
@Composable
fun CachedIconGlassPlate(sizePx: Int, corner: Int, modifier: Modifier = Modifier) {
    // LI: API 33+ và đã có ảnh nền mờ sẵn → kính tự vẽ bằng shader, sống, trôi theo nền ([IconGlassShaderPlate]); ảnh chụp chỉ còn cho lúc
    // chưa có (ảnh nền màu / preset, bản mờ đang dựng) và API 31–32. Công tắc đo `noIconShader` (bản `perf`) về ảnh chụp.
    val wall = if (PerfSwitches.noIconShader || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) null else LocalIconGlassWallpaper.current
    if (wall != null && wall.ready && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        IconGlassShaderPlate(sizePx, corner, wall, modifier)
        return
    }
    val host = LocalIconGlassHost.current
    if (host == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        Box(modifier.iconGlassPlate(sizePx, corner))
        return
    }
    val look by rememberGlassLook()
    var pos by remember { mutableStateOf<IntOffset?>(null) }
    val key = IconGlassKey(host.cacheKey, look, sizePx, corner, pos)
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var imageKey by remember { mutableStateOf<IconGlassKey?>(null) }
    val fresh = image != null && imageKey == key
    // Chỉ chụp khi biết vị trí trong vùng và vùng đứng yên trên nền. Ô đã có ảnh đúng KHÔNG đọc settled / LocalDsBackdrop (&& ngắt sớm), để
    // trang bắt đầu / dừng trượt hay vào / ra màn không dựng lại cả 24 ô.
    val capture = !fresh && pos != null && host.settled.value && LocalDsBackdrop.current != null
    val live = capture || (image == null && LocalDsBackdrop.current != null)
    val layer = rememberGraphicsLayer()
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val track = Modifier.onPlaced { c ->
        coords[0] = c
        pos = positionIn(host, c)
    }
    if (pos == null) {
        // Gốc vùng có thể được đặt SAU ô ở lần đầu: thử lại vài khung.
        LaunchedEffect(Unit) {
            repeat(POS_RETRY_FRAMES) {
                withFrameNanos {}
                val p = coords[0]?.let { positionIn(host, it) }
                if (p != null) {
                    pos = p
                    return@LaunchedEffect
                }
            }
        }
    }
    if (live) {
        Box(
            modifier
                .then(track)
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
                .iconGlassPlate(sizePx, corner)
        )
        if (capture) {
            LaunchedEffect(key) {
                // Chờ kính sống vẽ xong (lớp nền đã ghi, RenderEffect đã gắn) rồi mới chụp, như CachedGlass.
                withFrameNanos {}
                withFrameNanos {}
                withFrameNanos {}
                val shot = try {
                    layer.toImageBitmap()
                } catch (e: Throwable) {
                    Log.w(TAG, "capture failed", e)
                    null
                }
                if (shot != null) {
                    image = shot
                    imageKey = key
                }
            }
        }
    } else {
        val img = image
        Box(modifier.then(track).drawBehind { if (img != null) drawImage(img) })
    }
}

/** Mọi thứ làm ảnh chụp kính icon hết đúng. */
private data class IconGlassKey(val user: Any?, val look: Any, val sizePx: Int, val corner: Int, val pos: IntOffset?)

private fun positionIn(host: IconGlassHost, c: LayoutCoordinates): IntOffset? {
    val o = host.origin ?: return null
    return if (o.isAttached && c.isAttached) o.localPositionOf(c, Offset.Zero).round() else null
}

private const val TAG = "IconGlass"
private const val POS_RETRY_FRAMES = 10

/** Hình cắt theo [IconShape] ở cỡ thật của vùng vẽ (path dựng mỗi lần đổi cỡ, không mỗi khung). */
private fun iconClipShape(shape: IconShape): Shape = GenericShape { size, _ ->
    addPath(shape.path(size.minDimension.toInt()).asComposePath())
}

/** Như `FOLDER_LENS_SCALE` của ô thư mục (cùng cỡ icon): khúc xạ của card quá mạnh với bề mặt ~60 dp. */
private const val ICON_LENS_SCALE = 0.6f
