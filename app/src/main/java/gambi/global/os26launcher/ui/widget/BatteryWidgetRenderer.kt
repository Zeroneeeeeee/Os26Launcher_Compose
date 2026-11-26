package gambi.global.os26launcher.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import gambi.global.os26launcher.R
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.min

/**
 * BW (chủ dự án 2026-11-25: kết hợp đề xuất và ảnh widget "Pin" của iOS) — widget Pin 2 × 2: góc trên trái một vòng tiến độ quanh biểu tượng
 * điện thoại, dưới là số % lớn (Inter Display Medium, chữ số đều). Đang cắm sạc: tia sét bên phải vòng (2026-11-26: dời khỏi hàng số % vì "100%"
 * không còn chỗ). Dữ liệu từ [BatteryWidgetHub] (cả bản xem trước trong bảng chọn: dữ liệu thật); chưa có số thì vẽ rãnh trống và "–".
 *
 * Màu vòng theo quy ước của iOS (tra 2026-11-26, cộng đồng Apple / báo công nghệ): tiết kiệm pin (Low Power Mode) VÀNG, kể cả khi đang sạc;
 * đang sạc XANH LÁ; còn ≤ [LOW_LEVEL] % (không sạc) ĐỎ; còn lại xanh lá. Mốc 20 % của iOS; Android AOSP cảnh báo ở 15 %
 * (`config_lowBatteryWarningLevel`, nhiều ROM đặt 20) — giữ 20 cho giống iOS.
 *
 * Mọi số đo theo cạnh ngắn (thẻ vuông, PW). `prepare` dựng chuỗi / đường / màu; `draw` chỉ phát lệnh vẽ, không cấp phát (GL Task 6).
 */
internal class BatteryWidgetRenderer(private val context: Context) : WidgetRenderer {
    private val okColor = context.getColor(R.color.battery_ring_ok)
    private val lowColor = context.getColor(R.color.widget_accent)
    private val saverColor = context.getColor(R.color.battery_ring_saver)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = context.getColor(R.color.battery_ring_track)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.widget_text) }
    private val percentPaint = newWidgetTextPaint(context, context.getColor(R.color.widget_text), WidgetFont.DISPLAY_MEDIUM, tabular = true).apply {
        textAlign = Paint.Align.LEFT
    }
    private val ringRect = RectF()
    private val phoneRect = RectF()
    private val bolt = Path()

    private var ready = false
    private var sweep = 0f
    private var phoneCorner = 0f
    private var percent = ""
    private var percentX = 0f
    private var percentBaseline = 0f
    private var showBolt = false
    private var description = ""

    override fun prepare(width: Int, height: Int, density: Float, now: ZonedDateTime, locale: Locale, is24h: Boolean) {
        val side = min(width, height).toFloat()
        ready = side > 0f
        if (!ready) return
        val state = BatteryWidgetHub.state
        val pad = side * PAD_RATIO

        // Vòng tiến độ (góc trên trái).
        val d = side * RING_RATIO
        val stroke = d * RING_STROKE_RATIO
        trackPaint.strokeWidth = stroke
        ringPaint.strokeWidth = stroke
        ringRect.set(pad + stroke / 2f, pad + stroke / 2f, pad + d - stroke / 2f, pad + d - stroke / 2f)
        sweep = if (state == null) 0f else 360f * state.level / 100f
        ringPaint.color = when {
            state == null -> okColor
            state.powerSave -> saverColor // như iOS: tiết kiệm pin vàng cả khi đang sạc
            state.plugged -> okColor
            state.level <= LOW_LEVEL -> lowColor
            else -> okColor
        }

        // Biểu tượng điện thoại giữa vòng.
        val cx = ringRect.centerX()
        val cy = ringRect.centerY()
        val pw = d * PHONE_W_RATIO
        val ph = d * PHONE_H_RATIO
        phoneRect.set(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f)
        phoneCorner = pw * PHONE_CORNER_RATIO

        // Số % (góc dưới trái), thu nhỏ nếu không vừa bề ngang ("100%").
        percent = if (state == null) "–" else String.format(locale, "%d%%", state.level)
        percentPaint.fitWidth(percent, side - 2 * pad, side * PERCENT_RATIO)
        percentX = pad
        percentBaseline = side - pad
        showBolt = state?.plugged == true
        if (showBolt) {
            // Tia sét bên phải vòng, canh giữa theo chiều dọc của vòng.
            val bw = side * BOLT_W_RATIO
            val bh = bw * BOLT_ASPECT
            val left = pad + d + side * BOLT_GAP_RATIO
            val top = cy - bh / 2f
            bolt.reset()
            bolt.moveTo(left + bw * 0.62f, top)
            bolt.lineTo(left + bw * 0.08f, top + bh * 0.58f)
            bolt.lineTo(left + bw * 0.46f, top + bh * 0.58f)
            bolt.lineTo(left + bw * 0.34f, top + bh)
            bolt.lineTo(left + bw * 0.92f, top + bh * 0.40f)
            bolt.lineTo(left + bw * 0.54f, top + bh * 0.40f)
            bolt.close()
        }

        description = when {
            state == null -> context.getString(R.string.widget_battery_name)
            state.plugged -> context.getString(R.string.widget_battery_description_charging, state.level)
            else -> context.getString(R.string.widget_battery_description, state.level)
        }
    }

    override fun draw(canvas: Canvas) {
        if (!ready) return
        canvas.drawArc(ringRect, 0f, 360f, false, trackPaint)
        if (sweep > 0f) canvas.drawArc(ringRect, -90f, sweep, false, ringPaint)
        canvas.drawRoundRect(phoneRect, phoneCorner, phoneCorner, glyphPaint)
        canvas.drawText(percent, percentX, percentBaseline, percentPaint)
        if (showBolt) canvas.drawPath(bolt, glyphPaint)
    }

    override fun contentDescription(): String = description

    private companion object {
        /** CHỈNH TAY (BW, chỉnh mắt trên máy): lề trong / cạnh thẻ. */
        const val PAD_RATIO = 0.11f

        /** Đường kính vòng / cạnh thẻ. */
        const val RING_RATIO = 0.40f
        const val RING_STROKE_RATIO = 0.12f

        /** Điện thoại trong vòng: rộng, cao / đường kính vòng; bo / rộng. */
        const val PHONE_W_RATIO = 0.22f
        const val PHONE_H_RATIO = 0.40f
        const val PHONE_CORNER_RATIO = 0.3f

        /** Cỡ chữ số % / cạnh thẻ. */
        const val PERCENT_RATIO = 0.30f

        /** Tia sét: rộng / cạnh thẻ, cao / rộng, khe với vòng / cạnh thẻ. */
        const val BOLT_W_RATIO = 0.11f
        const val BOLT_ASPECT = 1.6f
        const val BOLT_GAP_RATIO = 0.05f

        /** Như iOS: từ mức này trở xuống vòng đỏ (khi không cắm sạc, không tiết kiệm pin). */
        const val LOW_LEVEL = 20
    }
}
