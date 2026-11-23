package gambi.global.os26launcher.ui.home.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import gambi.global.os26launcher.R
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.compose.dsPressHighlight
import gambi.global.os26launcher.core.glass.compose.dsGlass
import kotlin.random.Random

/**
 * JH2 Task 2 — rung khi ở edit mode, như `Jiggler`: xoay ±2° tuyến tính 160 ms, lặp đảo chiều, pha ngẫu nhiên cho mỗi ô (các ô không rung
 * cùng nhịp). Widget rung nửa biên độ ([scale] = 0.5). Tắt thì không dựng transition nào (không tốn khung hình khi Home đứng yên).
 */
fun Modifier.homeJiggle(enabled: Boolean, scale: Float = 1f): Modifier = if (!enabled) this else composed {
    val phase = remember { (Random.nextFloat() * JIGGLE_MS).roundToInt() }
    val transition = rememberInfiniteTransition(label = "jiggle")
    val t by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(JIGGLE_MS, easing = LinearEasing), RepeatMode.Reverse, StartOffset(phase)),
        label = "jiggleAngle"
    )
    graphicsLayer { rotationZ = t * JIGGLE_DEGREES * scale }
}

/**
 * JH2 Task 2 — dấu "−" của edit mode, như `IconCellView.drawBadge`: đường kính [MINUS_BADGE_RATIO] (0,30) × icon, tâm lùi 0,35 bán kính vào trong từ góc trên-trái
 * icon (widget: tâm ở (r/2, r/2) của thẻ), nền xám sáng có bóng, gạch ngang đậm. Vùng chạm 48 dp quanh tâm ([centerX], [centerY]: toạ
 * độ trong ô cha).
 */
@Composable
fun HomeRemoveBadge(iconSizePx: Int, centerX: Float, centerY: Float, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val density = LocalDensity.current
    val hit = dimensionResource(R.dimen.icon_badge_hit_size)
    val hitPx = with(density) { hit.toPx() }
    val diameter = iconSizePx * BADGE_RATIO
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            // Cha có thể ép cỡ tối thiểu (ô widget: Box propagateMinConstraints): requiredSize khi đó bị căn GIỮA ô, làm dấu "−" lệch vào
            // giữa widget. Bỏ ràng buộc tối thiểu và neo góc trên-trái để offset luôn tính từ góc ô.
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .offset { IntOffset((centerX - hitPx / 2).roundToInt(), (centerY - hitPx / 2).roundToInt()) }
            .requiredSize(hit)
            .semantics { contentDescription = label }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.requiredSize(with(density) { diameter.toDp() })) {
            val r = size.minDimension / 2f
            drawIntoCanvas { canvas ->
                val paint = Paint().apply { color = BADGE_FILL }
                paint.asFrameworkPaint().setShadowLayer(r * 0.25f, 0f, r * 0.08f, BADGE_SHADOW.toArgb())
                canvas.drawCircle(center, r, paint)
            }
            val half = r * 0.5f
            drawLine(BADGE_GLYPH, Offset(center.x - half, center.y), Offset(center.x + half, center.y), strokeWidth = r * 0.18f, cap = StrokeCap.Round)
        }
    }
}

/** Tâm dấu "−" của ô app/thư mục: góc trên-trái icon + 0,35 × bán kính dấu. */
fun badgeCenterForIcon(iconLeft: Float, iconTop: Float, iconSizePx: Int): Offset {
    val r = iconSizePx * BADGE_RATIO / 2f
    return Offset(iconLeft + r * BADGE_INSET_RATIO, iconTop + r * BADGE_INSET_RATIO)
}

/**
 * JH2 Task 2 — thanh nút của edit mode (bản Compose của `edit_toolbar`). P2-9e (REF a013dd8): "Sửa" bên trái mở menu kính
 * ([HomeEditMenu]: Thêm tiện ích / Hình nền), "Xong" (đậm) bên phải; bỏ "Cài đặt" và "Tiện ích" riêng (Cài đặt vào từ icon app và Trung tâm
 * điều khiển). Mỗi nút cao 48 dp vùng chạm, viên nang 28 dp nền `search_scrim`, chữ trắng 14 sp, sát mép trên màn hình (thanh trạng thái ẩn
 * trong edit mode).
 */
@Composable
fun HomeEditToolbar(
    onEdit: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** P2-5: vùng (toạ độ gốc) của từng nút, khóa = thứ tự nút (0 = "Sửa", 2 = "Xong"); null = nút gỡ. Cử chỉ gốc không coi vùng nút là vùng trống. */
    onPillBounds: (Int, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> }
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = dimensionResource(R.dimen.home_edit_toolbar_margin_horizontal)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditPill(stringResource(R.string.home_edit), bold = false, onClick = onEdit, onBounds = { onPillBounds(0, it) })
        Spacer(Modifier.weight(1f))
        EditPill(stringResource(R.string.home_done), bold = true, onClick = onDone, onBounds = { onPillBounds(2, it) })
    }
}

/** P2-9e: một mục của menu "Sửa". */
class HomeEditMenuItem(@androidx.annotation.StringRes val label: Int, @androidx.annotation.DrawableRes val icon: Int, val action: () -> Unit)

/**
 * P2-9e (REF `EditHomeMenu`): menu thả xuống của nút "Sửa", cùng kiểu menu nhấn giữ icon (`HomeIconMenu`: hàng icon 20 dp tô màu chữ, nền kính
 * CARD + lớp sáng `shortcut_menu_glass_tint`). Vẽ TRONG cửa sổ Home (không Popup) để kính lấy mẫu được ảnh nền. Ngay dưới [anchor] (toạ độ gốc),
 * thẳng mép trái nút; một lớp phủ trong suốt toàn màn bắt chạm ra ngoài để đóng (nuốt chạm). Mở: phóng từ góc trên-trái 0,85 -> 1 + hiện dần 180 ms.
 */
@Composable
fun HomeEditMenu(anchor: androidx.compose.ui.geometry.Rect, items: List<HomeEditMenuItem>, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val gap = with(density) { EDIT_MENU_GAP.roundToPx() }
    val open = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        open.animateTo(1f, tween(EDIT_MENU_OPEN_MS, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    onDismiss()
                }
            }
    ) {
        Column(
            Modifier
                .offset { IntOffset(anchor.left.roundToInt(), anchor.bottom.roundToInt() + gap) }
                .graphicsLayer {
                    val s = EDIT_MENU_OPEN_SCALE + (1f - EDIT_MENU_OPEN_SCALE) * open.value
                    scaleX = s
                    scaleY = s
                    alpha = open.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                }
                .widthIn(dimensionResource(R.dimen.shortcut_menu_min_width), dimensionResource(R.dimen.shortcut_menu_max_width))
                .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
                .dsGlass(DsSurface.CARD, DsCorner.Fixed(EDIT_MENU_RADIUS_DP, glass = true))
                .background(colorResource(R.color.shortcut_menu_glass_tint))
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } } // chạm trong thẻ (ngoài hàng) không đóng
                .padding(
                    horizontal = dimensionResource(R.dimen.shortcut_menu_padding_horizontal),
                    vertical = dimensionResource(R.dimen.shortcut_menu_padding_vertical)
                )
        ) {
            items.forEach { item ->
                EditMenuRow(item) {
                    onDismiss()
                    item.action()
                }
            }
        }
    }
}

@Composable
private fun EditMenuRow(item: HomeEditMenuItem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val color = colorResource(R.color.shortcut_menu_text)
    val iconSize = dimensionResource(R.dimen.shortcut_menu_icon_size)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = dimensionResource(R.dimen.shortcut_menu_row_inset))
            .height(dimensionResource(R.dimen.shortcut_menu_row_height))
            .clip(RoundedCornerShape(dimensionResource(R.dimen.shortcut_menu_row_radius)))
            .dsPressHighlight(interaction, darkPress = true)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(
                start = dimensionResource(R.dimen.shortcut_menu_row_padding_start),
                end = dimensionResource(R.dimen.shortcut_menu_row_padding_end)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(item.icon),
            contentDescription = null,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color),
            modifier = Modifier.size(iconSize)
        )
        Spacer(Modifier.width(dimensionResource(R.dimen.shortcut_menu_icon_padding)))
        BasicText(
            stringResource(item.label),
            style = TextStyle(color = color, fontSize = with(LocalDensity.current) { dimensionResource(R.dimen.shortcut_menu_text_size).toSp() }),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

private val EDIT_MENU_GAP = 6.dp
private const val EDIT_MENU_OPEN_SCALE = 0.85f
private const val EDIT_MENU_OPEN_MS = 180
private const val EDIT_MENU_RADIUS_DP = 24f // như MENU_RADIUS_DP của HomeIconMenu
private const val EDIT_PILL_LENS_SCALE = 0.5f // viên thuốc cao 28 dp: khúc xạ của card quá mạnh (như ô tìm Thư viện)


@Composable
private fun EditPill(text: String, bold: Boolean, onClick: () -> Unit, onBounds: (androidx.compose.ui.geometry.Rect?) -> Unit = {}) {
    val latestBounds by androidx.compose.runtime.rememberUpdatedState(onBounds)
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { latestBounds(null) } }
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .height(dimensionResource(R.dimen.home_edit_pill_touch))
            .onGloballyPositioned { latestBounds(it.boundsInRoot()) }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .height(dimensionResource(R.dimen.home_edit_pill_height))
                // 2026-11-23 (chủ dự án): nền nút là liquid glass thật (khúc xạ mép, viền sáng, theo thông số kính) thay nền đen 80 % `search_scrim`.
                // Viên thuốc nhỏ: khúc xạ thu nửa như ô tìm (lensScale). Dưới API 31: kính mờ tĩnh / bề mặt CONTROL.
                .dsGlass(DsSurface.CONTROL, DsCorner.Pill, realGlass = true, lensScale = EDIT_PILL_LENS_SCALE)
                .padding(horizontal = dimensionResource(R.dimen.home_edit_pill_padding_horizontal)),
            contentAlignment = Alignment.Center
        ) {
            BasicText(text, style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
        }
    }
}

// Cùng số với Jiggler / IconCellView (companion private).
private const val JIGGLE_MS = 160
private const val JIGGLE_DEGREES = 2f
const val WIDGET_JIGGLE_SCALE = 0.5f
/** Polish 2026-11-19 (IconCellView.MINUS_BADGE_RATIO): cạnh dấu "−" / cạnh icon, tách khỏi huy hiệu thông báo (0,38) và nhỏ lại. */
const val MINUS_BADGE_RATIO = 0.30f
private const val BADGE_RATIO = MINUS_BADGE_RATIO
private const val BADGE_INSET_RATIO = 0.35f
private val BADGE_FILL = Color(0xF2E5E5EA)
private val BADGE_SHADOW = Color(0x40000000)
private val BADGE_GLYPH = Color(0xFF1C1C1E)

