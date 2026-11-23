package gambi.global.os26launcher.core.designsystem.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsCornerRole
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.glass.compose.dsGlass
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------------------------------------------
// Công tắc

/**
 * JD Task 5 — công tắc kiểu iOS (F3; bản View: `SwitchCompat` + `ds_switch_track`/`ds_switch_thumb`). Ray 51 × 31 bo tròn, bật
 * `ds_accent`, tắt `ds_surface_control`; núm trắng 27 có bóng. Núm trượt bằng lò xo, nhấn thì núm giãn rộng như iOS, kéo núm được: thả ở
 * nửa nào thì về phía đó và chỉ báo [onCheckedChange] khi trạng thái thật sự đổi.
 *
 * [onCheckedChange] `null` = chỉ hiển thị (trong `DsRow`, hàng nhận chạm và báo trạng thái cho TalkBack).
 */
@Composable
fun DsToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val density = LocalDensity.current
    val travelPx = with(density) { (TRACK_W - THUMB - THUMB_PAD * 2).toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val position = remember { Animatable(if (checked) 1f else 0f) }
    val scope = rememberCoroutineScope()
    val currentOnChange by rememberUpdatedState(onCheckedChange)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dragged by interaction.collectIsDraggedAsState()

    // Trạng thái ngoài đổi (bấm hàng, chủ sở hữu đặt lại) -> núm trượt tới chỗ mới.
    LaunchedEffect(checked) { position.animateTo(if (checked) 1f else 0f, SPRING) }

    val accent = colorResource(R.color.ds_accent)
    val off = colorResource(R.color.ds_surface_control)
    val track by animateColorAsState(if (checked) accent else off, label = "dsToggleTrack")
    val stretch by animateDpAsState(if ((pressed || dragged) && onCheckedChange != null) THUMB_STRETCH else 0.dp, SPRING_DP, label = "dsToggleStretch")

    val interactive = onCheckedChange != null && enabled
    Box(
        modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .then(
                if (interactive) {
                    Modifier
                        .toggleable(value = checked, interactionSource = interaction, indication = null, role = Role.Switch) {
                            currentOnChange?.invoke(it)
                        }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            interactionSource = interaction,
                            state = rememberDraggableState { delta ->
                                val d = if (rtl) -delta else delta
                                scope.launch { position.snapTo((position.value + d / travelPx).coerceIn(0f, 1f)) }
                            },
                            onDragStopped = {
                                val target = position.value >= 0.5f
                                scope.launch { position.animateTo(if (target) 1f else 0f, SPRING) }
                                if (target != checked) currentOnChange?.invoke(target)
                            }
                        )
                } else {
                    Modifier
                }
            )
            .size(TRACK_W, TRACK_H)
            .background(track, RoundedCornerShape(TRACK_H / 2)),
        contentAlignment = Alignment.CenterStart
    ) {
        val thumbW = THUMB + stretch
        // Khi giãn, núm bật giãn về phía trái (giữ mép phải), núm tắt giãn về phía phải: như iOS.
        val x = THUMB_PAD + (TRACK_W - THUMB_PAD * 2 - thumbW) * position.value
        Box(
            Modifier
                .offset { IntOffset((if (rtl) -1 else 1) * with(density) { x.toPx() }.roundToInt(), 0) }
                .size(width = thumbW, height = THUMB)
                .drawBehind {
                    // Bóng núm: vòng đen 30% rộng hơn 1 dp, lệch xuống 1 dp (như ds_switch_thumb).
                    val grow = 1.dp.toPx()
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.3f),
                        topLeft = Offset(-grow / 2, grow / 2),
                        size = size.copy(width = size.width + grow, height = size.height + grow),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2 + grow / 2)
                    )
                }
                .background(colorResource(R.color.ds_text_primary), RoundedCornerShape(THUMB / 2))
        )
    }
}

private val TRACK_W = 51.dp
private val TRACK_H = 31.dp
private val THUMB = 27.dp
private val THUMB_PAD = 2.dp
private val THUMB_STRETCH = 6.dp
private val SPRING = spring<Float>(dampingRatio = 0.7f, stiffness = 600f)
private val SPRING_DP = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.7f, stiffness = 600f)
private const val DISABLED_ALPHA = 0.4f

// ---------------------------------------------------------------------------------------------------------------------------------
// Hàng

/** Phần cuối của [DsRow], như các loại của `DsRowType`. */
sealed interface DsRowTrailing {
    data object None : DsRowTrailing
    data object Chevron : DsRowTrailing
    data class Value(val text: String) : DsRowTrailing
    data class Toggle(val checked: Boolean) : DsRowTrailing
    data class Radio(val selected: Boolean) : DsRowTrailing
}

/**
 * JD Task 5 — hàng DS3 bằng Compose (bản View: `DsRowView` + `ds_row.xml`). Cao tối thiểu `ds_row_min_height` (56), đệm đầu 12, [ ô radio
 * nếu là Radio ] [ [leading] ] [ tiêu đề `body` ≤ 2 dòng / mô tả `caption` ] [ phần cuối, lùi 12 ], khe 12; đường kẻ đáy bắt đầu từ mép
 * trái khối chữ, chạy hết mép phải, mờ dần hai đầu.
 *
 * Trợ năng: cả hàng là MỘT mục (`Switch` khi Toggle, `RadioButton` khi Radio, nút khi có [onClick]); công tắc/radio con không nhận chạm.
 * Hàng không tự đổi trạng thái: [onClick] là chỗ chủ sở hữu lật Toggle hoặc chọn Radio. Nhấn: phủ sáng kiểu iOS (không co, như hàng iOS).
 */
@Composable
fun DsRow(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    description: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: DsRowTrailing = DsRowTrailing.None,
    showDivider: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val gap = dimensionResource(R.dimen.ds_row_gap)
    val primary = colorResource(R.color.ds_text_primary)
    val secondary = colorResource(R.color.ds_text_secondary)
    // Mép trái khối chữ (px), đo sau layout; đọc trong drawBehind nên đổi thì đường kẻ tự vẽ lại.
    val textStartPx = remember { mutableFloatStateOf(0f) }
    val paddingStart = dimensionResource(R.dimen.ds_row_padding_start)

    val click: Modifier = when {
        onClick == null -> Modifier
        trailing is DsRowTrailing.Toggle ->
            Modifier.toggleable(value = trailing.checked, interactionSource = interaction, indication = null, role = Role.Switch) { onClick() }
        trailing is DsRowTrailing.Radio ->
            Modifier.selectable(selected = trailing.selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
        else -> Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
    }
    val divider = colorResource(R.color.ds_divider)

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = dimensionResource(R.dimen.ds_row_min_height))
            .then(click)
            .dsPressHighlight(interaction)
            .drawBehind {
                if (showDivider) {
                    val h = DIVIDER.toPx()
                    // Vị trí khối chữ đo trong phần nội dung (sau đệm đầu hàng); lớp vẽ này đứng trước đệm nên cộng lại đệm.
                    val start = textStartPx.floatValue + paddingStart.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to divider.copy(alpha = 0f), 0.12f to divider, 0.88f to divider, 1f to divider.copy(alpha = 0f),
                            startX = start, endX = size.width
                        ),
                        topLeft = Offset(start, size.height - h),
                        size = androidx.compose.ui.geometry.Size(size.width - start, h)
                    )
                }
            }
            .padding(start = paddingStart),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (trailing is DsRowTrailing.Radio) {
            DsRadioDot(trailing.selected)
            Spacer(Modifier.width(gap))
        }
        if (leading != null) {
            leading()
            Spacer(Modifier.width(gap))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = dimensionResource(R.dimen.ds_row_padding_v))
                .onGloballyPositioned { textStartPx.floatValue = it.positionInParentX() }
        ) {
            BasicText(title, style = DsTextStyles.body.copy(color = primary), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!description.isNullOrEmpty()) {
                BasicText(description, style = DsTextStyles.caption.copy(color = secondary), modifier = Modifier.padding(top = 2.dp))
            }
        }
        val end = dimensionResource(R.dimen.ds_row_padding_end)
        when (trailing) {
            DsRowTrailing.None, is DsRowTrailing.Radio -> Spacer(Modifier.width(end))
            DsRowTrailing.Chevron -> Row(Modifier.padding(start = gap, end = end)) { Chevron(secondary) }
            is DsRowTrailing.Value -> Row(Modifier.padding(start = gap, end = end), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    trailing.text,
                    style = DsTextStyles.value.copy(color = secondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Giá trị dài cắt "…" ở nửa bề rộng cửa sổ, không đẩy chevron ra ngoài (như DsRowView.onMeasure).
                    modifier = Modifier.widthIn(max = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.width / 2).toDp() })
                )
                Spacer(Modifier.width(4.dp))
                Chevron(secondary)
            }
            is DsRowTrailing.Toggle -> Box(Modifier.padding(start = gap, end = end)) { DsToggle(trailing.checked, onCheckedChange = null) }
        }
    }
}

@Composable
private fun Chevron(tint: Color) {
    Image(
        painter = painterResource(R.drawable.ds_ic_chevron_right),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(dimensionResource(R.dimen.ds_icon_size))
    )
}

/** Ô radio 22 dp như `ds_radio.xml`: chọn = tròn `ds_accent` + chấm trắng 8; không chọn = viền 2 dp `ds_text_secondary`. */
@Composable
private fun DsRadioDot(selected: Boolean) {
    val accent = colorResource(R.color.ds_accent)
    val primary = colorResource(R.color.ds_text_primary)
    val secondary = colorResource(R.color.ds_text_secondary)
    Canvas(Modifier.size(22.dp)) {
        if (selected) {
            drawCircle(accent)
            drawCircle(primary, radius = 4.dp.toPx())
        } else {
            val stroke = 2.dp.toPx()
            drawCircle(secondary, radius = 10.dp.toPx() - stroke / 2, style = Stroke(stroke))
        }
    }
}

private fun androidx.compose.ui.layout.LayoutCoordinates.positionInParentX(): Float =
    parentLayoutCoordinates?.let { parent -> parent.localPositionOf(this, Offset.Zero).x } ?: 0f

private val DIVIDER = 1.dp // ds_divider

// ---------------------------------------------------------------------------------------------------------------------------------
// Card nhóm

/** Phạm vi khai báo hàng của [DsCardGroup], kiểu `LazyColumn`: nhóm biết hàng nào là hàng cuối để bỏ đường kẻ. */
class DsCardGroupScope internal constructor() {
    internal val items = ArrayList<@Composable (showDivider: Boolean) -> Unit>()

    /** Một mục tùy ý; [content] nhận `showDivider` (false ở mục cuối). */
    fun item(content: @Composable (showDivider: Boolean) -> Unit) {
        items += content
    }

    /** Một [DsRow]. */
    fun row(
        title: String,
        onClick: (() -> Unit)?,
        description: String? = null,
        leading: (@Composable () -> Unit)? = null,
        trailing: DsRowTrailing = DsRowTrailing.None
    ) = item { showDivider -> DsRow(title, onClick, description = description, leading = leading, trailing = trailing, showDivider = showDivider) }
}

/**
 * JD Task 5 — card nhóm hàng DS3 (bản View: `DsCardGroup`): kính `CARD` bo theo vai `CARD` (theo thanh "Bo góc"), cắt nội dung theo
 * hình bo nên lớp nhấn của hàng không tràn góc; hàng cuối không có đường kẻ.
 */
@Composable
fun DsCardGroup(modifier: Modifier = Modifier, content: DsCardGroupScope.() -> Unit) {
    val scope = DsCardGroupScope().apply(content)
    Column(modifier.fillMaxWidth().dsGlass(DsSurface.CARD, DsCorner.Role(DsCornerRole.CARD))) {
        scope.items.forEachIndexed { i, item -> item(i < scope.items.lastIndex) }
    }
}
