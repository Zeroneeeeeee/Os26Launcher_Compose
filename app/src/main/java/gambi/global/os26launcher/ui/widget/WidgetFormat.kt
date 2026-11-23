package gambi.global.os26launcher.ui.widget

import android.text.format.DateFormat
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * G2c Task 5 — định dạng giờ / ngày cho widget, theo ngôn ngữ và 12/24 giờ của máy. Gọi từ `prepare` (mỗi phút), không từ `draw`.
 * Tên thứ / tháng của `java.time` theo locale; tiếng Việt trả chữ thường nên [cap] viết hoa chữ đầu (suy luận chưa kiểm chứng, kiểm
 * ở Task 5 trên máy).
 */
internal object WidgetFormat {

    fun cap(text: String, locale: Locale): String =
        if (text.isEmpty()) text else text.replaceFirstChar { it.titlecase(locale) }

    /** Giờ theo [is24h], mẫu tốt nhất của locale (vd "HH:mm", "h:mm a"); lỗi mẫu thì dùng mẫu dự phòng. */
    fun time(now: ZonedDateTime, locale: Locale, is24h: Boolean): String {
        val pattern = try {
            DateFormat.getBestDateTimePattern(locale, if (is24h) "Hm" else "hm")
        } catch (e: Exception) {
            if (is24h) "HH:mm" else "h:mm a"
        }
        return try {
            DateTimeFormatter.ofPattern(pattern, locale).format(now)
        } catch (e: IllegalArgumentException) {
            DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale).format(now)
        }
    }

    /** Một dòng ngày dài, vd "Thứ Hai, 3 tháng 11" / "Monday, November 3". */
    fun dateLong(now: ZonedDateTime, locale: Locale): String {
        val pattern = try {
            DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        } catch (e: Exception) {
            "EEEE d MMMM"
        }
        val text = try {
            DateTimeFormatter.ofPattern(pattern, locale).format(now)
        } catch (e: IllegalArgumentException) {
            DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(now)
        }
        return cap(text, locale)
    }

    fun weekdayFull(now: ZonedDateTime, locale: Locale): String =
        cap(now.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale), locale)

    fun weekdayShort(day: DayOfWeek, locale: Locale): String =
        cap(day.getDisplayName(TextStyle.SHORT_STANDALONE, locale), locale)

    fun weekdayNarrow(day: DayOfWeek, locale: Locale): String =
        cap(day.getDisplayName(TextStyle.NARROW_STANDALONE, locale), locale)

    fun monthFull(now: ZonedDateTime, locale: Locale): String =
        cap(now.month.getDisplayName(TextStyle.FULL_STANDALONE, locale), locale)
}
