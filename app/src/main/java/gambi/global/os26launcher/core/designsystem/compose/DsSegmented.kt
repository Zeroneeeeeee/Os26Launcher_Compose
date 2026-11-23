package gambi.global.os26launcher.core.designsystem.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.glass.compose.dsGlass
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * JD Task 6 — segmented DS3 bằng Compose (bản View: `DsSegmentedControl`, Figma Segmented 312:2671 + Segment 302:3344). Khung kính
 * `CONTROL` bo PILL cao nhìn thấy 36 trong vùng chạm 48, đệm 3, khe 2; mỗi lựa chọn cao 30, đệm ngang 14, chữ `body`. Lựa chọn đang
 * chọn: viên `ds_text_primary` bo tròn, chữ `ds_on_light` 600. [fill] = chia đều bề rộng, ngược lại ôm chữ.
 *
 * F4 (JD): viên chọn TRƯỢT từ lựa chọn cũ sang lựa chọn mới bằng lò xo (bản View nhảy tức thì). Chỉ báo [onSelect] khi chọn lựa chọn
 * khác. TalkBack: một nhóm chọn một, mỗi lựa chọn là RadioButton có vị trí trong nhóm, như bản View.
 */
@Composable
fun DsSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = true
) {
    val density = LocalDensity.current
    val touch = dimensionResource(R.dimen.ds_touch_min)
    val inset = ((touch - VISIBLE) / 2).coerceAtLeast(0.dp)
    // Vị trí/bề rộng từng lựa chọn (px trong hàng), đo sau layout; viên chọn trượt giữa các giá trị này.
    val bounds = remember(options.size) { mutableStateListOf(*Array(options.size) { 0f to 0f }) }
    val thumbX = remember { Animatable(0f) }
    val thumbW = remember { Animatable(0f) }
    val selected = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    val target = bounds.getOrNull(selected)

    LaunchedEffect(selected, target) {
        val (x, w) = target ?: return@LaunchedEffect
        if (w <= 0f) return@LaunchedEffect
        if (thumbW.value <= 0f) {
            // Lần đầu: đặt thẳng, không trượt từ 0.
            thumbX.snapTo(x)
            thumbW.snapTo(w)
        } else {
            launch { thumbX.animateTo(x, SPRING) }
            thumbW.animateTo(w, SPRING)
        }
    }

    val primary = colorResource(R.color.ds_text_primary)
    val onLight = colorResource(R.color.ds_on_light)

    Box(
        modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .padding(vertical = inset)
            .height(VISIBLE)
            .dsGlass(DsSurface.CONTROL, DsCorner.Pill)
            .padding(PAD),
        contentAlignment = Alignment.CenterStart
    ) {
        if (thumbW.value > 0f) {
            Box(
                Modifier
                    .offset { IntOffset(thumbX.value.roundToInt(), 0) }
                    .width(with(density) { thumbW.value.toDp() })
                    .height(OPTION_H)
                    .background(primary, RoundedCornerShape(OPTION_H / 2))
            )
        }
        Row(
            Modifier
                .then(if (fill) Modifier.fillMaxWidth() else Modifier)
                .selectableGroup()
                .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = options.size) },
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier
                        .then(if (fill) Modifier.weight(1f) else Modifier)
                        .height(OPTION_H)
                        .onGloballyPositioned { c ->
                            if (i < bounds.size) bounds[i] = c.positionInParent().x to c.size.width.toFloat()
                        }
                        .selectable(
                            selected = on,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { if (!on) onSelect(i) }
                        )
                        .semantics { collectionItemInfo = CollectionItemInfo(0, 1, i, 1) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = label,
                        style = DsTextStyles.body.copy(
                            color = if (on) onLight else primary,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private val VISIBLE = 36.dp
private val PAD = 3.dp
private val GAP = 2.dp
private val OPTION_H = 30.dp
private val SPRING = spring<Float>(dampingRatio = 0.75f, stiffness = 500f)
