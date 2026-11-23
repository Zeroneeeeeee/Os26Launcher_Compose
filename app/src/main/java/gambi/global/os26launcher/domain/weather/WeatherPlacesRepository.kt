package gambi.global.os26launcher.domain.weather

import gambi.global.os26launcher.domain.model.weather.AddPlaceResult
import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.LocationMode
import gambi.global.os26launcher.domain.model.weather.WeatherPlaces
import kotlinx.coroutines.flow.StateFlow

/**
 * WX — danh sách địa điểm theo dõi + địa điểm mà từng widget thời tiết hiển thị (D1: lưu ngoài Room, khóa theo `itemId` hàng
 * `launcher_items`; id không bao giờ dùng lại nhờ `AUTOINCREMENT`). Không gọi mạng. Đây cũng là nguồn mà nhánh thông báo thời tiết
 * sau này đọc (cùng danh sách, cùng mã địa điểm).
 *
 * Mọi hàm ghi là suspend, trả về sau khi đã ghi file; không ném (lỗi ghi chỉ log loại ngoại lệ, trạng thái trong RAM vẫn đổi).
 */
interface WeatherPlacesRepository {
    /** Giá trị mặc định ([WeatherPlaces] rỗng) cho tới khi đọc xong file; dùng [awaitLoaded] nếu cần phân biệt. */
    val places: StateFlow<WeatherPlaces>

    /** Trả về khi đã đọc xong file (kể cả khi file không có / hỏng). */
    suspend fun awaitLoaded()

    /** Địa điểm của từng widget (`itemId` -> mã địa điểm); chỉ chứa widget KHÔNG dùng "Vị trí hiện tại". Phát lại khi một widget đổi/được dọn địa điểm (`places` không phát vì không đổi). */
    val widgetPlaces: StateFlow<Map<Long, String>>

    /** Thêm một thành phố. Đủ [WeatherPlaces.MAX_PLACES] -> [AddPlaceResult.FULL]; trùng tọa độ với một mục đã có (kể cả "Vị trí hiện tại") -> [AddPlaceResult.DUPLICATE]. */
    suspend fun add(place: GeoPlace): AddPlaceResult

    /** Xóa một thành phố đã thêm theo mã ([WeatherPlaces.placeIdOf]); [WeatherPlaces.CURRENT] hoặc mã lạ thì bỏ qua. Widget đang dùng mã đó rơi về "Vị trí hiện tại". */
    suspend fun remove(placeId: String)

    suspend fun setMode(mode: LocationMode)

    /** Đặt vị trí tự động VÀ chuyển sang [LocationMode.AUTO] (một lần ghi, một lần phát). */
    suspend fun setAutoPlace(place: GeoPlace)

    /**
     * WX Task 6 (cập nhật vị trí im lặng): ghi lần lấy vị trí tự động ở giờ [at]; [place] != null thì đồng thời đổi vị trí tự động. KHÔNG đổi chế độ:
     * nếu chế độ không còn là Tự động (người dùng vừa chuyển sang Thủ công) thì bỏ kết quả và trả false.
     */
    suspend fun updateAutoFix(place: GeoPlace?, at: Long): Boolean

    /** Đặt thành phố thủ công VÀ chuyển sang [LocationMode.MANUAL] (một lần ghi, một lần phát). */
    suspend fun setManualPlace(place: GeoPlace)

    /** Địa điểm của [placeId]; null nếu không có hoặc "Vị trí hiện tại" chưa xác định. */
    fun resolve(placeId: String): GeoPlace?

    /** Mã địa điểm widget [itemId] đang hiển thị: [WeatherPlaces.CURRENT] nếu chưa chọn hoặc địa điểm đã bị xóa. Đọc rẻ, gọi được từ luồng chính mỗi lần vẽ lại. */
    fun widgetPlaceId(itemId: Long): String

    /** Gán địa điểm cho widget; [placeId] không có trong danh sách thì bỏ qua; [WeatherPlaces.CURRENT] xóa mục ánh xạ (mặc định). */
    suspend fun setWidgetPlace(itemId: Long, placeId: String)

    /** Widget [itemId] đã bị xóa: dọn mục ánh xạ. */
    suspend fun forgetWidget(itemId: Long)
}
