package gambi.global.os26launcher.data.weather

import android.util.Log
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.WeatherError
import gambi.global.os26launcher.domain.model.weather.WeatherException
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationConfig
import gambi.global.os26launcher.domain.model.weather.WeatherPlaces
import gambi.global.os26launcher.domain.model.weather.WeatherPolicy
import gambi.global.os26launcher.domain.model.weather.WeatherSnapshot
import gambi.global.os26launcher.domain.model.weather.WeatherState
import gambi.global.os26launcher.domain.weather.WeatherPlacesRepository
import gambi.global.os26launcher.domain.weather.WeatherProvider
import gambi.global.os26launcher.domain.weather.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * G3e Task 4 + WX Task 2 — trạng thái thời tiết THEO TỪNG ĐỊA ĐIỂM cho widget và bảng. Hợp đồng chặn tần suất ở KDoc của [WeatherRepository]:
 *
 * | Lần thử gần nhất của địa điểm | Lần kế sớm nhất |
 * |---|---|
 * | chưa có / không còn hợp lệ (đổi tọa độ) | ngay |
 * | thành công | +2 giờ |
 * | 429 | +2 giờ |
 * | mạng / máy chủ / parse | +15 phút |
 * | 401 | không (tới khi tiến trình khởi động lại; khóa chỉ đổi khi cài bản mới) |
 *
 * Ai kích hoạt: chỉ [refreshIfStale] do widget đang hiện gọi (WX); với mã CURRENT nó còn gọi [CurrentLocationUpdater] (Task 6). Mọi lệnh đi qua [mutex] (một lượt một lúc cho cả app). `lastAttemptAt` lưu ở file
 * nên khởi động lại không vượt được chặn. Địa điểm lấy từ [WeatherPlacesRepository]. Không log chẩn đoán (mọi `Log.w` chỉ ghi tên lớp ngoại lệ).
 */
@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val provider: WeatherProvider,
    private val store: WeatherStore,
    private val places: WeatherPlacesRepository,
    private val locationUpdater: CurrentLocationUpdater,
    config: WeatherProviderConfig,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher
) : WeatherRepository {

    override val hasKey: Boolean = config.apiKey.isNotBlank()

    private val _states = MutableStateFlow<Map<String, WeatherState>>(emptyMap())
    override val states: StateFlow<Map<String, WeatherState>> = _states

    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()

    /**
     * Bộ nhớ đệm theo mã địa điểm. Bản đồ bất biến, thay nguyên khối TRONG [mutex]; lần đọc nhanh ở [refreshIfStale] không khóa (chấp nhận trễ một nhịp).
     */
    @Volatile
    private var entries: Map<String, StoredWeatherEntry> = emptyMap()

    @Volatile
    private var keyDead = false

    init {
        scope.launch {
            try {
                val stored = withContext(io) { store.load() }
                places.awaitLoaded() // địa điểm lấy từ weather_places.json (đã di trú từ G3e nếu cần)
                mutex.withLock {
                    entries = stored?.entries.orEmpty()
                    reconcile(places.places.value, save = false)
                }
            } finally {
                loaded.complete(Unit) // luôn hoàn tất: lệnh sau không treo ở loaded.await() (B1)
            }
            // Danh sách / chế độ / thành phố đổi (Cài đặt, bảng chọn): bỏ mục không còn hợp lệ, phát lại trạng thái.
            places.places.collect { list -> mutex.withLock { reconcile(list, save = true) } }
        }
    }

    override fun refreshIfStale(placeId: String) {
        if (!hasKey) return
        // WX Task 6: chỉ widget đang hiện "Vị trí hiện tại" gọi tới đây với CURRENT => cũng là tín hiệu "đang có widget dùng vị trí hiện tại hiện lên" cho việc cập nhật vị trí im lặng.
        if (placeId == WeatherPlaces.CURRENT && loaded.isCompleted) locationUpdater.maybeUpdate()
        if (loaded.isCompleted && !due(placeId, System.currentTimeMillis())) return
        scope.launch {
            loaded.await()
            mutex.withLock {
                val now = System.currentTimeMillis()
                if (due(placeId, now)) doRefresh(placeId, now)
            }
        }
    }

    override suspend fun snapshotForNotification(placeId: String, timeoutMs: Long): WeatherSnapshot? {
        if (!hasKey) return null
        // Việc tải chạy ở scope ứng dụng (không phải con của người gọi): HTTP chặn luồng nên `withTimeout` trực tiếp không ngắt được; ở đây người gọi
        // chỉ CHỜ tối đa [timeoutMs] (await hủy được), hết hạn thì dùng cache và việc tải vẫn chạy tiếp (kết quả vào cache cho lần sau).
        val work = scope.async {
            try {
                loadForNotification(placeId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "notification load failed: ${e.javaClass.simpleName}")
                null
            }
        }
        return withTimeoutOrNull(timeoutMs) { work.await() } ?: cachedSnapshot(placeId)
    }

    private suspend fun loadForNotification(placeId: String): WeatherSnapshot? {
        loaded.await()
        return mutex.withLock {
            // WN: CỐ Ý không gọi locationUpdater.maybeUpdate() như refreshIfStale(CURRENT): thông báo chạy nền, chủ dự án chốt "không vị trí nền"
            // (cũng không có ACCESS_BACKGROUND_LOCATION); nó chỉ dùng "Vị trí hiện tại" như đang lưu.
            val now = System.currentTimeMillis()
            // Cùng quy tắc 2 giờ / 15 phút / 401 như widget. NGOẠI LỆ MỘT LẦN: mục cache thành công nhưng chưa có đủ mốc dự báo (cache của bản trước WN)
            // thì tải một lần dù chưa đến hạn 2 giờ (2 lệnh); sau đó có 12 mốc nên không lặp. Lỗi/429 vẫn theo quy tắc thường (không ngoại lệ).
            if (dueReason(placeId, now) != null || needsForecastOnce(placeId)) doRefresh(placeId, now)
            cachedSnapshot(placeId)
        }
    }

    private fun needsForecastOnce(placeId: String): Boolean {
        if (keyDead) return false
        val place = places.resolve(placeId) ?: return false
        val entry = validEntry(placeId, place) ?: return false
        return entry.lastResult == RESULT_OK &&
            (entry.snapshot?.forecast?.size ?: 0) < WeatherNotificationConfig.MIN_FORECAST_POINTS
    }

    /** Snapshot hợp lệ trong cache của [placeId] (đúng tọa độ), không tải gì. */
    private fun cachedSnapshot(placeId: String): WeatherSnapshot? {
        val place = places.resolve(placeId) ?: return null
        return validEntry(placeId, place)?.snapshot
    }

    override suspend fun search(query: String): List<GeoPlace> = mapFailures { provider.search(query) }

    override suspend fun reverse(lat: Double, lon: Double): GeoPlace? =
        mapFailures { provider.reverse(lat, lon) }

    /** Hợp đồng (B1): chỉ [WeatherException] hoặc hủy coroutine thoát ra; ngoại lệ lạ thành lỗi, không mang `cause`. */
    private inline fun <T> mapFailures(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: WeatherException) {
        throw e
    } catch (e: Exception) {
        throw WeatherException(errorOf(e))
    }

    private fun errorOf(e: Exception): WeatherError {
        if (e is WeatherException) return e.error
        // Chỉ tên lớp: thông điệp có thể chứa URL/khóa (quy tắc bí mật G3e).
        Log.w(TAG, "weather call failed: ${e.javaClass.simpleName}")
        return if (e is IllegalArgumentException) WeatherError.PARSE else WeatherError.SERVER
    }

    // ---- nội bộ ----

    /** Mục của [placeId] nếu còn hợp lệ với địa điểm [place] (cùng tọa độ). */
    private fun validEntry(placeId: String, place: GeoPlace): StoredWeatherEntry? =
        entries[placeId]?.takeIf { sameSpot(it.place, place) }

    private fun due(placeId: String, now: Long): Boolean = dueReason(placeId, now) != null

    /** null = chưa đến hạn (hoặc không làm mới được); ngược lại là lý do đến hạn. CHỈ dựa vào `lastAttemptAt` và `lastResult` của mục, không đọc dữ liệu thời tiết. */
    private fun dueReason(placeId: String, now: Long): String? {
        if (keyDead) return null
        val place = places.resolve(placeId) ?: return null
        val entry = validEntry(placeId, place) ?: return "noEntry" // chưa có dữ liệu cho nơi này: tải lần đầu ngay
        if (entry.lastAttemptAt == 0L) return "noAttempt"
        val age = now - entry.lastAttemptAt
        if (age < 0) return "clockBack" // giờ máy bị chỉnh lùi: coi như hết hạn
        val interval =
            if (entry.lastResult == RESULT_OK || entry.lastResult == WeatherError.RATE_LIMITED.name) {
                WeatherPolicy.REFRESH_INTERVAL_MS
            } else {
                WeatherPolicy.RETRY_AFTER_ERROR_MS
            }
        return if (age >= interval) "expired" else null
    }

    private suspend fun doRefresh(placeId: String, now: Long) {
        val place = places.resolve(placeId) ?: return
        val previous = validEntry(placeId, place)
        var result = RESULT_OK
        var fresh = previous?.snapshot
        var attemptAt = previous?.lastAttemptAt ?: 0L
        try {
            fresh = provider.fetch(place)
            attemptAt = now
        } catch (e: CancellationException) {
            throw e
        } catch (e: WeatherException) {
            result = e.error.name
            if (e.error != WeatherError.NO_KEY) attemptAt = now // NO_KEY: chưa thử gì, không ghi lần thử
            if (e.error == WeatherError.INVALID_KEY) keyDead = true
        } catch (e: Exception) {
            // B1: lỗi lạ cũng là một lần thử thất bại (thử lại sau RETRY_AFTER_ERROR_MS, không mỗi phút).
            attemptAt = now
            result = errorOf(e).name
        }
        if (result == WeatherError.NO_KEY.name) return // chưa thử gì: không ghi `attempt`
        entries = entries + (placeId to StoredWeatherEntry(place, fresh, attemptAt, result))
        withContext(io) { store.save(StoredWeather(entries)) }
        publish(places.places.value)
    }

    /**
     * Gọi trong [mutex]: bỏ mục của địa điểm không còn trong danh sách hoặc đổi tọa độ, rồi phát trạng thái. Chỉ ghi file khi có mục bị bỏ
     * ([save] = false lúc nạp đầu: chưa có gì thay đổi để ghi).
     */
    private suspend fun reconcile(list: WeatherPlaces, save: Boolean) {
        val kept = entries.filter { (id, entry) ->
            val place = list.resolve(id)
            place != null && sameSpot(entry.place, place)
        }
        if (kept.size != entries.size) {
            entries = kept
            if (save) withContext(io) { store.save(StoredWeather(entries)) }
        }
        publish(list)
    }

    private fun publish(list: WeatherPlaces) {
        val ids = listOf(WeatherPlaces.CURRENT) + list.saved.map { WeatherPlaces.placeIdOf(it) }
        _states.value = ids.associateWith { stateOf(it, list) }
    }

    private fun stateOf(placeId: String, list: WeatherPlaces): WeatherState {
        if (!hasKey) return WeatherState.NoKey
        val place = list.resolve(placeId) ?: return WeatherState.NoLocation
        val entry = validEntry(placeId, place) ?: return WeatherState.Loading
        val error =
            if (entry.lastResult == RESULT_OK) null else runCatching { WeatherError.valueOf(entry.lastResult) }.getOrNull()
        val snap = entry.snapshot
        return when {
            snap != null -> {
                val old = System.currentTimeMillis() - snap.fetchedAtMillis > WeatherPolicy.STALE_AFTER_MS
                WeatherState.Ready(snap, stale = old || error != null, lastError = error)
            }

            error != null -> WeatherState.Failed(error)
            else -> WeatherState.Loading
        }
    }

    private fun sameSpot(a: GeoPlace, b: GeoPlace): Boolean =
        abs(a.lat - b.lat) < SPOT_EPSILON && abs(a.lon - b.lon) < SPOT_EPSILON

    private companion object {
        const val RESULT_OK = "ok"
        const val TAG = "WeatherRepo"

        /** Hai tọa độ (đã làm tròn 2 số thập phân) coi là cùng nơi khi lệch dưới giá trị này. */
        const val SPOT_EPSILON = 0.0005
    }
}
