package gambi.global.os26launcher.ui.widget

import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.HourlyPoint
import gambi.global.os26launcher.domain.model.weather.TemperatureUnit
import gambi.global.os26launcher.domain.model.weather.WeatherCondition
import gambi.global.os26launcher.domain.model.weather.WeatherPlaces
import gambi.global.os26launcher.domain.model.weather.WeatherSnapshot
import gambi.global.os26launcher.domain.model.weather.WeatherState
import gambi.global.os26launcher.domain.weather.WeatherPlacesRepository
import gambi.global.os26launcher.domain.weather.WeatherPreferencesRepository
import gambi.global.os26launcher.domain.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * G3e Task 5 — cầu nối giữa [WeatherRepository] và các ô widget thời tiết (view). `object` sống xuyên Activity, KHÔNG giữ `View`:
 * - [stateOf]: các renderer đọc trạng thái hiện tại ở `prepare`.
 * - repository đổi trạng thái -> [WidgetTicker.tickNow] để mọi widget đang HIỆN vẽ lại (không có widget hiện thì không làm gì).
 * - [onVisible]: gọi từ `prepare` mỗi nhịp phút của [WidgetTicker] (chỉ chạy khi có widget hiện) và khi một widget hiện lên;
 *   repository tự so giờ với chặn 2 giờ của từng địa điểm nên gọi rất rẻ. Không `Handler`, không receiver riêng (D2).
 */
object WeatherWidgetHub {
    private var repository: WeatherRepository? = null

    /** WU: null cho tới khi đọc được đơn vị từ DataStore; trong lúc đó [stateOf] báo Loading để không chớp sai đơn vị. */
    @Volatile
    private var loadedUnit: TemperatureUnit? = null

    private var places: WeatherPlacesRepository? = null

    /**
     * WX Task 5: false cho tới khi danh sách địa điểm (và ánh xạ widget) đọc xong. Trước đó chưa biết widget nào hiện địa điểm nào nên KHÔNG kích hoạt
     * làm mới (kẻo tải nhầm "Vị trí hiện tại" cho widget đang dùng nơi khác) và báo Loading.
     */
    @Volatile
    private var placesLoaded = false

    /**
     * WX Task 5: `itemId` của widget thời tiết vừa được bấm, để bảng Thời tiết biết đang chọn địa điểm cho widget nào. Chỉ đọc trên luồng chính,
     * ngay sau lần bấm (đi qua `object` để khỏi đổi chữ ký callback trong `HomeActivity`, file độc quyền).
     */
    @Volatile
    var tappedWidgetId: Long = 0L

    fun init(
        repo: WeatherRepository,
        units: WeatherPreferencesRepository,
        placesRepo: WeatherPlacesRepository,
        scope: CoroutineScope
    ) {
        if (repository != null) return
        repository = repo
        places = placesRepo
        scope.launch {
            placesRepo.awaitLoaded()
            placesLoaded = true
            WidgetTicker.tickNow()
        }
        // Một widget đổi địa điểm (hoặc được dọn) -> widget đang hiện dựng lại ngay; không gọi mạng ở đây (widget gọi `onVisible` ở `prepare`).
        scope.launch { placesRepo.widgetPlaces.collect { WidgetTicker.tickNow() } }
        scope.launch { repo.states.collect { WidgetTicker.tickNow() } }
        // WU: đổi đơn vị -> mọi widget đang hiện dựng lại ngay (không gọi mạng; widget ẩn thì tự đọc lại khi hiện, WidgetTicker.register).
        scope.launch {
            units.unit.collect {
                loadedUnit = it
                WidgetTicker.tickNow()
            }
        }
    }

    /** Đơn vị hiển thị; trước khi đọc xong là mặc định (chỉ dùng cho bản xem trước, xem [stateOf]). */
    val unit: TemperatureUnit
        get() = loadedUnit ?: TemperatureUnit.DEFAULT

    /** WX: trạng thái của địa điểm [placeId] (mã của `WeatherPlacesRepository`); Loading cho tới khi đọc xong đơn vị và có trạng thái của nơi đó. */
    fun stateOf(placeId: String): WeatherState =
        if (loadedUnit == null || !placesLoaded) WeatherState.Loading
        else repository?.states?.value?.get(placeId) ?: WeatherState.Loading

    /** WX Task 5: mã địa điểm widget [itemId] đang hiển thị ("Vị trí hiện tại" nếu chưa chọn hoặc địa điểm đã bị xóa). Đọc rẻ, gọi mỗi `prepare`. */
    fun placeIdOf(itemId: Long): String = places?.widgetPlaceId(itemId) ?: WeatherPlaces.CURRENT

    /** Gọi từ `prepare` của widget đang hiện: kích hoạt làm mới (nếu đến hạn) cho địa điểm của widget đó. Repository tự chặn nên gọi rất rẻ. */
    fun onVisible(placeId: String) {
        if (placesLoaded) repository?.refreshIfStale(placeId)
    }

    /** Dữ liệu mẫu cố định cho bảng chọn widget (không phụ thuộc mạng) và cho công tắc debug. */
    fun sample(): WeatherSnapshot {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val base = now.toEpochSecond()
        val conds = listOf(
            WeatherCondition.PARTLY_CLOUDY,
            WeatherCondition.CLOUDY,
            WeatherCondition.RAIN,
            WeatherCondition.CLEAR
        )
        val temps = doubleArrayOf(29.0, 27.0, 25.0, 24.0)
        return WeatherSnapshot(
            place = GeoPlace("Hà Nội", 21.03, 105.85, "VN"),
            tempC = 28.0,
            condition = WeatherCondition.PARTLY_CLOUDY,
            isNight = false,
            highC = 31.0,
            lowC = 24.0,
            hourly = List(4) {
                HourlyPoint(
                    base + 3 * 3600L * (it + 1),
                    temps[it],
                    conds[it],
                    false
                )
            },
            utcOffsetSeconds = now.offset.totalSeconds,
            fetchedAtMillis = System.currentTimeMillis()
        )
    }
}
