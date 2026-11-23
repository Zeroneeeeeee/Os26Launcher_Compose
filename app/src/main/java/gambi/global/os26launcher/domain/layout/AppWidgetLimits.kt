package gambi.global.os26launcher.domain.layout

/**
 * G3c — trần cỡ (tính theo ô) của widget app. Chủ dự án chốt 4x4 (2026-11-13, câu hỏi 1); nằm đúng MỘT chỗ này để đổi trần không phải đi
 * tìm số 4 rải rác. 4 cột là bề rộng nhỏ nhất của mọi `GridPreset`, 4 hàng chừa ít nhất một hàng ở preset 4x5, nên mọi cỡ trong trần vừa mọi preset
 * (đổi cỡ lưới không làm một widget app không còn vừa).
 */
object AppWidgetLimits {
    const val MAX_SPAN_X = 4
    const val MAX_SPAN_Y = 4

    fun allows(spanX: Int, spanY: Int): Boolean =
        spanX in 1..MAX_SPAN_X && spanY in 1..MAX_SPAN_Y
}
