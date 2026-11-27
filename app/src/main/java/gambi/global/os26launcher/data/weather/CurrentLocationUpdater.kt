package gambi.global.os26launcher.data.weather

import android.util.Log
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.LocationMode
import gambi.global.os26launcher.domain.model.weather.WeatherException
import gambi.global.os26launcher.domain.model.weather.WeatherPolicy
import gambi.global.os26launcher.domain.weather.CoarseLocationSource
import gambi.global.os26launcher.domain.weather.WeatherPlacesRepository
import gambi.global.os26launcher.domain.weather.WeatherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * WX Task 6 (chủ dự án chốt 2026-11-13) — cập nhật vị trí tự động IM LẶNG cho mục "Vị trí hiện tại". Được gọi từ [WeatherRepositoryImpl.refreshIfStale] khi
 * widget đang hiện địa điểm "current" (nên chỉ chạy khi Home hiện và có widget dùng vị trí hiện tại); KHÔNG có `Handler`, `LocationListener` sống lâu hay việc nền.
 *
 * Điều kiện chạy (mọi điều kiện đều phải đúng):
 * - chế độ [LocationMode.AUTO] (Thủ công thì không bao giờ lấy vị trí);
 * - đã cấp quyền vị trí (không bao giờ tự xin quyền ở đây);
 * - lần lấy thành công gần nhất cách đây ≥ chu kỳ "Làm mới sau" (WR, mặc định 1 ngày; lấy tay ở Cài đặt / nút làm mới cũng tính); sau một lần thất bại (không có vị trí,
 *   không tra được tên) thử lại sau ≥ [WeatherPolicy.RETRY_AFTER_ERROR_MS] (chỉ trong RAM: khởi động lại tiến trình thì thử lại);
 * - một lần chạy mỗi lúc.
 *
 * Kết quả: lấy MỘT lần (tối đa 10 giây, `CoarseLocationSource`); chỉ khi cách vị trí tự động đã lưu hơn [MOVED_THRESHOLD_M] (hoặc chưa có vị trí đã lưu) mới tra tên
 * (`reverse`, 1 lệnh OWM) và đổi vị trí tự động; nhỏ hơn thì chỉ ghi giờ lấy (chống nhảy do sai số nhà cung cấp mạng). Đổi tọa độ làm bộ nhớ đệm thời tiết của
 * "current" mất hiệu lực nên widget tải lại nơi mới (hợp đồng của `WeatherRepository`). Không tra được tên thì KHÔNG đổi (không đặt tên giả cho nơi mới).
 *
 * Không log tọa độ hay tên (chỉ `Log.w` với tên lớp ngoại lệ).
 */
@Singleton
class CurrentLocationUpdater @Inject constructor(
    private val places: WeatherPlacesRepository,
    private val location: CoarseLocationSource,
    private val provider: WeatherProvider,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val running = AtomicBoolean(false)

    @Volatile
    private var lastFailAt = 0L

    /**
     * Gọi rẻ (so vài số); không chặn. Chỉ gọi khi đã biết một widget đang hiện "current" và danh sách địa điểm đã nạp. [intervalMs] = chu kỳ
     * "Làm mới sau" (WR, thay hằng 2 giờ).
     */
    fun maybeUpdate(intervalMs: Long) {
        val snapshot = places.places.value
        if (snapshot.mode != LocationMode.AUTO) return
        val now = System.currentTimeMillis()
        if (snapshot.autoFixAt != 0L && now - snapshot.autoFixAt in 0 until intervalMs) return
        if (lastFailAt != 0L && now - lastFailAt in 0 until WeatherPolicy.RETRY_AFTER_ERROR_MS) return
        if (!location.hasPermission()) return
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                update(now, snapshot.autoPlace)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastFailAt = now // lỗi lạ: coi như thất bại một lần, không ném ra scope ứng dụng
                Log.w(TAG, "silent update failed: ${e.javaClass.simpleName}")
            } finally {
                running.set(false)
            }
        }
    }

    /**
     * WR — làm mới tay ("Vị trí hiện tại" trên widget): lấy vị trí ngay, bỏ qua chu kỳ và lần lỗi trước; vẫn chỉ ở chế độ Tự động, đã có quyền,
     * và một lần chạy mỗi lúc (đang chạy thì bỏ qua). Chờ tới khi xong (để thời tiết tải đúng nơi mới).
     */
    suspend fun updateNow() {
        val snapshot = places.places.value
        if (snapshot.mode != LocationMode.AUTO || !location.hasPermission()) return
        if (!running.compareAndSet(false, true)) return
        val now = System.currentTimeMillis()
        try {
            update(now, snapshot.autoPlace)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastFailAt = now
            Log.w(TAG, "manual update failed: ${e.javaClass.simpleName}")
        } finally {
            running.set(false)
        }
    }

    private suspend fun update(now: Long, stored: GeoPlace?) {
        val fix = location.getOnce()
        if (fix == null) {
            lastFailAt = now
            return
        }
        val movedM = stored?.let { distanceMeters(it.lat, it.lon, fix.lat, fix.lon) }
        if (movedM != null && movedM <= MOVED_THRESHOLD_M) {
            places.updateAutoFix(null, now) // đứng yên: chỉ ghi giờ, giữ nguyên địa điểm (và bộ nhớ đệm thời tiết)
            lastFailAt = 0L
            return
        }
        val named = try {
            provider.reverse(fix.lat, fix.lon)
        } catch (e: WeatherException) {
            null
        }
        if (named == null) {
            lastFailAt = now
            return
        }
        places.updateAutoFix(named.copy(lat = fix.lat, lon = fix.lon), now) // false = người dùng vừa chuyển sang Thủ công: bỏ kết quả
        lastFailAt = 0L
    }

    /** Khoảng cách trên mặt cầu (haversine), mét. */
    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    private companion object {
        const val TAG = "LocationUpdater"

        /** CHỈNH TAY (chủ dự án chốt 2026-11-13): chỉ đổi địa điểm khi xa hơn mức này. */
        const val MOVED_THRESHOLD_M = 5_000.0
        const val EARTH_RADIUS_M = 6_371_000.0
    }
}
