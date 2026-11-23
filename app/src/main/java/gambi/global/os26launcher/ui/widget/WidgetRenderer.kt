package gambi.global.os26launcher.ui.widget

import android.content.Context
import android.graphics.Canvas
import gambi.global.os26launcher.domain.model.WidgetKind
import java.time.ZonedDateTime
import java.util.Locale

/**
 * G2c Task 5 — vẽ nội dung một widget tự vẽ bằng Canvas. Nền (kính / phẳng) và dấu "−" do `IconCellView` vẽ; renderer chỉ vẽ phần
 * nội dung bên trong hình chữ nhật `0..width x 0..height`.
 *
 * Quy tắc hiệu năng (GL Task 6): KHÔNG cấp phát trong [draw]. Mọi chuỗi, bố cục, tọa độ dựng trong [prepare] (gọi khi cỡ đổi và mỗi
 * phút), [draw] chỉ phát lệnh vẽ. Cỡ chữ suy từ cạnh ngắn của widget, không từ `sp` (bài học GL Task 11: `font_scale`).
 */
internal interface WidgetRenderer {
    /** Dựng lại mọi thứ cho cỡ ([width] x [height]) px, thời điểm [now], ngôn ngữ [locale], [is24h] giờ 24. */
    fun prepare(
        width: Int,
        height: Int,
        density: Float,
        now: ZonedDateTime,
        locale: Locale,
        is24h: Boolean
    )

    fun draw(canvas: Canvas)

    /**
     * WX Task 5: ô widget báo cho renderer biết `itemId` (hàng `launcher_items`) của widget nó đang vẽ, gọi ở MỖI lần bind (ô được tái dùng giữa các widget
     * và giữa các loại item, bài học A1a) và trước lần [prepare] đầu của lần bind đó. Mặc định không làm gì: đồng hồ, lịch không có cấu hình riêng.
     */
    fun bindItem(itemId: Long) {}

    /** Câu đọc cho TalkBack, đã có giờ / ngày hiện tại. */
    fun contentDescription(): String
}

internal object WidgetRenderers {
    /** [sample] chỉ có nghĩa với thời tiết: bản xem trước trong bảng chọn dùng dữ liệu mẫu, không đọc trạng thái thật. */
    fun create(
        context: Context,
        kind: WidgetKind,
        spanX: Int,
        spanY: Int,
        sample: Boolean = false
    ): WidgetRenderer = when (kind) {
        WidgetKind.CLOCK -> if (spanX <= 2) ClockAnalogRenderer(context) else ClockDigitalRenderer(
            context
        )

        WidgetKind.CALENDAR -> if (spanX <= 2) CalendarSmallRenderer(context) else CalendarMediumRenderer(
            context
        )

        WidgetKind.WEATHER -> WeatherRenderer(context, medium = spanX > 2, sample = sample)

        WidgetKind.PHOTO -> PhotoWidgetRenderer(context, sample = sample) // G3b
    }
}
