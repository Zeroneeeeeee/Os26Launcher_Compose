package gambi.global.os26launcher.domain.model.weather

import kotlin.math.roundToInt

/**
 * WU — đơn vị hiển thị nhiệt độ. Dữ liệu thời tiết luôn ở °C thô (Double, chưa làm tròn); đơn vị chỉ là cách hiển thị, làm tròn MỘT lần
 * ở [display] để °F không lệch do làm tròn trung gian (vd 24.4 °C = 75.9 °F -> 76, không phải 24 -> 75).
 */
enum class TemperatureUnit {
    CELSIUS, FAHRENHEIT;

    /** Số nguyên để hiển thị. `roundToInt` làm tròn nửa lên; kết quả là Int nên không bao giờ in "-0". */
    fun display(tempC: Double): Int = when (this) {
        CELSIUS -> tempC.roundToInt()
        FAHRENHEIT -> (tempC * 9.0 / 5.0 + 32.0).roundToInt()
    }

    companion object {
        val DEFAULT = CELSIUS

        /** Tên lạ (bản sau đổi tên) hoặc null -> [DEFAULT], không crash. */
        fun fromName(name: String?): TemperatureUnit = entries.find { it.name == name } ?: DEFAULT
    }
}
