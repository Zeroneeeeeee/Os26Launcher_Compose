package gambi.global.os26launcher.ui.controlcenter

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsCornerRole
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.compose.DsButton
import gambi.global.os26launcher.core.designsystem.compose.DsButtonSize
import gambi.global.os26launcher.core.designsystem.compose.DsButtonVariant
import gambi.global.os26launcher.core.designsystem.compose.DsSliderRow
import gambi.global.os26launcher.core.designsystem.compose.DsTextStyles
import gambi.global.os26launcher.core.glass.compose.dsGlass
import kotlin.math.roundToInt

/**
 * JS7 — khối dựng màn cài đặt con của Trung tâm điều khiển bằng Compose (thay `CcForm` View + Material): cột cuộn với lề màn, tiêu đề nhóm,
 * nhóm kính `CARD`, hàng thanh trượt theo khoảng số thực, ghi chú chân trang.
 */
@Composable
internal fun CcFormColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val margin = dimensionResource(R.dimen.ds_screen_margin_h)
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = margin, end = margin, top = 8.dp, bottom = 32.dp)) {
        content()
    }
}

@Composable
internal fun CcHeader(text: String) {
    BasicText(
        text,
        style = DsTextStyles.sectionHeader.copy(color = colorResource(R.color.ds_text_secondary)),
        modifier = Modifier.padding(top = 20.dp, bottom = 12.dp).semantics { heading() }
    )
}

@Composable
internal fun CcGroup(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().dsGlass(DsSurface.CARD, DsCorner.Role(DsCornerRole.CARD))) { content() }
}

@Composable
internal fun CcNote(text: String) {
    BasicText(
        text,
        style = DsTextStyles.caption.copy(color = colorResource(R.color.ds_text_tertiary)),
        modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp)
    )
}

/**
 * Thanh trượt cho giá trị thực [from]..[to] (quy về 0..100 của `DsSliderRow`): lúc kéo tay chỉ hiện, nhả tay mới gọi [onCommit]; đổi bằng
 * TalkBack (không có cử chỉ kéo) thì ghi ngay, như `CcForm.sliderRow`.
 */
@Composable
internal fun CcSlider(label: String, value: Float, from: Float, to: Float, onCommit: (Float) -> Unit) {
    fun toPct(v: Float) = (((v - from) / (to - from)).coerceIn(0f, 1f) * 100f).roundToInt()
    fun fromPct(p: Int) = from + (to - from) * p / 100f
    var pct by remember(value) { mutableIntStateOf(toPct(value)) }
    var tracking by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        DsSliderRow(
            label = label,
            value = pct,
            onValueChange = {
                pct = it
                if (!tracking) onCommit(fromPct(it))
            },
            onTrackingChange = { now ->
                tracking = now
                if (!now) onCommit(fromPct(pct))
            }
        )
    }
}

/** Ô màu tròn (dải mép): ô mẫu luôn đục để nhìn rõ màu; ô đang chọn viền dày. */
@Composable
internal fun CcSwatch(color: Int, selected: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(dimensionResource(R.dimen.cc_swatch))
            .clip(CircleShape)
            .background(Color(color or 0xFF000000.toInt()))
            .border(if (selected) 4.dp else 1.dp, Color(0xFF8E8E93), CircleShape)
            .semantics { contentDescription = description; this.selected = selected }
            .clickable(interactionSource = null, indication = null, role = Role.RadioButton, onClick = onClick)
    )
}

/**
 * Chọn giờ (thay `TimePickerDialog` View): hai cột giờ / phút với nút −/+ (phút bước 5), hiển thị "H:mm". Dùng làm nội dung của `DsAlertDialog`.
 */
@Composable
internal fun CcTimeStepper(minutes: Int, onChange: (Int) -> Unit) {
    val h = minutes / 60
    val m = minutes % 60
    val primary = colorResource(R.color.ds_text_primary)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Stepper("%d".format(h), onMinus = { onChange(((h + 23) % 24) * 60 + m) }, onPlus = { onChange(((h + 1) % 24) * 60 + m) })
        BasicText(":", style = DsTextStyles.headingLarge.copy(color = primary))
        Stepper("%02d".format(m), onMinus = { onChange(h * 60 + (m + 55) % 60) }, onPlus = { onChange(h * 60 + (m + 5) % 60) })
    }
}

@Composable
private fun Stepper(text: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DsButton("+", onClick = onPlus, variant = DsButtonVariant.Secondary, size = DsButtonSize.S)
        BasicText(
            text,
            style = DsTextStyles.headingLarge.copy(color = colorResource(R.color.ds_text_primary), textAlign = TextAlign.Center),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        DsButton("−", onClick = onMinus, variant = DsButtonVariant.Secondary, size = DsButtonSize.S)
    }
}
