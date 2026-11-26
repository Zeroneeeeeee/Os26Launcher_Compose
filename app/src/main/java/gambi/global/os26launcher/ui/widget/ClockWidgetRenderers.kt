package gambi.global.os26launcher.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import gambi.global.os26launcher.R
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Đồng hồ kim 2x2: mặt tròn, 12 vạch, kim giờ và kim phút, không kim giây (D4/quyết định 13: cập nhật mỗi phút). */
internal class ClockAnalogRenderer(private val context: Context) : WidgetRenderer {
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private val majorTicks = FloatArray(4 * 4)
    private val minorTicks = FloatArray(8 * 4)
    private var hourX = 0f
    private var hourY = 0f
    private var minuteX = 0f
    private var minuteY = 0f
    private var description = ""

    private val white = context.getColor(R.color.widget_text)
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = context.getColor(R.color.widget_text_dim)
    }
    private val minorPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.widget_text_dim) }
    private val majorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white }
    private val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = white
        strokeCap = Paint.Cap.ROUND
    }
    private val minutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = white
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.widget_accent) }
    private var dotRadius = 0f
    private var hourW = 0f

    override fun prepare(
        width: Int,
        height: Int,
        density: Float,
        now: ZonedDateTime,
        locale: Locale,
        is24h: Boolean
    ) {
        val m = min(width, height).toFloat()
        cx = width / 2f
        cy = height / 2f
        radius = m * 0.5f * FACE_RATIO
        facePaint.strokeWidth = maxOf(1f, m * 0.012f)
        minorPaint.strokeWidth = maxOf(1f, m * 0.014f)
        majorPaint.strokeWidth = maxOf(1.5f, m * 0.028f)
        for (i in 0 until 12) {
            val angle = Math.toRadians(i * 30.0)
            val dx = sin(angle).toFloat()
            val dy = -cos(angle).toFloat()
            val outer = radius * 0.96f
            val inner = radius * (if (i % 3 == 0) 0.80f else 0.88f)
            val target = if (i % 3 == 0) majorTicks else minorTicks
            val k = if (i % 3 == 0) i / 3 else i - (i / 3 + 1)
            target[k * 4] = cx + dx * inner
            target[k * 4 + 1] = cy + dy * inner
            target[k * 4 + 2] = cx + dx * outer
            target[k * 4 + 3] = cy + dy * outer
        }
        val minutes = now.minute
        val hourAngle = Math.toRadians((((now.hour % 12) + minutes / 60.0) * 30.0))
        val minuteAngle = Math.toRadians(minutes * 6.0)
        hourX = cx + sin(hourAngle).toFloat() * radius * 0.50f
        hourY = cy - cos(hourAngle).toFloat() * radius * 0.50f
        minuteX = cx + sin(minuteAngle).toFloat() * radius * 0.78f
        minuteY = cy - cos(minuteAngle).toFloat() * radius * 0.78f
        hourW = maxOf(2f, m * 0.05f)
        hourPaint.strokeWidth = hourW
        minutePaint.strokeWidth = maxOf(1.5f, m * 0.032f)
        dotRadius = maxOf(2f, m * 0.032f)
        description = context.getString(
            R.string.widget_clock_description,
            WidgetFormat.time(now, locale, is24h)
        )
    }

    override fun draw(canvas: Canvas) {
        if (radius <= 0f) return
        canvas.drawCircle(cx, cy, radius, facePaint)
        canvas.drawLines(minorTicks, minorPaint)
        canvas.drawLines(majorTicks, majorPaint)
        canvas.drawLine(cx, cy, hourX, hourY, hourPaint)
        canvas.drawLine(cx, cy, minuteX, minuteY, minutePaint)
        canvas.drawCircle(cx, cy, dotRadius, dotPaint)
    }

    override fun contentDescription(): String = description

    private companion object {
        /** CHỈNH TAY: bán kính mặt đồng hồ / nửa cạnh ngắn. */
        const val FACE_RATIO = 0.84f
    }
}

/** Đồng hồ số 4x2: giờ lớn (12/24 giờ theo máy) và một dòng ngày; dòng ngày ẩn khi widget quá thấp. */
internal class ClockDigitalRenderer(private val context: Context) : WidgetRenderer {
    private val timePaint =
        newWidgetTextPaint(context, context.getColor(R.color.widget_text), WidgetFont.MEDIUM, tabular = true) // PW
    private val datePaint = newWidgetTextPaint(context, context.getColor(R.color.widget_text_dim), WidgetFont.SEMIBOLD)
    private var w = 0f
    private var time = ""
    private var date = ""
    private var showDate = false
    private var timeBaseline = 0f
    private var dateBaseline = 0f
    private var description = ""

    override fun prepare(
        width: Int,
        height: Int,
        density: Float,
        now: ZonedDateTime,
        locale: Locale,
        is24h: Boolean
    ) {
        w = width.toFloat()
        val h = height.toFloat()
        time = WidgetFormat.time(now, locale, is24h)
        date = WidgetFormat.dateLong(now, locale)
        showDate = h >= HIDE_DATE_BELOW_DP * density
        timePaint.fitWidth(time, w * 0.88f, if (showDate) h * 0.46f else h * 0.58f)
        timeBaseline = timePaint.baselineAround(if (showDate) h * 0.40f else h * 0.5f)
        if (showDate) {
            datePaint.fitWidth(date, w * 0.88f, h * 0.15f)
            dateBaseline = datePaint.baselineAround(h * 0.77f)
        }
        description = context.getString(R.string.widget_clock_description, time)
    }

    override fun draw(canvas: Canvas) {
        if (w <= 0f) return
        canvas.drawText(time, w / 2f, timeBaseline, timePaint)
        if (showDate) canvas.drawText(date, w / 2f, dateBaseline, datePaint)
    }

    override fun contentDescription(): String = description

    private companion object {
        /** CHỈNH TAY (dp): thấp hơn mức này thì chỉ còn giờ (split-screen). */
        const val HIDE_DATE_BELOW_DP = 84f
    }
}
