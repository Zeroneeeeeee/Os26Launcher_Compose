package gambi.global.os26launcher.ui.controlcenter.compose

import android.graphics.Bitmap
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.compose.drawDrawable
import gambi.global.os26launcher.ui.controlcenter.CcGlassDrawable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

// JS7b: các mảnh dùng chung của panel Compose (kính, chữ, ô tròn, cử chỉ). Hình kính vẫn là CcGlassDrawable của CG, vẽ lên canvas
// Compose bằng drawDrawable nên trông y như bản View.

/** Dịch, co, mờ của một khối theo [CcBlockAnim]; đặt đầu chuỗi để chạm cũng theo hình đã biến đổi (như View). */
internal fun Modifier.ccBlock(anim: CcBlockAnim): Modifier = graphicsLayer {
    alpha = anim.alpha
    scaleX = anim.scale
    scaleY = anim.scale
    translationY = anim.translationY
}

/** Nhớ một [CcGlassDrawable] theo hình; [fill] null = màu tô mặc định `cc_tile_bg`. */
@Composable
internal fun rememberCcGlass(
    shape: CcGlassDrawable.Shape = CcGlassDrawable.Shape.ROUNDED,
    cornerRadius: Float = -1f,
    @ColorRes fill: Int? = null,
    rim: Boolean = true
): CcGlassDrawable {
    val context = LocalContext.current
    val fillColor = fill?.let { colorResource(it).toArgb() }
    return remember(context, shape, cornerRadius, fillColor, rim) {
        if (fillColor == null) CcGlassDrawable(context, shape, cornerRadius, rim = rim)
        else CcGlassDrawable(context, shape, cornerRadius, fillColor, rim)
    }
}

/** Nền kính CG phủ cả khung. */
internal fun Modifier.ccGlass(glass: CcGlassDrawable): Modifier = drawBehind { drawDrawable(glass) }

/**
 * JS7b: báo gốc panel rằng một phần tử con đã nhận cú chạm (thay `isClickable` của View cũ): nhả tay tại chỗ không đóng panel.
 * [onDown] chạy lúc chạm xuống (hoạt ảnh nhấn của ô).
 */
internal fun Modifier.ccClaimTouch(panel: ControlCenterPanel, onDown: (() -> Unit)? = null): Modifier = composed {
    val down by rememberUpdatedState(onDown)
    pointerInput(panel) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            panel.childTouched = true
            down?.invoke()
        }
    }
}

/**
 * JS7b: nhấn giữ cả một khối (kết nối, nhạc) kể cả khi ngón đặt trên một nút con (RE mục 5.2). Đọc ở pass Initial nên thấy chạm
 * trước nút con; khi đã bắn thì tiêu thụ phần còn lại của cú chạm để nút con không nhận thêm cú bấm (như `performLongClick`). Huỷ khi
 * nhả tay, khi ngón ra khỏi khối (cộng slop) hoặc khi gốc panel đã giành cú chạm để đổi trang.
 */
internal fun Modifier.ccLongPressGroup(panel: ControlCenterPanel, onLongPress: () -> Unit): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val callback by rememberUpdatedState(onLongPress)
    pointerInput(panel) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            panel.childTouched = true
            val slop = viewConfiguration.touchSlop
            val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var stop = false
                while (!stop) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val c = event.changes.firstOrNull { it.id == down.id }
                    stop = c == null || !c.pressed || c.isConsumed ||
                            c.position.x < -slop || c.position.y < -slop ||
                            c.position.x > size.width + slop || c.position.y > size.height + slop
                }
                true
            } ?: false
            if (cancelled) return@awaitEachGesture
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            callback()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

/** Chữ trắng của panel (TextView `white()`/`ccText()` cũ): font hệ thống, một dòng, cắt đuôi; [marquee] = chạy chữ như app mẫu. */
@Composable
internal fun CcText(
    text: String,
    sizeSp: Float,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    alpha: Float = 1f,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
    marquee: Boolean = false,
    color: Color = colorResource(R.color.cc_icon)
) {
    val style = TextStyle(
        color = color.copy(alpha = color.alpha * alpha),
        fontSize = sizeSp.sp,
        fontWeight = if (bold) FontWeight.Bold else null,
        textAlign = align,
        platformStyle = TEXT_VIEW_PADDING
    )
    if (marquee) {
        BasicText(text, modifier.basicMarquee(iterations = Int.MAX_VALUE), style = style, maxLines = 1, softWrap = false)
    } else {
        BasicText(text, modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** TextView mặc định có đệm font; giữ để chiều cao chữ (và bố cục khối nhạc, trang Mạng) khớp bản View. */
@Suppress("DEPRECATION")
private val TEXT_VIEW_PADDING = PlatformTextStyle(includeFontPadding = true)

/** Icon trắng (ImageView + `setColorFilter(cc_icon)` cũ), FIT_CENTER. */
@Composable
internal fun CcIcon(@DrawableRes res: Int, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painterResource(res), null, modifier.size(size),
        colorFilter = ColorFilter.tint(colorResource(R.color.cc_icon))
    )
}

/** Vẽ [painter] vuông cạnh [sizePx] ở ([left], [top]). */
internal fun DrawScope.drawIconAt(painter: Painter, left: Int, top: Int, sizePx: Int, tint: Color?, alpha: Float = 1f) {
    if (sizePx <= 0) return
    translate(left.toFloat(), top.toFloat()) {
        with(painter) { draw(Size(sizePx.toFloat(), sizePx.toFloat()), alpha, tint?.let { ColorFilter.tint(it) }) }
    }
}

/**
 * JS7b: một nút tròn của panel (thay `ControlTileView`, mọi chỗ dùng đều là hình tròn). Nền kính tròn: tắt thì kính trong mờ, icon
 * trắng; bật thì kính tô [onBackground], icon [onIconColor]. [glass] = false: chỉ icon (nút cài đặt ở góc). [checkable] = công tắc
 * (TalkBack đọc Bật/Tắt). [appIcon] != null: icon app, giữ màu gốc. Chạm xuống thì co 1 → 0,96 rồi về trong 200 ms (RE mục 5.2, `m41`).
 */
@Composable
internal fun CcTile(
    panel: ControlCenterPanel,
    @DrawableRes icon: Int,
    label: String,
    on: Boolean,
    onBackground: Color,
    onIconColor: Color,
    modifier: Modifier = Modifier,
    checkable: Boolean = true,
    glass: Boolean = true,
    enabled: Boolean = true,
    appIcon: Bitmap? = null,
    onClick: () -> Unit
) {
    val glassBg = rememberCcGlass(CcGlassDrawable.Shape.CIRCLE)
    val offBackground = colorResource(R.color.cc_tile_bg)
    val offIcon = colorResource(R.color.cc_icon)
    val vector = painterResource(icon)
    val bitmapPainter = remember(appIcon) { appIcon?.let { BitmapPainter(it.asImageBitmap()) } }
    val painter = bitmapPainter ?: vector
    val scope = rememberCoroutineScope()
    val pulse = remember { Animatable(1f) }
    val interaction = remember { MutableInteractionSource() }
    val fraction = when {
        bitmapPainter != null -> APP_ICON_FRACTION
        !glass -> BARE_ICON_FRACTION
        else -> ICON_FRACTION
    }
    Box(
        modifier
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            }
            .ccClaimTouch(panel) {
                if (enabled) scope.launch {
                    pulse.snapTo(1f)
                    pulse.animateTo(PRESS_SCALE, tween(PRESS_MS, easing = LinearEasing))
                    pulse.animateTo(1f, tween(PRESS_MS, easing = LinearEasing))
                }
            }
            .semantics {
                contentDescription = label
                if (checkable) toggleableState = ToggleableState(on)
            }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .drawBehind {
                if (glass) {
                    glassBg.fillColor = (if (on) onBackground else offBackground).toArgb()
                    drawDrawable(glassBg)
                }
                // Icon theo cỡ ô (ô 1×1 lớn, nút nhỏ trong khối kết nối đều cân), như ảnh iOS 26.
                val w = size.width
                val h = size.height
                val s = (min(w, h) * fraction).toInt()
                val l = ((w - s) / 2f).toInt()
                val t = ((h - s) / 2f).toInt()
                drawIconAt(painter, l, t, s, if (bitmapPainter != null) null else if (on) onIconColor else offIcon)
            }
    )
}

/** Ô 1×1 trên trang chính: kính tròn; bật thì nền trắng, icon màu [onColor] (như `squareTile` cũ). */
@Composable
internal fun CcSquareTile(
    panel: ControlCenterPanel,
    @DrawableRes icon: Int,
    label: String,
    on: Boolean,
    @ColorRes onColor: Int,
    modifier: Modifier = Modifier,
    checkable: Boolean = true,
    enabled: Boolean = true,
    appIcon: Bitmap? = null,
    onClick: () -> Unit
) = CcTile(
    panel, icon, label, on,
    onBackground = colorResource(R.color.cc_tile_bg_on),
    onIconColor = colorResource(onColor),
    modifier = modifier, checkable = checkable, enabled = enabled, appIcon = appIcon, onClick = onClick
)

/** Nhãn TalkBack của một nút có long-click (khối kết nối/nhạc, thanh trượt). */
internal fun Modifier.ccLongClickSemantics(label: String, onLongPress: () -> Unit): Modifier = semantics {
    contentDescription = label
    onLongClick {
        onLongPress()
        true
    }
}

/** Chuỗi hiển thị một mức thời gian chờ, như app mẫu (`yeb.i()`): 15s, 30s, 1m... */
@Composable
internal fun ccTimeoutLabel(ms: Int): String {
    val s = ms / 1000
    return if (s < 60) stringResource(R.string.cc_timeout_seconds, s) else stringResource(R.string.cc_timeout_minutes, s / 60)
}

private const val PRESS_SCALE = 0.96f
private const val PRESS_MS = 200
private const val APP_ICON_FRACTION = 0.56f
private const val ICON_FRACTION = 0.4f
private const val BARE_ICON_FRACTION = 0.62f
