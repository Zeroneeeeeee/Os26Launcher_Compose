package gambi.global.os26launcher.domain.weather

import gambi.global.os26launcher.domain.model.weather.ForecastSummary
import gambi.global.os26launcher.domain.model.weather.HourlyPoint
import gambi.global.os26launcher.domain.model.weather.WeatherCondition
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationConfig
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationKind
import gambi.global.os26launcher.domain.model.weather.WeatherSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * WN — rút bản tóm tắt thông báo từ [WeatherSnapshot.forecast]. Thuần, không làm tròn (WU1). Quy tắc của APP (không phải của OWM), plan D6:
 *
 * - Ngày theo múi giờ của ĐỊA ĐIỂM ([WeatherSnapshot.utcOffsetSeconds]).
 * - DAILY: mốc cùng ngày với `now` chưa kết thúc (`epoch + 3h > now`), cần >= [WeatherNotificationConfig.MIN_POINTS_TODAY].
 *   TOMORROW: mốc của ngày kế, cần >= [WeatherNotificationConfig.MIN_POINTS_TOMORROW]. Thiếu -> null (không đoán).
 * - Cao/thấp = max/min `tempC` của các mốc đó.
 * - Tình trạng đại diện = nặng nhất trong các mốc 06:00-21:59 giờ địa điểm (không có mốc ban ngày thì lấy mọi mốc); thứ tự nặng:
 *   giông > tuyết > mưa > sương mù > nhiều mây > ít mây > quang > không rõ.
 * - Khoảng có mưa = từ đầu mốc mưa/giông/tuyết đầu tiên tới cuối mốc cuối cùng (phút trong ngày, tối đa 1440).
 *
 * Ví dụ: snapshot chỉ có 3 mốc ngày mai -> TOMORROW = null; mốc 22:00 thuộc ngày D, mốc 01:00 sau nửa đêm thuộc ngày D+1.
 */
object WeatherForecastSummarizer {

    private const val MINUTES_PER_DAY = 24 * 60
    private const val DAY_START_HOUR = 6
    private const val DAY_END_HOUR = 21
    private const val MAX_OFFSET_SECONDS = 18 * 3600

    fun summarize(snapshot: WeatherSnapshot, kind: WeatherNotificationKind, nowMillis: Long): ForecastSummary? {
        val offset = ZoneOffset.ofTotalSeconds(snapshot.utcOffsetSeconds.coerceIn(-MAX_OFFSET_SECONDS, MAX_OFFSET_SECONDS))
        val nowSec = Math.floorDiv(nowMillis, 1000L)
        val today = Instant.ofEpochSecond(nowSec).atOffset(offset).toLocalDate()
        val slot = WeatherNotificationConfig.SLOT_SECONDS
        val points: List<HourlyPoint>
        val minPoints: Int
        when (kind) {
            WeatherNotificationKind.DAILY -> {
                points = snapshot.forecast.filter { localDate(it, offset) == today && it.epochSeconds + slot > nowSec }
                minPoints = WeatherNotificationConfig.MIN_POINTS_TODAY
            }

            WeatherNotificationKind.TOMORROW -> {
                points = snapshot.forecast.filter { localDate(it, offset) == today.plusDays(1) }
                minPoints = WeatherNotificationConfig.MIN_POINTS_TOMORROW
            }
        }
        if (points.size < minPoints) return null

        val daytime = points.filter { localHour(it, offset) in DAY_START_HOUR..DAY_END_HOUR }
        val condition = daytime.ifEmpty { points }.maxBy { severity(it.condition) }.condition
        val wet = points.filter { isPrecipitation(it.condition) }.sortedBy { it.epochSeconds }
        val start = wet.firstOrNull()?.let { minuteOfDay(it.epochSeconds, offset) }
        val end = wet.lastOrNull()
            ?.let { (minuteOfDay(it.epochSeconds, offset) + (slot / 60).toInt()).coerceAtMost(MINUTES_PER_DAY) }
        return ForecastSummary(
            kind = kind,
            placeName = snapshot.place.displayName,
            highC = points.maxOf { it.tempC },
            lowC = points.minOf { it.tempC },
            condition = condition,
            precipStartMinute = start,
            precipEndMinute = end
        )
    }

    private fun localDate(p: HourlyPoint, offset: ZoneOffset): LocalDate =
        Instant.ofEpochSecond(p.epochSeconds).atOffset(offset).toLocalDate()

    private fun localHour(p: HourlyPoint, offset: ZoneOffset): Int =
        Instant.ofEpochSecond(p.epochSeconds).atOffset(offset).hour

    private fun minuteOfDay(epochSeconds: Long, offset: ZoneOffset): Int {
        val t = Instant.ofEpochSecond(epochSeconds).atOffset(offset)
        return t.hour * 60 + t.minute
    }

    private fun isPrecipitation(c: WeatherCondition): Boolean =
        c == WeatherCondition.RAIN || c == WeatherCondition.THUNDERSTORM || c == WeatherCondition.SNOW

    private fun severity(c: WeatherCondition): Int = when (c) {
        WeatherCondition.THUNDERSTORM -> 7
        WeatherCondition.SNOW -> 6
        WeatherCondition.RAIN -> 5
        WeatherCondition.FOG -> 4
        WeatherCondition.CLOUDY -> 3
        WeatherCondition.PARTLY_CLOUDY -> 2
        WeatherCondition.CLEAR -> 1
        WeatherCondition.UNKNOWN -> 0
    }
}
