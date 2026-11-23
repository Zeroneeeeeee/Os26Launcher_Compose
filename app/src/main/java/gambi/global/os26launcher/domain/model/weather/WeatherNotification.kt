package gambi.global.os26launcher.domain.model.weather

/**
 * WN — hai thông báo thời tiết (chủ dự án chốt 2026-11-13): dự báo hằng ngày 07:00 và dự báo ngày mai 21:00, giờ cố định theo giờ máy.
 * Lưu bằng tên enum nên thêm giá trị sau không cần di trú. Chữ hiển thị ở `strings.xml`.
 */
enum class WeatherNotificationKind(val hour: Int, val minute: Int) {
    DAILY(7, 0),
    TOMORROW(21, 0)
}

/** Hai công tắc của nhóm "Thông báo" (Cài đặt → Thời tiết); mặc định tắt. */
data class WeatherNotificationSettings(
    val daily: Boolean = false,
    val tomorrow: Boolean = false
) {
    fun isEnabled(kind: WeatherNotificationKind): Boolean = when (kind) {
        WeatherNotificationKind.DAILY -> daily
        WeatherNotificationKind.TOMORROW -> tomorrow
    }

    val any: Boolean get() = daily || tomorrow
}

/**
 * Hằng chính sách của thông báo thời tiết. CHỈNH TAY, mỗi thứ MỘT chỗ (điều phối duyệt plan 2026-11-13).
 * (Mức quan trọng của kênh thông báo là hằng của `ui` ở Task 4, vì cần hằng của Android.)
 */
object WeatherNotificationConfig {
    /**
     * Lịch bằng báo thức CHÍNH XÁC (cần quyền đặc biệt "Báo thức & lời nhắc" từ Android 13) thay vì không chính xác.
     * Mặc định false (Q1 của plan: chấp nhận trễ tới ~1 giờ). Task 3 đọc hằng này; đổi thành true sẽ cần thêm quyền và màn xin.
     */
    const val USE_EXACT_ALARMS = false

    /** Quá giờ mục tiêu tới mức này vẫn còn đăng bù (máy tắt/Doze dài làm lỡ); quá nữa thì bỏ thông báo hôm đó. */
    const val GRACE_MS = 3 * 60 * 60_000L

    /** Tải lỗi thì chỉ dùng cache có `fetchedAtMillis` không cũ hơn mức này. */
    const val MAX_STALE_CACHE_MS = 12 * 60 * 60_000L

    /** Mỗi mốc dự báo OWM phủ 3 giờ. */
    const val SLOT_SECONDS = 3 * 60 * 60L

    /** Cache thành công có ít mốc dự báo hơn mức này (cache của bản trước WN) thì thông báo được tải một lần dù chưa đến hạn 2 giờ. */
    const val MIN_FORECAST_POINTS = 8

    /** Hạn chót CHỜ dữ liệu khi báo thức nổ (trong `goAsync`, hệ thống mong xong dưới 10 giây) và khi chạy job (job có thời gian dài hơn). */
    const val RECEIVER_FETCH_TIMEOUT_MS = 8_000L
    const val JOB_FETCH_TIMEOUT_MS = 30_000L

    /** Số mốc tối thiểu để tóm tắt (thiếu thì không đăng, không đoán). */
    const val MIN_POINTS_TODAY = 2
    const val MIN_POINTS_TOMORROW = 4
}

/** Vì sao một thông báo không đăng (chỉ [NO_DATA] đáng thử lại khi có mạng). */
enum class NotificationSkip { WINDOW, SHOWN, NO_KEY, NO_PLACE, NO_DATA }

/** Kết quả chuẩn bị một thông báo: có bản tóm tắt để đăng, hoặc bỏ qua kèm lý do. */
sealed interface NotificationPrep {
    data class Ready(val summary: ForecastSummary) : NotificationPrep
    data class Skip(val reason: NotificationSkip) : NotificationPrep
}

/**
 * Bản tóm tắt để dựng thông báo. Nhiệt độ là °C THÔ (làm tròn một lần lúc hiển thị, WU1). [precipStartMinute]/[precipEndMinute] là phút trong
 * ngày theo giờ ĐỊA ĐIỂM (end tối đa 1440), null khi không có mưa/giông/tuyết.
 */
data class ForecastSummary(
    val kind: WeatherNotificationKind,
    val placeName: String,
    val highC: Double,
    val lowC: Double,
    val condition: WeatherCondition,
    val precipStartMinute: Int? = null,
    val precipEndMinute: Int? = null
)
