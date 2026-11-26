package gambi.global.os26launcher.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.format.DateFormat
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.weather.WeatherCondition
import gambi.global.os26launcher.domain.model.weather.TemperatureUnit
import gambi.global.os26launcher.domain.model.weather.WeatherError
import gambi.global.os26launcher.domain.model.weather.WeatherPlaces
import gambi.global.os26launcher.domain.model.weather.WeatherSnapshot
import gambi.global.os26launcher.domain.model.weather.WeatherState
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

/**
 * G3e Task 5 (D8) — widget thời tiết 2x2 ([medium] = false) và 4x2 ([medium] = true). Mọi trạng thái (không khóa, chưa chọn thành phố,
 * đang tải, có dữ liệu, dữ liệu cũ, lỗi) vẽ trong cùng một lớp. Bố cục, chuỗi và tọa độ dựng ở [prepare] (mỗi phút, mỗi khi dữ liệu hoặc
 * cỡ đổi); [draw] chỉ phát lệnh vẽ, không cấp phát (bài học GL Task 6). Cỡ chữ suy từ cạnh ngắn, không từ `sp` (bài học GL Task 11).
 *
 * PB Task 5 (2026-11-18): bố cục theo card xem trước ở Cài đặt → Thời tiết (`item_settings_weather_preview.xml`, Figma 1.4 `302:1900`):
 * khối trái thành phố + nhiệt độ lớn, khối phải icon + tình trạng + "C x° · T y°", dải dưới 5 cột "Bây giờ" + 4 mốc (nhãn trên, nhiệt
 * độ dưới, canh trái cột), font Be Vietnam Pro. Nền vẫn là thẻ kính của widget (`IconCellView`, theo cài đặt Kính), chữ giữ bóng mềm
 * như các widget khác (chủ dự án chốt 2026-11-18: chỉ đổi bố cục, không đổi nền).
 *
 * [sample] = true: dùng dữ liệu mẫu cố định (bản xem trước trong bảng chọn), không đọc trạng thái thật và không kích hoạt làm mới mạng.
 */
internal class WeatherRenderer(
    private val context: Context,
    private val medium: Boolean,
    private val sample: Boolean = false
) : WidgetRenderer {
    private val icons = WeatherIcons(context)
    private val primary = context.getColor(R.color.widget_text)
    private val secondary = context.getColor(R.color.widget_text_dim)

    // PW (2026-11-25): Inter thay Be Vietnam Pro (gần SF của iOS): tên thành phố Semibold như iOS, nhiệt độ lớn Display Light.
    private val fontLight = WidgetFont.DISPLAY_LIGHT
    private val fontRegular = WidgetFont.TEXT_REGULAR
    private val fontSemibold = WidgetFont.TEXT_SEMIBOLD
    private val fontBold = WidgetFont.TEXT_SEMIBOLD

    /** WU: đơn vị người dùng chọn, đọc lại ở mỗi [prepare] (kể cả bản xem trước). */
    private var unit = TemperatureUnit.DEFAULT

    private val cityPaint = textPaint(primary, fontBold, Paint.Align.LEFT)
    private val tempPaint = textPaint(primary, fontLight, Paint.Align.LEFT)
    private val condPaint = textPaint(primary, fontRegular, Paint.Align.RIGHT)
    private val hiLoPaint = textPaint(primary, fontRegular, Paint.Align.RIGHT)
    private val smallCondPaint = textPaint(primary, fontRegular, Paint.Align.LEFT)
    private val smallHiLoPaint = textPaint(secondary, fontRegular, Paint.Align.LEFT)
    private val hourPaint = textPaint(secondary, fontRegular, Paint.Align.LEFT)
    private val hourTempPaint = textPaint(primary, fontSemibold, Paint.Align.LEFT)
    private val centerPaint = textPaint(primary, fontSemibold, Paint.Align.CENTER)
    private val centerSubPaint = textPaint(secondary, fontRegular, Paint.Align.CENTER)

    private var w = 0f
    private var h = 0f
    private var mode = Mode.NONE
    private var snapshot: WeatherSnapshot? = null
    private var stale = false
    private var description = ""

    // Khối chính
    private var padX = 0f
    private var city = ""
    private var cityBase = 0f
    private var temp = ""
    private var tempBase = 0f
    private var cond = ""
    private var condBase = 0f
    private var hiLo = ""
    private var hiLoBase = 0f
    private var showSecondary = true
    private var iconCx = 0f
    private var iconCy = 0f
    private var iconSize = 0f
    private var offlineCx = 0f
    private var offlineCy = 0f
    private var offlineSize = 0f

    // Dải theo giờ (4x2): cột 0 = "Bây giờ", rồi tối đa [HOURS] mốc
    private var showStrip = false
    private var stripCount = 0
    private val hourLabels = Array(HOURS + 1) { "" }
    private val hourTemps = Array(HOURS + 1) { "" }
    private var stripColW = 0f
    private var hourBase = 0f
    private var hourTempBase = 0f

    // Trạng thái không có dữ liệu
    private var msg = ""
    private var msgSub = ""
    private var msgBase = 0f
    private var msgSubBase = 0f
    private var msgIconCx = 0f
    private var msgIconCy = 0f
    private var msgIconSize = 0f

    private enum class Mode { NONE, DATA, NO_KEY, NO_LOCATION, LOADING, FAILED }

    /** WX Task 5: `itemId` hàng `launcher_items` của widget này; 0 = chưa biết (bản xem trước, ảnh kéo từ bảng chọn) -> "Vị trí hiện tại". */
    private var itemId = 0L

    override fun bindItem(itemId: Long) {
        this.itemId = itemId
    }

    override fun prepare(
        width: Int,
        height: Int,
        density: Float,
        now: ZonedDateTime,
        locale: Locale,
        is24h: Boolean
    ) {
        w = width.toFloat()
        h = height.toFloat()
        unit = WeatherWidgetHub.unit
        // WX Task 5: địa điểm của CHÍNH widget này (mặc định "Vị trí hiện tại"); bản xem trước không đọc ánh xạ. Tra mỗi lần prepare vì người dùng đổi được bất cứ lúc nào.
        val placeId = if (sample) WeatherPlaces.CURRENT else WeatherWidgetHub.placeIdOf(itemId)
        val state: WeatherState = if (sample) WeatherState.Ready(
            WeatherWidgetHub.sample(),
            stale = false,
            lastError = null
        ) else WeatherWidgetHub.stateOf(placeId)
        if (!sample) WeatherWidgetHub.onVisible(placeId)
        when (state) {
            // PB D6: bản release trên máy khác có thể không có khóa: báo chung "không khả dụng", không hướng dẫn local.properties.
            WeatherState.NoKey -> prepareMessage(
                Mode.NO_KEY,
                context.getString(R.string.weather_no_key),
                "",
                density
            )

            WeatherState.NoLocation -> prepareMessage(
                Mode.NO_LOCATION,
                context.getString(R.string.weather_no_location),
                context.getString(R.string.weather_no_location_hint),
                density
            )

            WeatherState.Loading -> prepareMessage(
                Mode.LOADING,
                context.getString(R.string.weather_loading),
                "",
                density
            )

            is WeatherState.Failed -> prepareMessage(
                Mode.FAILED,
                errorText(state.error),
                "",
                density
            )

            is WeatherState.Ready -> prepareData(
                state.snapshot,
                state.stale,
                density,
                locale,
                is24h
            )
        }
        description = when (mode) {
            Mode.DATA -> snapshot!!.let {
                context.getString(
                    R.string.weather_description,
                    it.place.displayName,
                    unit.display(it.tempC),
                    conditionText(it.condition),
                    unit.display(it.highC),
                    unit.display(it.lowC),
                    context.getString(
                        if (unit == TemperatureUnit.FAHRENHEIT) R.string.settings_weather_a11y_f else R.string.settings_weather_a11y_c
                    )
                )
            }

            else -> context.getString(R.string.weather_description_state, msg)
        }
    }

    private fun prepareMessage(m: Mode, title: String, sub: String, density: Float) {
        mode = m
        snapshot = null
        msg = title
        msgSub = sub
        val s = min(w, h)
        val showSub = sub.isNotEmpty() && medium && h >= HIDE_SECONDARY_BELOW_DP * density
        msgIconSize = if (medium) h * 0.42f else s * 0.34f
        msgIconCx = w / 2f
        msgIconCy = h * (if (showSub) 0.30f else 0.34f)
        // Như iOS: dòng báo lỗi cỡ chữ thân (~15–17 pt), không to như số nhiệt độ (trước 0.13 / 0.11).
        centerPaint.fitWidth(title, w * 0.88f, s * (if (medium) 0.085f else 0.095f))
        msgBase = centerPaint.baselineAround(h * (if (showSub) 0.64f else 0.68f))
        if (!showSub) msgSub = ""
        if (showSub) {
            centerSubPaint.fitWidth(sub, w * 0.88f, s * 0.10f)
            msgSubBase = centerSubPaint.baselineAround(h * 0.84f)
        }
    }

    private fun prepareData(
        s: WeatherSnapshot,
        isStale: Boolean,
        density: Float,
        locale: Locale,
        is24h: Boolean
    ) {
        mode = Mode.DATA
        snapshot = s
        stale = isStale
        val short = min(w, h)
        val pad = short * 0.10f // card xem trước: 16 dp trên thẻ cao ~160 dp
        padX = pad
        showSecondary = h >= HIDE_SECONDARY_BELOW_DP * density
        city = s.place.displayName
        temp = degree(s.tempC)
        cond = conditionText(s.condition)
        hiLo = context.getString(R.string.settings_weather_preview_high_low, degree(s.highC), degree(s.lowC))
        // Dữ liệu cũ: dòng cao/thấp nhường chỗ cho giờ cập nhật (cao/thấp vẫn có trong câu đọc TalkBack).
        if (stale) hiLo = context.getString(
            R.string.weather_updated_at,
            hourText(s.fetchedAtMillis / 1000, locale, is24h)
        )
        if (medium) prepareMedium(s, pad, density, locale, is24h) else prepareSmall(pad)
        offlineSize = short * 0.11f
    }

    /** 4x2: tỉ lệ đọc từ card xem trước (16/40 sp chữ chính, icon 32 dp, chữ phụ 12 sp, dải 12 + 14 sp) trên thẻ cao ~160 dp. */
    private fun prepareMedium(s: WeatherSnapshot, pad: Float, density: Float, locale: Locale, is24h: Boolean) {
        val u = h
        iconSize = u * 0.20f
        iconCx = w - pad - iconSize / 2f
        iconCy = pad + iconSize / 2f
        val rightW = w * 0.42f
        condPaint.fitWidth(cond, rightW, u * 0.085f)
        condBase = pad + iconSize + u * 0.03f - condPaint.fontMetrics.ascent
        hiLoPaint.fitWidth(hiLo, rightW, u * 0.085f)
        hiLoBase = condBase + condPaint.fontMetrics.descent - hiLoPaint.fontMetrics.ascent
        offlineCx = iconCx - iconSize / 2f - u * 0.09f
        offlineCy = iconCy

        val leftW = w - 2 * pad - maxOf(condPaint.measureText(cond), hiLoPaint.measureText(hiLo), iconSize) - pad
        cityPaint.fitWidth(city, leftW, u * 0.105f)
        cityBase = pad - cityPaint.fontMetrics.ascent
        tempPaint.fitWidth(temp, leftW, u * 0.27f)
        tempBase = cityBase + cityPaint.fontMetrics.descent - tempPaint.fontMetrics.ascent * 0.92f

        showStrip = h >= HIDE_STRIP_BELOW_DP * density
        if (!showStrip) return
        stripCount = 1 + min(HOURS, s.hourly.size)
        stripColW = (w - 2 * pad) / (HOURS + 1) // luôn 5 cột như card: thiếu mốc thì để trống bên phải
        hourLabels[0] = context.getString(R.string.settings_weather_preview_now)
        hourTemps[0] = temp
        for (i in 1 until stripCount) {
            val p = s.hourly[i - 1]
            hourLabels[i] = hourLabel(p.epochSeconds, s.utcOffsetSeconds, locale, is24h)
            hourTemps[i] = degree(p.tempC)
        }
        // Một paint dùng chung cho cả dải: co theo chuỗi dài nhất cho vừa cột.
        var widestLabel = hourLabels[0]
        var widestTemp = hourTemps[0]
        for (i in 1 until stripCount) {
            if (hourLabels[i].length > widestLabel.length) widestLabel = hourLabels[i]
            if (hourTemps[i].length > widestTemp.length) widestTemp = hourTemps[i]
        }
        hourPaint.fitWidth(widestLabel, stripColW * 0.92f, u * 0.075f)
        hourTempPaint.fitWidth(widestTemp, stripColW * 0.92f, u * 0.088f)
        hourTempBase = h - pad - hourTempPaint.fontMetrics.descent
        hourBase = hourTempBase + hourTempPaint.fontMetrics.ascent - u * 0.015f - hourPaint.fontMetrics.descent
    }

    /** 2x2: cùng thẻ và kiểu chữ, không có dải giờ; tình trạng + cao/thấp ở đáy. */
    private fun prepareSmall(pad: Float) {
        val u = min(w, h)
        showStrip = false
        iconSize = u * 0.24f
        iconCx = w - pad - iconSize / 2f
        iconCy = pad + iconSize / 2f
        cityPaint.fitWidth(city, w - 3 * pad - iconSize, u * 0.11f)
        cityBase = pad - cityPaint.fontMetrics.ascent
        tempPaint.fitWidth(temp, w - 2 * pad, u * 0.30f)
        tempBase = cityBase + cityPaint.fontMetrics.descent - tempPaint.fontMetrics.ascent * 0.92f
        smallHiLoPaint.fitWidth(hiLo, w - 2 * pad - u * 0.14f, u * 0.085f)
        hiLoBase = h - pad - smallHiLoPaint.fontMetrics.descent
        smallCondPaint.fitWidth(cond, w - 2 * pad, u * 0.095f)
        condBase = hiLoBase + smallHiLoPaint.fontMetrics.ascent - smallCondPaint.fontMetrics.descent
        offlineCx = w - pad - u * 0.055f
        offlineCy = h - pad - u * 0.055f
    }

    override fun draw(canvas: Canvas) {
        if (w <= 0f) return
        when (mode) {
            Mode.DATA -> drawData(canvas)
            Mode.NO_KEY -> {
                icons.drawCloudOff(canvas, msgIconCx, msgIconCy, msgIconSize)
                drawMessage(canvas)
            }

            Mode.NO_LOCATION, Mode.LOADING -> {
                icons.draw(
                    canvas,
                    WeatherCondition.CLOUDY,
                    false,
                    msgIconCx,
                    msgIconCy,
                    msgIconSize,
                    if (mode == Mode.LOADING) 120 else 255
                )
                drawMessage(canvas)
            }

            Mode.FAILED -> {
                icons.drawAlert(canvas, msgIconCx, msgIconCy, msgIconSize)
                drawMessage(canvas)
            }

            Mode.NONE -> Unit
        }
    }

    private fun drawMessage(canvas: Canvas) {
        canvas.drawText(msg, w / 2f, msgBase, centerPaint)
        if (msgSub.isNotEmpty()) canvas.drawText(msgSub, w / 2f, msgSubBase, centerSubPaint)
    }

    private fun drawData(canvas: Canvas) {
        val s = snapshot ?: return
        val alpha = if (stale) 150 else 255
        canvas.drawText(city, padX, cityBase, cityPaint)
        canvas.drawText(temp, padX, tempBase, tempPaint)
        icons.draw(canvas, s.condition, s.isNight, iconCx, iconCy, iconSize, alpha)
        if (medium) {
            // Khối phải canh phải theo mép icon (gravity end của card).
            val right = w - padX
            canvas.drawText(cond, right, condBase, condPaint)
            canvas.drawText(hiLo, right, hiLoBase, hiLoPaint)
            if (showStrip) {
                for (i in 0 until stripCount) {
                    val x = padX + stripColW * i
                    canvas.drawText(hourLabels[i], x, hourBase, hourPaint)
                    canvas.drawText(hourTemps[i], x, hourTempBase, hourTempPaint)
                }
            }
        } else if (showSecondary) {
            canvas.drawText(cond, padX, condBase, smallCondPaint)
            canvas.drawText(hiLo, padX, hiLoBase, smallHiLoPaint)
        }
        // Dữ liệu cũ: icon mờ nhẹ + biểu tượng "ngoại tuyến" nhỏ (D8 trạng thái 5).
        if (stale) icons.drawCloudOff(canvas, offlineCx, offlineCy, offlineSize)
    }

    override fun contentDescription(): String = description

    private fun conditionText(c: WeatherCondition): String = context.getString(
        when (c) {
            WeatherCondition.CLEAR -> R.string.weather_cond_clear
            WeatherCondition.PARTLY_CLOUDY -> R.string.weather_cond_partly_cloudy
            WeatherCondition.CLOUDY -> R.string.weather_cond_cloudy
            WeatherCondition.RAIN -> R.string.weather_cond_rain
            WeatherCondition.THUNDERSTORM -> R.string.weather_cond_thunderstorm
            WeatherCondition.SNOW -> R.string.weather_cond_snow
            WeatherCondition.FOG -> R.string.weather_cond_fog
            WeatherCondition.UNKNOWN -> R.string.weather_cond_unknown
        }
    )

    private fun errorText(e: WeatherError): String = context.getString(
        when (e) {
            WeatherError.NETWORK -> R.string.weather_error_network
            WeatherError.INVALID_KEY -> R.string.weather_error_key
            WeatherError.RATE_LIMITED -> R.string.weather_error_rate
            WeatherError.NOT_FOUND -> R.string.weather_error_not_found
            else -> R.string.weather_error_generic
        }
    )

    /** Nhãn giờ của mốc dự báo theo múi giờ CỦA ĐỊA ĐIỂM: 24 giờ "15h" như card xem trước (PB), 12 giờ "3 PM". */
    private fun hourLabel(
        epochSeconds: Long,
        offsetSeconds: Int,
        locale: Locale,
        is24h: Boolean
    ): String {
        if (is24h) {
            val hour = Instant.ofEpochSecond(epochSeconds).atOffset(ZoneOffset.ofTotalSeconds(offsetSeconds)).hour
            return context.getString(R.string.settings_weather_preview_hour, hour)
        }
        val pattern = try {
            DateFormat.getBestDateTimePattern(locale, if (is24h) "H" else "h a")
        } catch (e: Exception) {
            if (is24h) "HH" else "h a"
        }
        return try {
            DateTimeFormatter.ofPattern(pattern, locale).format(
                Instant.ofEpochSecond(epochSeconds)
                    .atOffset(ZoneOffset.ofTotalSeconds(offsetSeconds))
            )
        } catch (e: Exception) {
            ""
        }
    }

    private fun hourText(
        epochSeconds: Long,
        locale: Locale,
        is24h: Boolean
    ): String {
        val pattern = try {
            DateFormat.getBestDateTimePattern(locale, if (is24h) "Hm" else "hm")
        } catch (e: Exception) {
            if (is24h) "HH:mm" else "h:mm a"
        }
        // Giờ cập nhật là giờ trên MÁY người dùng (không phải giờ địa điểm).
        return try {
            DateTimeFormatter.ofPattern(pattern, locale).format(
                Instant.ofEpochSecond(epochSeconds).atZone(java.time.ZoneId.systemDefault())
            )
        } catch (e: Exception) {
            ""
        }
    }

    /** PB: "24°" như card xem trước (đơn vị đang chọn, không kèm C/F; câu đọc TalkBack vẫn nói rõ đơn vị). */
    private fun degree(tempC: Double): String =
        context.getString(R.string.settings_weather_preview_degree, unit.display(tempC))

    /** Bóng mềm như nhãn icon (`newWidgetTextPaint`): nền kính trong nên chữ phải đọc được trên mọi ảnh nền. */
    private fun textPaint(color: Int, font: WidgetFont, align: Paint.Align): TextPaint =
        newWidgetTextPaint(context, color, font, tabular = true).apply { textAlign = align }

    private companion object {
        const val HOURS = 4

        /** CHỈNH TAY (dp): thấp hơn mức này thì ẩn dòng tình trạng và cao/thấp (split-screen). */
        const val HIDE_SECONDARY_BELOW_DP = 100f

        /** CHỈNH TAY (dp): thấp hơn mức này thì ẩn dải theo giờ của 4x2. */
        const val HIDE_STRIP_BELOW_DP = 110f
    }
}
