package gambi.global.os26launcher.ui.widget

import android.content.Context
import android.graphics.Paint
import android.text.TextPaint
import gambi.global.os26launcher.R

/**
 * G2c Task 5 — bóng chữ như nhãn icon (đọc được trên ảnh nền bất kỳ), CHỈNH TAY. Sau bài kiểm Live 4 (chữ phụ khó đọc trên vùng ảnh nền
 * sáng): bán kính bóng lấy đúng `icon_label_shadow_radius` (4dp, bản đầu chỉ 2 px) và đậm hơn nhãn icon một chút (70% thay vì 60%).
 */
private const val SHADOW_COLOR = 0xB3000000.toInt()

/** Bút chữ canh giữa theo `x` của `drawText`, có bóng mềm. Dựng một lần trong constructor của renderer. */
internal fun newWidgetTextPaint(context: Context, color: Int, bold: Boolean = false): TextPaint =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textAlign = Paint.Align.CENTER
        isFakeBoldText = bold
        setShadowLayer(
            context.resources.getDimension(R.dimen.icon_label_shadow_radius),
            0f,
            0f,
            SHADOW_COLOR
        )
    }

/** Đặt cỡ chữ [size] px, thu nhỏ nếu [text] rộng hơn [maxWidth]. */
internal fun TextPaint.fitWidth(text: String, maxWidth: Float, size: Float) {
    textSize = size
    val width = measureText(text)
    if (width > maxWidth && width > 0f) textSize = size * maxWidth / width
}

/** `y` của baseline để chữ cỡ hiện tại nằm giữa theo chiều dọc quanh [centerY]. */
internal fun TextPaint.baselineAround(centerY: Float): Float {
    val fm = fontMetrics
    return centerY - (fm.ascent + fm.descent) / 2f
}
