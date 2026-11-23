package gambi.global.os26launcher.data.weather

import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.domain.model.weather.AddPlaceResult
import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.LocationMode
import gambi.global.os26launcher.domain.model.weather.WeatherPlaces
import gambi.global.os26launcher.domain.weather.WeatherPlacesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WX Task 1 — danh sách địa điểm + ánh xạ widget -> địa điểm (D1), lưu ở `weather_places.json` ([WeatherPlacesStore]). Không gọi mạng.
 *
 * - Mọi ghi đi qua một [mutex], ghi file ở IO rồi mới phát trạng thái mới; trạng thái trong RAM luôn là nguồn thật, file chỉ là bản lưu.
 * - **Di trú từ G3e:** chưa có `weather_places.json` mà `weather.json` (bản G3e) có địa điểm đã chọn thì địa điểm đó thành thành phố thủ công và
 *   chế độ MANUAL (không phân biệt được nó từ vị trí hay gõ tay; chấp nhận), rồi ghi file mới ngay để lần sau không di trú lại.
 * - Ánh xạ widget là map bất biến thay nguyên khối (đọc từ luồng chính mỗi lần vẽ, không khóa); tối đa [MAX_WIDGET_ENTRIES] mục, đầy thì bỏ mục cũ nhất
 *   (mục mồ côi do widget bị xóa gián tiếp vô hại vì `itemId` không bao giờ dùng lại).
 */
@Singleton
class WeatherPlacesRepositoryImpl @Inject constructor(
    private val store: WeatherPlacesStore,
    private val legacyStore: WeatherStore,
    @ApplicationScope scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher
) : WeatherPlacesRepository {

    private val _places = MutableStateFlow(WeatherPlaces())
    override val places: StateFlow<WeatherPlaces> = _places

    /** WX Task 5: ánh xạ widget -> địa điểm là StateFlow (map bất biến thay nguyên khối) để widget đang hiện vẽ lại khi một widget đổi địa điểm. Ghi trong [mutex]; đọc không khóa. */
    private val _widgets = MutableStateFlow<Map<Long, String>>(emptyMap())
    override val widgetPlaces: StateFlow<Map<Long, String>> = _widgets

    private val widgets: Map<Long, String> get() = _widgets.value

    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                val stored = withContext(io) { store.load() ?: migrateFromG3e() }
                mutex.withLock {
                    if (stored != null) {
                        _widgets.value = stored.widgets
                        _places.value = WeatherPlaces(
                            mode = stored.mode,
                            autoPlace = stored.autoPlace,
                            manualPlace = stored.manualPlace,
                            saved = stored.saved.take(WeatherPlaces.MAX_SAVED),
                            autoFixAt = stored.autoFixAt
                        )
                    }
                }
            } finally {
                loaded.complete(Unit) // luôn hoàn tất: lệnh sau không treo ở awaitLoaded()
            }
        }
    }

    /** Chạy ở IO. */
    private fun migrateFromG3e(): StoredPlaces? {
        val place = legacyStore.load()?.legacyPlace ?: return null
        val migrated = StoredPlaces(LocationMode.MANUAL, null, WeatherPlaces.rounded(place), emptyList(), emptyMap())
        store.save(migrated)
        return migrated
    }

    override suspend fun awaitLoaded() = loaded.await()

    override suspend fun add(place: GeoPlace): AddPlaceResult {
        loaded.await()
        return mutex.withLock {
            val now = _places.value
            val rounded = WeatherPlaces.rounded(place)
            val id = WeatherPlaces.placeIdOf(rounded)
            when {
                now.isFull -> AddPlaceResult.FULL
                now.current?.let { WeatherPlaces.placeIdOf(it) == id } == true ||
                        now.saved.any { WeatherPlaces.placeIdOf(it) == id } -> AddPlaceResult.DUPLICATE

                else -> {
                    commit(now.copy(saved = now.saved + rounded), widgets)
                    AddPlaceResult.ADDED
                }
            }
        }
    }

    override suspend fun remove(placeId: String) {
        loaded.await()
        mutex.withLock {
            val now = _places.value
            if (placeId == WeatherPlaces.CURRENT || now.saved.none { WeatherPlaces.placeIdOf(it) == placeId }) return
            commit(
                now.copy(saved = now.saved.filterNot { WeatherPlaces.placeIdOf(it) == placeId }),
                widgets.filterValues { it != placeId }
            )
        }
    }

    override suspend fun setMode(mode: LocationMode) {
        loaded.await()
        mutex.withLock {
            val now = _places.value
            if (now.mode != mode) commit(now.copy(mode = mode), widgets)
        }
    }

    override suspend fun setAutoPlace(place: GeoPlace) {
        loaded.await()
        mutex.withLock {
            commit(
                _places.value.copy(
                    mode = LocationMode.AUTO,
                    autoPlace = WeatherPlaces.rounded(place),
                    autoFixAt = System.currentTimeMillis() // lấy tay cũng tính là một lần lấy: chặn lần im lặng kế
                ),
                widgets
            )
        }
    }

    override suspend fun updateAutoFix(place: GeoPlace?, at: Long): Boolean {
        loaded.await()
        return mutex.withLock {
            val now = _places.value
            if (now.mode != LocationMode.AUTO) return@withLock false // người dùng vừa chuyển sang Thủ công trong lúc đang lấy vị trí: bỏ kết quả
            commit(
                now.copy(autoPlace = place?.let { WeatherPlaces.rounded(it) } ?: now.autoPlace, autoFixAt = at),
                widgets
            )
            true
        }
    }

    override suspend fun setManualPlace(place: GeoPlace) {
        loaded.await()
        mutex.withLock {
            commit(_places.value.copy(mode = LocationMode.MANUAL, manualPlace = WeatherPlaces.rounded(place)), widgets)
        }
    }

    override fun resolve(placeId: String): GeoPlace? = _places.value.resolve(placeId)

    override fun widgetPlaceId(itemId: Long): String {
        val id = widgets[itemId] ?: return WeatherPlaces.CURRENT
        // Mã trỏ vào thành phố đã bị xóa (không nên xảy ra vì remove() dọn) -> rơi về mặc định, không crash.
        return if (_places.value.saved.any { WeatherPlaces.placeIdOf(it) == id }) id else WeatherPlaces.CURRENT
    }

    override suspend fun setWidgetPlace(itemId: Long, placeId: String) {
        loaded.await()
        mutex.withLock {
            val current = widgets
            val next = LinkedHashMap(current)
            if (placeId == WeatherPlaces.CURRENT) {
                next.remove(itemId) ?: return
            } else {
                if (_places.value.saved.none { WeatherPlaces.placeIdOf(it) == placeId }) return
                if (next[itemId] == placeId) return
                next.remove(itemId) // đưa mục vừa đổi xuống cuối: "cũ nhất" là mục lâu chưa đụng tới
                next[itemId] = placeId
                while (next.size > MAX_WIDGET_ENTRIES) next.remove(next.keys.first())
            }
            commit(_places.value, next)
        }
    }

    override suspend fun forgetWidget(itemId: Long) {
        loaded.await()
        mutex.withLock {
            if (itemId !in widgets) return
            commit(_places.value, widgets - itemId)
        }
    }

    /** Gọi trong [mutex]: ghi file (IO) rồi đổi trạng thái trong RAM. */
    private suspend fun commit(next: WeatherPlaces, nextWidgets: Map<Long, String>) {
        withContext(io) {
            store.save(StoredPlaces(next.mode, next.autoPlace, next.manualPlace, next.saved, nextWidgets, next.autoFixAt))
        }
        _widgets.value = nextWidgets
        _places.value = next
    }

    private companion object {
        /** CHỈNH TAY: chặn trên cho số mục ánh xạ (widget thật nhiều nhất cỡ vài chục). */
        const val MAX_WIDGET_ENTRIES = 64
    }
}
