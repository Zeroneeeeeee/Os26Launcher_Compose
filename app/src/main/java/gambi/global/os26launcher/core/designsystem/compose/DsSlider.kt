package gambi.global.os26launcher.core.designsystem.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import kotlin.math.roundToInt

/**
 * JD Task 6 — hàng thanh trượt DS3 bằng Compose (bản View: `DsSliderRow` + `ds_slider_row.xml`, Figma Slider 302:3493): nhãn `body`
 * rộng 104 (≤ 2 dòng), khe 12, thanh trượt 0..100 (vùng chạm cao 48, ray 4 dp bo 2 màu `ds_surface_control`, phần đã chọn `ds_accent`,
 * núm 10 dp trắng viền 1 dp `ds_accent_light` có vòng bóng đen 30%, lề 6 dp hai đầu), giá trị "x%" chữ `caption` rộng 34 căn phải.
 *
 * Chạm vào ray thì nhảy tới đó, kéo thì chạy theo ngón (lệch dọc khỏi núm vẫn kéo được); [onTrackingChange] báo lúc bắt đầu/nhả tay để màn
 * chứa chỉ ghi khi nhả. TalkBack: một thanh trượt, mô tả là [label], giá trị là phần trăm; vuốt lên/xuống đổi giá trị.
 */
@Composable
fun DsSliderRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onTrackingChange: ((Boolean) -> Unit)? = null
) {
    val v = value.coerceIn(0, 100)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnTracking by rememberUpdatedState(onTrackingChange)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val primary = colorResource(R.color.ds_text_primary)
    val accent = colorResource(R.color.ds_accent)
    val accentLight = colorResource(R.color.ds_accent_light)
    val rail = colorResource(R.color.ds_surface_control)
    val percent = "$v%"

    Row(modifier.fillMaxWidth().heightIn(min = dimensionResource(R.dimen.ds_touch_min)), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = DsTextStyles.body.copy(color = primary), maxLines = 2, modifier = Modifier.width(104.dp))
        Spacer(Modifier.width(12.dp))
        Canvas(
            Modifier
                .weight(1f)
                .height(dimensionResource(R.dimen.ds_touch_min))
                .clearAndSetSemantics {
                    contentDescription = label
                    stateDescription = percent
                    progressBarRangeInfo = ProgressBarRangeInfo(v.toFloat(), 0f..100f, steps = 99)
                    setProgress { target ->
                        val next = target.roundToInt().coerceIn(0, 100)
                        if (next != v) currentOnChange(next)
                        true
                    }
                }
                // Kéo: chỉ khi ngón đi NGANG vượt ngưỡng chạm; đi dọc trước thì nhường cho màn cuộn (đo được trên Oppo: đặt giá trị ngay
                // lúc chạm xuống làm cú vuốt cuộn màn đổi luôn giá trị 52 -> 16).
                .pointerInput(rtl) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            ?: return@awaitEachGesture
                        currentOnTracking?.invoke(true)
                        currentOnChange(valueAt(start.position.x, rtl))
                        horizontalDrag(start.id) { change ->
                            change.consume()
                            currentOnChange(valueAt(change.position.x, rtl))
                        }
                        currentOnTracking?.invoke(false)
                    }
                }
                // Chạm gọn: nhảy tới điểm chạm khi nhấc tay (bị màn cuộn giành thì không tính).
                .pointerInput(rtl) {
                    detectTapGestures { pos ->
                        currentOnTracking?.invoke(true)
                        currentOnChange(valueAt(pos.x, rtl))
                        currentOnTracking?.invoke(false)
                    }
                }
        ) {
            val edge = EDGE.toPx()
            val trackH = TRACK_H.toPx()
            val cy = size.height / 2
            val span = size.width - edge * 2
            val f = v / 100f
            val thumbX = if (rtl) edge + span * (1f - f) else edge + span * f
            val radius = CornerRadius(trackH / 2)
            drawRoundRect(rail, Offset(edge, cy - trackH / 2), Size(span, trackH), radius)
            val activeStart = if (rtl) thumbX else edge
            drawRoundRect(accent, Offset(activeStart, cy - trackH / 2), Size(span * f, trackH), radius)
            // Núm: vòng bóng đen 30% lệch xuống 1 dp, rồi tròn trắng viền ds_accent_light (như ds_slider_thumb.xml).
            val r = THUMB.toPx() / 2
            drawCircle(Color.Black.copy(alpha = 0.3f), radius = r + 0.5.dp.toPx(), center = Offset(thumbX, cy + 1.dp.toPx()))
            drawCircle(primary, radius = r, center = Offset(thumbX, cy))
            drawCircle(accentLight, radius = r - 0.5.dp.toPx(), center = Offset(thumbX, cy), style = Stroke(1.dp.toPx()))
        }
        BasicText(
            percent,
            style = DsTextStyles.caption.copy(color = primary, textAlign = TextAlign.End),
            maxLines = 1,
            modifier = Modifier.width(34.dp)
        )
    }
}

/** Giá trị 0..100 ở toạ độ [x] của ray (trừ lề [EDGE] hai đầu; RTL thì đảo). */
private fun androidx.compose.ui.input.pointer.PointerInputScope.valueAt(x: Float, rtl: Boolean): Int {
    val edge = EDGE.toPx()
    val span = (size.width - edge * 2).coerceAtLeast(1f)
    val f = ((x - edge) / span).coerceIn(0f, 1f)
    return ((if (rtl) 1f - f else f) * 100f).roundToInt()
}

private val EDGE = 6.dp
private val TRACK_H = 4.dp
private val THUMB = 10.dp
