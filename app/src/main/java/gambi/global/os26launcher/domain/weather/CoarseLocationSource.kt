package gambi.global.os26launcher.domain.weather

import gambi.global.os26launcher.domain.model.weather.CoarseFix

/**
 * S1 — cổng lấy vị trí thô cho bảng Thời tiết (D6). Bản hiện thực ở `data/weather`, nối ở `di/WeatherModule`.
 *
 * Lấy MỘT LẦN, chỉ khi người dùng bấm "Dùng vị trí hiện tại". Không ném (trừ hủy coroutine) và không log tọa độ.
 */
interface CoarseLocationSource {
    fun hasPermission(): Boolean

    /** null = không lấy được (không quyền, dịch vụ vị trí tắt, hết thời gian). */
    suspend fun getOnce(): CoarseFix?
}
