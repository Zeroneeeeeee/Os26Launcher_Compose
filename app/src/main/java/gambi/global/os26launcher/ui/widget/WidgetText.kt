package gambi.global.os26launcher.ui.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import gambi.global.os26launcher.R

/**
 * G2c Task 5 — bóng chữ như nhãn icon (đọc được trên ảnh nền bất kỳ), CHỈNH TAY. Sau bài kiểm Live 4 (chữ phụ khó đọc trên vùng ảnh nền
 * sáng): bán kính bóng lấy đúng `icon_label_shadow_radius` (4dp, bản đầu chỉ 2 px) và đậm hơn nhãn icon một chút (70% thay vì 60%).
 */
private const val SHADOW_COLOR = 0xB3000000.toInt()

/**
 * PW (chủ dự án 2026-11-25: "giống font iOS nhất có thể") — font chữ của widget tự vẽ. 2026-11-26: chủ dự án tự thêm **SF Pro Display**
 * vào `res/font` và yêu cầu thay hẳn Inter. Lưu ý giấy phép: SF Pro của Apple chỉ cho làm giao diện chạy trên nền tảng Apple
 * (`docs/lessons/widgets.md`), chủ dự án tự quyết việc nhúng. Mọi cỡ chữ đều dùng SF Pro Display (không có bản Text trong dự án).
 */
internal enum class WidgetFont(val res: Int) {
    REGULAR(R.font.sf_pro_display_regular),
    SEMIBOLD(R.font.sf_pro_display_semibold),
    LIGHT(R.font.sf_pro_display_light),
    MEDIUM(R.font.sf_pro_display_medium)
}

/** Nạp mỗi font một lần cho cả process (renderer dựng lại mỗi lần ô widget đổi cỡ / loại). Lỗi nạp thì null → font mặc định. */
internal object WidgetFonts {
    private val cache = arrayOfNulls<Typeface>(WidgetFont.entries.size)

    fun get(context: Context, font: WidgetFont): Typeface? = cache[font.ordinal] ?: try {
        context.resources.getFont(font.res).also { cache[font.ordinal] = it }
    } catch (e: Resources.NotFoundException) {
        null
    }
}

/**
 * Bút chữ canh giữa theo `x` của `drawText`, có bóng mềm. Dựng một lần trong constructor của renderer. PW: [font] thay cho `bold` (đậm giả);
 * mặc định SF Pro Display Semibold như chữ nhỏ của widget iOS. [tabular]: chữ số đều độ rộng (`tnum`) để giờ / số không nhảy khi đổi.
 */
internal fun newWidgetTextPaint(context: Context, color: Int, font: WidgetFont = WidgetFont.SEMIBOLD, tabular: Boolean = false): TextPaint =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textAlign = Paint.Align.CENTER
        WidgetFonts.get(context, font)?.let { typeface = it }
        if (tabular) fontFeatureSettings = "tnum"
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
