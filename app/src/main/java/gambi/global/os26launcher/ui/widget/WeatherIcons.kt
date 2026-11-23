package gambi.global.os26launcher.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.weather.WeatherCondition
import kotlin.math.cos
import kotlin.math.sin

/**
 * G3e Task 5 (D7) — biểu tượng thời tiết tự vẽ bằng Canvas, tác phẩm của dự án (không dùng ảnh của OpenWeatherMap: tài liệu không nêu
 * giấy phép ảnh). Mọi `Path` dựng MỘT LẦN ở khối khởi tạo theo hệ đơn vị (bán kính 1); [draw] chỉ `save/translate/scale` rồi phát lệnh vẽ,
 * không cấp phát (bài học GL Task 6). Một thể hiện cho mỗi renderer (không chia sẻ giữa luồng).
 */
internal class WeatherIcons(context: Context) {
    private val cloudPaint = fill(context.getColor(R.color.weather_cloud))
    private val cloudDimPaint = fill(context.getColor(R.color.weather_cloud_dim))
    private val sunPaint = fill(context.getColor(R.color.weather_sun))
    private val moonPaint = fill(context.getColor(R.color.weather_moon))
    private val rainPaint = stroke(context.getColor(R.color.weather_rain), 0.075f)
    private val boltPaint = fill(context.getColor(R.color.weather_bolt))
    private val snowPaint = fill(context.getColor(R.color.weather_cloud))
    private val fogPaint = stroke(context.getColor(R.color.weather_cloud_dim), 0.085f)
    private val rayPaint = stroke(context.getColor(R.color.weather_sun), 0.07f)
    private val alertPaint = stroke(context.getColor(R.color.weather_alert), 0.09f)
    private val alertFill = fill(context.getColor(R.color.weather_alert))
    private val slashPaint = stroke(context.getColor(R.color.weather_cloud), 0.08f)

    private val rect = RectF()
    private val moonPath = Path()
    private val boltPath = Path()

    init {
        // Trăng lưỡi liềm: hình tròn bán kính 1 trừ hình tròn lệch phải-trên.
        val full = Path().apply { addCircle(0f, 0f, 1f, Path.Direction.CW) }
        val cut = Path().apply { addCircle(0.55f, -0.35f, 0.85f, Path.Direction.CW) }
        moonPath.op(full, cut, Path.Op.DIFFERENCE)
        boltPath.apply {
            moveTo(0.12f, -0.30f); lineTo(-0.20f, 0.12f); lineTo(-0.02f, 0.12f)
            lineTo(-0.14f, 0.46f); lineTo(0.22f, -0.04f); lineTo(0.04f, -0.04f); close()
        }
    }

    /** Vẽ biểu tượng tình trạng [c] (đêm hay ngày chỉ khác với quang/ít mây) trong ô vuông cạnh [size], tâm ([cx], [cy]). */
    fun draw(
        canvas: Canvas,
        c: WeatherCondition,
        night: Boolean,
        cx: Float,
        cy: Float,
        size: Float,
        alpha: Int = 255
    ) {
        val s = size / 2f
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(s, s)
        applyAlpha(alpha)
        when (c) {
            WeatherCondition.CLEAR -> sunOrMoon(canvas, night, 0f, 0f, 0.5f)
            WeatherCondition.PARTLY_CLOUDY -> {
                sunOrMoon(canvas, night, -0.34f, -0.34f, 0.32f)
                cloud(canvas, 0.1f, 0.2f, 0.9f, cloudPaint)
            }

            WeatherCondition.CLOUDY -> {
                cloud(canvas, -0.2f, -0.18f, 0.7f, cloudDimPaint)
                cloud(canvas, 0.1f, 0.12f, 0.95f, cloudPaint)
            }

            WeatherCondition.RAIN -> {
                cloud(canvas, 0f, -0.2f, 1f, cloudPaint)
                for (i in -1..1) canvas.drawLine(
                    i * 0.34f + 0.1f,
                    0.40f,
                    i * 0.34f - 0.02f,
                    0.72f,
                    rainPaint
                )
            }

            WeatherCondition.THUNDERSTORM -> {
                cloud(canvas, 0f, -0.25f, 1f, cloudDimPaint)
                canvas.drawPath(boltPath, boltPaint)
                canvas.save()
                canvas.translate(0f, 0.38f)
                canvas.scale(1.2f, 1.2f)
                canvas.drawPath(boltPath, boltPaint)
                canvas.restore()
            }

            WeatherCondition.SNOW -> {
                cloud(canvas, 0f, -0.2f, 1f, cloudPaint)
                for (i in -1..1) canvas.drawCircle(
                    i * 0.34f,
                    0.55f + if (i == 0) 0.12f else 0f,
                    0.09f,
                    snowPaint
                )
            }

            WeatherCondition.FOG -> {
                for (i in -1..1) canvas.drawLine(
                    -0.7f + if (i == 0) 0.15f else 0f,
                    i * 0.32f,
                    0.7f - if (i == 0) 0.15f else 0f,
                    i * 0.32f,
                    fogPaint
                )
            }

            WeatherCondition.UNKNOWN -> cloud(canvas, 0f, 0f, 1f, cloudDimPaint)
        }
        restoreAlpha()
        canvas.restore()
    }

    /** Đám mây bị gạch chéo: chưa cấu hình khóa / ngoại tuyến. */
    fun drawCloudOff(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val s = size / 2f
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(s, s)
        cloud(canvas, 0f, 0f, 1f, cloudDimPaint)
        canvas.drawLine(-0.7f, -0.6f, 0.7f, 0.7f, slashPaint)
        canvas.restore()
    }

    /** Dấu "!" trong vòng tròn: lỗi, không có dữ liệu. */
    fun drawAlert(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val s = size / 2f
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(s, s)
        canvas.drawCircle(0f, 0f, 0.82f, alertPaint)
        canvas.drawLine(0f, -0.42f, 0f, 0.12f, alertPaint)
        canvas.drawCircle(0f, 0.42f, 0.07f, alertFill)
        canvas.restore()
    }

    private fun sunOrMoon(canvas: Canvas, night: Boolean, x: Float, y: Float, r: Float) {
        if (night) {
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(r * 1.5f, r * 1.5f)
            canvas.drawPath(moonPath, moonPaint)
            canvas.restore()
        } else {
            canvas.drawCircle(x, y, r * 0.62f, sunPaint)
            for (i in 0 until 8) {
                val a = i * (Math.PI / 4).toFloat()
                val c = cos(a)
                val sn = sin(a)
                canvas.drawLine(
                    x + c * r * 0.82f,
                    y + sn * r * 0.82f,
                    x + c * r * 1.12f,
                    y + sn * r * 1.12f,
                    rayPaint
                )
            }
        }
    }

    /** Đám mây rộng ~1.4 x [k], tâm ([x], [y]): ba hình tròn + một thanh bo tròn. */
    private fun cloud(canvas: Canvas, x: Float, y: Float, k: Float, paint: Paint) {
        canvas.drawCircle(x - 0.30f * k, y + 0.04f * k, 0.26f * k, paint)
        canvas.drawCircle(x + 0.02f * k, y - 0.10f * k, 0.34f * k, paint)
        canvas.drawCircle(x + 0.36f * k, y + 0.07f * k, 0.22f * k, paint)
        rect.set(x - 0.56f * k, y + 0.04f * k, x + 0.58f * k, y + 0.30f * k)
        canvas.drawRoundRect(rect, 0.13f * k, 0.13f * k, paint)
    }

    private val all = arrayOf(
        cloudPaint,
        cloudDimPaint,
        sunPaint,
        moonPaint,
        rainPaint,
        boltPaint,
        snowPaint,
        fogPaint,
        rayPaint
    )
    private val baseAlpha = IntArray(all.size) { all[it].alpha }

    private fun applyAlpha(alpha: Int) {
        if (alpha >= 255) return
        for (i in all.indices) all[i].alpha = baseAlpha[i] * alpha / 255
    }

    private fun restoreAlpha() {
        for (i in all.indices) all[i].alpha = baseAlpha[i]
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    // Độ dày nét theo hệ đơn vị (bán kính 1), nên co giãn cùng biểu tượng.
    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = width
        this.color = color
    }
}
