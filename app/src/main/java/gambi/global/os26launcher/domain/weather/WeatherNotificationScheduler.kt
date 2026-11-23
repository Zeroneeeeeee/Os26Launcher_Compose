package gambi.global.os26launcher.domain.weather

import gambi.global.os26launcher.domain.model.weather.WeatherNotificationKind

/**
 * WN — lịch nổ của hai thông báo thời tiết (plan D1/D7). Bản hiện thực: `data/weather/notify/AlarmWeatherNotificationScheduler` (AlarmManager).
 * Mọi hàm idempotent và chạy được từ luồng bất kỳ (tự chuyển sang IO); không ném (lỗi chỉ log tên lớp).
 */
interface WeatherNotificationScheduler {
    /**
     * Đặt lại báo thức của từng loại theo trạng thái hiện tại: công tắc bật + build có khóa thì đặt báo thức kế tiếp
     * ([WeatherNotificationPlanner.nextTrigger]), ngược lại hủy. Gọi khi: đổi công tắc, khởi động máy, cập nhật app, đổi giờ/múi giờ,
     * tiến trình tạo. Cùng `PendingIntent` nên báo thức cũ bị thay, không nhân đôi.
     */
    suspend fun rearm()

    /** Báo thức [kind] vừa nổ: đặt NGAY báo thức của lần kế (mục tiêu ngày mai) trước khi làm việc khác, để chuỗi không đứt dù job hỏng. */
    suspend fun rearmAfterFire(kind: WeatherNotificationKind)
}

/** Hằng của intent báo thức (chuỗi thuần để `data` đặt và `ui` nhận mà không import nhau). */
object WeatherNotificationIntents {
    const val ACTION_FIRE = "gambi.global.os26launcher.WN_FIRE"
    const val EXTRA_KIND = "kind"

    /** Chỉ công cụ gỡ lỗi (bản debug) đặt: bỏ qua kiểm thời gian đăng và ngày đã đăng, không ghi "đã đăng". */
    const val EXTRA_FORCE = "force"

    /** Tên enum -> loại; null hoặc lạ -> null. */
    fun kindOf(name: String?): WeatherNotificationKind? = WeatherNotificationKind.entries.firstOrNull { it.name == name }
}
