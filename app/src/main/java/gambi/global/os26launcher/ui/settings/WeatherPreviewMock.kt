package gambi.global.os26launcher.ui.settings

import android.content.Context
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.weather.TemperatureUnit
import kotlin.math.roundToInt

/**
 * RS Task 7 — dữ liệu MẪU cho card xem trước thời tiết ở màn 1.4 (đúng chữ Figma `302:1900`). Chỉ tên thành phố là thật (vị trí hiện tại,
 * chưa có thì "Hà Nội" như Figma); nhiệt độ, tình trạng, mốc giờ là mẫu, đổi °C/°F theo đơn vị đang chọn. Thay bằng dữ liệu thật khi làm thẻ thời tiết 1.4 đầy đủ.
 */
object WeatherPreviewMock {
    private const val CITY = "Hà Nội"
    private const val NOW_C = 24
    private const val HIGH_C = 27
    private const val LOW_C = 21

    /** Giờ (null = "Bây giờ") và nhiệt độ °C. */
    private val HOURLY_C: List<Pair<Int?, Int>> = listOf(null to 24, 11 to 25, 12 to 26, 13 to 27, 14 to 26)

    fun row(context: Context, id: String, unit: TemperatureUnit, city: String?): SettingsRow.WeatherPreview {
        fun t(c: Int): String {
            val v = if (unit == TemperatureUnit.FAHRENHEIT) (c * 9 / 5f + 32).roundToInt() else c
            return context.getString(R.string.settings_weather_preview_degree, v)
        }
        return SettingsRow.WeatherPreview(
            id = id,
            city = city ?: CITY,
            temperature = t(NOW_C),
            condition = context.getString(R.string.weather_cond_cloudy), // LG: theo ngôn ngữ app (trước là chữ cứng "Nhiều mây")
            highLow = context.getString(R.string.settings_weather_preview_high_low, t(HIGH_C), t(LOW_C)),
            hourly = HOURLY_C.map { (hour, c) ->
                val label = if (hour == null) context.getString(R.string.settings_weather_preview_now)
                else context.getString(R.string.settings_weather_preview_hour, hour)
                label to t(c)
            }
        )
    }
}
