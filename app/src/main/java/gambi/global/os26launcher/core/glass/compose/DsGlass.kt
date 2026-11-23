package gambi.global.os26launcher.core.glass.compose

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsCorners
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.DsSurfaceDrawable
import gambi.global.os26launcher.core.glass.GlassTuning
import androidx.compose.runtime.State
import gambi.global.os26launcher.domain.model.GlassLook

/**
 * JD Task 2 — lớp nền mà kính của màn đang vẽ đọc vào (lớp `layerBackdrop`). `DsScreen` cung cấp; `null` = không có nền để khúc xạ,
 * [dsGlass] vẽ bề mặt tĩnh. Đặt ở `core/glass` (không phải `DsScreen.kt`) để gói kính không phụ thuộc ngược vào gói design system.
 */
val LocalDsBackdrop: ProvidableCompositionLocal<Backdrop?> = staticCompositionLocalOf { null }

/**
 * Bề mặt kính DS cho Compose, dùng chung cho mọi bề mặt (JC D4).
 *
 * Lớp bề mặt luôn là [DsSurfaceDrawable] của [surface] (nền `ds_surface_fill` trong 20%, viền sáng mép trên...), nên nhìn đúng DS3. Phía sau
 * nó, theo API (JC D5):
 * - API ≥ 33: nền qua `vibrancy` → `blur` → `lens` (khúc xạ mép), cộng viền sáng của Backdrop (F1).
 * - API 31–32: `vibrancy` → `blur` + viền sáng (thư viện tự bỏ `lens` dưới API 33).
 * - API < 31, hoặc không có nền: không gọi Backdrop (dưới API 31 nó vẽ nền mà không hiệu ứng nào); chỉ vẽ [DsSurfaceDrawable].
 *
 * JD: mọi số đọc từ thông số kính của người dùng ([rememberGlassLook] + `GlassTuning.backdrop*`), nên kéo thanh "Khúc xạ", "Độ mờ",
 * "Độ bóng", "Bo góc" thì kính đổi ngay. Ở `GlassLook.DEFAULT` cho đúng số của JC.
 *
 * Nội dung bên trong bị cắt theo hình bo, giống `clipToOutline` của `applyDsSurface`.
 *
 * [backdrop] `null` = lấy [LocalDsBackdrop].
 *
 * PG3 Task 2 (chủ dự án 2026-11-23): API < 31, CARD/SHEET có lớp nền mờ tĩnh ([frosted], `null` = [LocalDsFrostedBackdrop]) thì vẽ lát cắt nền
 * đã mờ sẵn phía sau lớp bề mặt (`drawPlainBackdrop` không hiệu ứng, như kính tại chỗ của XML) thay cho bề mặt phẳng. API ≥ 31 không đổi.
 *
 * [realGlass] (2026-11-23, chủ dự án theo ảnh iOS 26): bề mặt CONTROL vẫn được kính thật (khúc xạ mép + viền sáng) dù không thuộc CARD/SHEET.
 * Chỉ dùng cho từng bề mặt đơn lẻ, nổi bật (ô tìm Spotlight, thanh tìm Thư viện), không cho chip/nút hàng loạt (JD D9).
 * [lensScale] nhân độ cao + lượng khúc xạ (bề mặt thấp dùng < 1).
 */
fun Modifier.dsGlass(
    surface: DsSurface,
    corner: DsCorner,
    backdrop: Backdrop? = null,
    minBlurDp: Float = 0f,
    frosted: Backdrop? = null,
    realGlass: Boolean = false,
    lensScale: Float = 1f,
    surfaceFill: Boolean = true,
    fixedBlurDp: Float? = null
): Modifier = composed {
    val context = LocalContext.current
    val source = backdrop ?: LocalDsBackdrop.current
    // Chỉ đọc dưới API 31: máy ≥ 31 không phụ thuộc lớp nền mờ (không dựng lại khi nó đổi).
    val frostedSource = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) frosted ?: LocalDsFrostedBackdrop.current else null
    // JS1: màn Tinh chỉnh kính cho thông số đang kéo qua LocalGlassLookOverride (xem trước trước khi lưu).
    val lookState = LocalGlassLookOverride.current ?: rememberGlassLook()
    val look = lookState.value
    val drawable = remember(context, surface, corner) { DsSurfaceDrawable(context, surface, corner) }
    // Độ bóng và bo góc của lớp bề mặt đọc GlassEnvironment lúc dựng: dựng lại khi thông số đổi.
    LaunchedEffect(drawable, look) { drawable.refresh() }

    // Bán kính tính lại khi đo được cỡ hoặc thanh "Bo góc" đổi. Các lambda của drawBackdrop phải ĐỌC state lúc vẽ: node của thư viện
    // giữ lambda của lần dựng đầu, nên lambda bắt sẵn giá trị (bán kính 0 trước khi đo) làm kính vẽ góc vuông (đo được, JC Task 2).
    val radius = remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    if (size != IntSize.Zero) radius.floatValue = DsCorners.radiusPx(context, corner, size.width, size.height, look)
    // Backdrop đòi CornerBasedShape (lens đọc bán kính góc), nên dùng RoundedCornerShape với bán kính px đã tính.
    val shape = RoundedCornerShape(radius.floatValue)
    // [surfaceFill] = false: bỏ lớp bề mặt DS (nền tối ds_surface_fill + viền) trên kính thật, như panel thư mục iOS 26 trong veo (2026-11-23).
    val skipSurface = !surfaceFill && source != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val drawSurface: DrawScope.() -> Unit = {
        if (!skipSurface) {
            drawable.setBounds(0, 0, this.size.width.toInt(), this.size.height.toInt())
            drawIntoCanvas { drawable.draw(it.nativeCanvas) }
        }
    }

    // Chỉ CARD và SHEET lấy mẫu nền (kính thật). Bề mặt đặc (PRIMARY, badge, CONTROL_SELECTED) che kín nền; CONTROL (nút phụ, chip,
    // segmented, ô tìm, nút back) nhỏ nên kính khó thấy mà tốn: chủ dự án chốt 2026-11-19 (JD D9) sau khi đo trên Oppo: catalog ~15 bề
    // mặt kính thật giật 38% khung hình khi cuộn, chỉ kính trên card còn 10%.
    val glass = if (source != null && (surface in REAL_GLASS || realGlass) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.drawBackdrop(
            backdrop = source,
            shape = { RoundedCornerShape(radius.floatValue) },
            effects = {
                val l = lookState.value
                vibrancy()
                // [minBlurDp]: sàn độ mờ cho bề mặt nằm trên nội dung bận (panel thư mục lấy mẫu cả lưới icon), như iOS luôn mờ ở đó.
                // [fixedBlurDp]: độ mờ cố định, bỏ qua thanh "Độ mờ" (panel thư mục phải mờ đúng bằng nền Home phía sau).
                blur((fixedBlurDp ?: maxOf(GlassTuning.backdropBlurDp(l), minBlurDp)).dp.toPx())
                val (height, amount) = GlassTuning.backdropLensDp(l)
                // Thư viện yêu cầu độ cao khúc xạ ≤ bán kính góc.
                // [lensScale]: bề mặt thấp (ô tìm cao 48 dp) bị khúc xạ cả chiều cao nếu dùng số của card; đo trên A17 2026-11-23.
                lens(minOf(height.dp.toPx() * lensScale, radius.floatValue), amount.dp.toPx() * lensScale)
            },
            // F1 (JD): viền sáng kiểu iOS 26 của Backdrop, mạnh/yếu theo thanh "Độ bóng". Bóng đổ vẫn lấy từ DsSurfaceDrawable.
            highlight = {
                val l = lookState.value
                Highlight.Default.copy(
                    width = Highlight.Default.width * GlassTuning.backdropHighlightWidthScale(l),
                    alpha = GlassTuning.backdropHighlightAlpha(l)
                )
            },
            shadow = null,
            onDrawSurface = drawSurface
        )
    } else if (frostedSource != null && (surface in REAL_GLASS || realGlass) && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        // PG3: kính mờ tĩnh cho máy dưới API 31 (Android 8–11); lớp bề mặt DS3 vẽ đè lên như nhánh kính thật.
        Modifier.drawPlainBackdrop(
            backdrop = frostedSource,
            shape = { RoundedCornerShape(radius.floatValue) },
            effects = FrostedNoEffects,
            onDrawSurface = drawSurface
        )
    } else {
        Modifier.drawBehind(drawSurface)
    }

    onSizeChanged { size = it }.then(glass).clip(shape)
}

/**
 * JS1 — thông số kính dùng thay cho thông số đã lưu trong cây con (màn Tinh chỉnh kính xem trước lúc kéo thanh trượt). `null` = đọc
 * `GlassEnvironment` như mọi nơi khác.
 */
val LocalGlassLookOverride: ProvidableCompositionLocal<State<GlassLook>?> = staticCompositionLocalOf { null }

/** Bề mặt được dùng kính Backdrop thật (JD D9). */
private val REAL_GLASS = setOf(DsSurface.CARD, DsSurface.SHEET)

/** JC: overload cũ (nền truyền thẳng). Probe Home và màn 1.1 dùng tới khi chuyển sang `DsScreen` (JD D3). */
fun Modifier.dsGlass(backdrop: Backdrop, surface: DsSurface, corner: DsCorner): Modifier = dsGlass(surface, corner, backdrop)
