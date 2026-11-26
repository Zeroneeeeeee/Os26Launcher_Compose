package gambi.global.os26launcher.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import gambi.global.os26launcher.R
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.min

/** Lịch 2x2: thứ (đỏ), ngày trong tháng thật lớn, tên tháng (ẩn khi quá thấp). Không có sự kiện (cần READ_CALENDAR, ngoài phạm vi). */
internal class CalendarSmallRenderer(private val context: Context) : WidgetRenderer {
    // PW: như widget Lịch iOS — thứ / tháng Semibold, số ngày Medium (SF Pro Display).
    private val weekdayPaint =
        newWidgetTextPaint(context, context.getColor(R.color.widget_accent), WidgetFont.SEMIBOLD)
    private val numberPaint = newWidgetTextPaint(context, context.getColor(R.color.widget_text), WidgetFont.MEDIUM)
    private val monthPaint = newWidgetTextPaint(context, context.getColor(R.color.widget_text_dim), WidgetFont.SEMIBOLD)
    private var cx = 0f
    private var weekday = ""
    private var number = ""
    private var month = ""
    private var showMonth = false
    private var weekdayBaseline = 0f
    private var numberBaseline = 0f
    private var monthBaseline = 0f
    private var description = ""

    override fun prepare(
        width: Int,
        height: Int,
        density: Float,
        now: ZonedDateTime,
        locale: Locale,
        is24h: Boolean
    ) {
        cx = width / 2f
        val h = height.toFloat()
        val w = width.toFloat()
        weekday = WidgetFormat.weekdayFull(now, locale).uppercase(locale)
        number = now.dayOfMonth.toString()
        month = WidgetFormat.monthFull(now, locale)
        showMonth = min(w, h) >= HIDE_MONTH_BELOW_DP * density
        weekdayPaint.fitWidth(weekday, w * 0.82f, h * 0.13f)
        weekdayBaseline = weekdayPaint.baselineAround(h * 0.20f)
        numberPaint.textSize = h * (if (showMonth) 0.46f else 0.56f)
        numberBaseline = numberPaint.baselineAround(h * (if (showMonth) 0.54f else 0.60f))
        if (showMonth) {
            monthPaint.fitWidth(month, w * 0.82f, h * 0.12f)
            monthBaseline = monthPaint.baselineAround(h * 0.86f)
        }
        description = context.getString(
            R.string.widget_calendar_description,
            WidgetFormat.dateLong(now, locale)
        )
    }

    override fun draw(canvas: Canvas) {
        if (cx <= 0f) return
        canvas.drawText(weekday, cx, weekdayBaseline, weekdayPaint)
        canvas.drawText(number, cx, numberBaseline, numberPaint)
        if (showMonth) canvas.drawText(month, cx, monthBaseline, monthPaint)
    }

    override fun contentDescription(): String = description

    private companion object {
        /** CHỈNH TAY (dp): cạnh ngắn thấp hơn mức này thì bỏ dòng tháng. */
        const val HIDE_MONTH_BELOW_DP = 96f
    }
}

/**
 * Lịch 4x2: nửa trái thứ + ngày lớn + tháng năm; nửa phải lưới tháng 7 cột (hàng đầu tên thứ, hôm nay tô đỏ). Đầu tuần theo locale
 * (`WeekFields`, tiếng Việt: thứ Hai). Lưới ẩn khi widget quá thấp / hẹp.
 */
internal class CalendarMediumRenderer(private val context: Context) : WidgetRenderer {
    // PW: như widget Lịch iOS — thứ / tháng Semibold, số ngày Medium (SF Pro Display).
    private val weekdayPaint =
        newWidgetTextPaint(context, context.getColor(R.color.widget_accent), WidgetFont.SEMIBOLD)
    private val numberPaint = newWidgetTextPaint(context, context.getColor(R.color.widget_text), WidgetFont.MEDIUM)
    private val monthPaint = newWidgetTextPaint(context, context.getColor(R.color.widget_text_dim), WidgetFont.SEMIBOLD)
    private val headerPaint = newWidgetTextPaint(context, context.getColor(R.color.widget_text_dim), WidgetFont.SEMIBOLD)
    private val dayPaint = newWidgetTextPaint(context, context.getColor(R.color.widget_text), WidgetFont.SEMIBOLD, tabular = true)
    private val todayPaint =
        newWidgetTextPaint(context, context.getColor(R.color.widget_text), WidgetFont.SEMIBOLD, tabular = true)
    private val todayFill =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.widget_accent) }

    private var leftCx = 0f
    private var weekday = ""
    private var number = ""
    private var monthYear = ""
    private var weekdayBaseline = 0f
    private var numberBaseline = 0f
    private var monthBaseline = 0f
    private var showGrid = false
    private var labels = emptyArray<String>()
    private var xs = FloatArray(0)
    private var baselines = FloatArray(0)
    private var todayIndex = -1
    private var todayCenterY = 0f
    private var todayRadius = 0f
    private var description = ""
    private var ready = false

    override fun prepare(
        width: Int,
        height: Int,
        density: Float,
        now: ZonedDateTime,
        locale: Locale,
        is24h: Boolean
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = min(w, h) * 0.09f
        val leftW = w * 0.42f
        leftCx = (pad + leftW) / 2f
        weekday = WidgetFormat.weekdayFull(now, locale).uppercase(locale)
        number = now.dayOfMonth.toString()
        monthYear = WidgetFormat.monthFull(now, locale) + " " + now.year
        weekdayPaint.fitWidth(weekday, leftW - pad, h * 0.13f)
        weekdayBaseline = weekdayPaint.baselineAround(h * 0.20f)
        numberPaint.textSize = h * 0.42f
        numberBaseline = numberPaint.baselineAround(h * 0.52f)
        monthPaint.fitWidth(monthYear, leftW - pad, h * 0.11f)
        monthBaseline = monthPaint.baselineAround(h * 0.84f)

        val gx0 = w * 0.46f
        val gx1 = w - pad
        val gy0 = pad * 0.9f
        val gy1 = h - pad * 0.9f
        val cellW = (gx1 - gx0) / 7f
        val cellH = (gy1 - gy0) / 7f
        showGrid = cellH >= MIN_ROW_DP * density && w >= MIN_GRID_WIDTH_DP * density
        if (showGrid) {
            val first = WeekFields.of(locale).firstDayOfWeek
            val ym = YearMonth.from(now)
            val offset = ((ym.atDay(1).dayOfWeek.value - first.value) + 7) % 7
            val days = ym.lengthOfMonth()
            val textSize = min(cellH * 0.62f, cellW * 0.52f)
            headerPaint.textSize = textSize
            dayPaint.textSize = textSize
            todayPaint.textSize = textSize
            val baselineShift = dayPaint.baselineAround(0f)
            labels = Array(7 + days) { i ->
                if (i < 7) WidgetFormat.weekdayNarrow(
                    DayOfWeek.of(((first.value - 1 + i) % 7) + 1),
                    locale
                ) else (i - 6).toString()
            }
            xs = FloatArray(labels.size)
            baselines = FloatArray(labels.size)
            for (i in labels.indices) {
                val row: Int
                val col: Int
                if (i < 7) {
                    row = 0
                    col = i
                } else {
                    val pos = offset + (i - 7)
                    row = 1 + pos / 7
                    col = pos % 7
                }
                xs[i] = gx0 + (col + 0.5f) * cellW
                baselines[i] = gy0 + (row + 0.5f) * cellH + baselineShift
            }
            todayIndex = 7 + (now.dayOfMonth - 1)
            todayCenterY = baselines[todayIndex] - baselineShift
            todayRadius = min(cellW, cellH) * 0.48f
        } else {
            todayIndex = -1
        }
        description = context.getString(
            R.string.widget_calendar_description,
            WidgetFormat.dateLong(now, locale)
        )
        ready = true
    }

    override fun draw(canvas: Canvas) {
        if (!ready) return
        canvas.drawText(weekday, leftCx, weekdayBaseline, weekdayPaint)
        canvas.drawText(number, leftCx, numberBaseline, numberPaint)
        canvas.drawText(monthYear, leftCx, monthBaseline, monthPaint)
        if (!showGrid) return
        if (todayIndex >= 0) canvas.drawCircle(xs[todayIndex], todayCenterY, todayRadius, todayFill)
        for (i in labels.indices) {
            val paint = if (i < 7) headerPaint else if (i == todayIndex) todayPaint else dayPaint
            canvas.drawText(labels[i], xs[i], baselines[i], paint)
        }
    }

    override fun contentDescription(): String = description

    private companion object {
        /** CHỈNH TAY (dp): hàng lưới thấp hơn mức này (chữ nhỏ hơn khoảng 6sp) hoặc widget hẹp hơn mức dưới thì ẩn lưới. */
        const val MIN_ROW_DP = 9f
        const val MIN_GRID_WIDTH_DP = 150f
    }
}
