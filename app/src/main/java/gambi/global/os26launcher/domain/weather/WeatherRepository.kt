package gambi.global.os26launcher.domain.weather

import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.WeatherSnapshot
import gambi.global.os26launcher.domain.model.weather.WeatherState
import kotlinx.coroutines.flow.StateFlow

/**
 * G3e/WX — nơi duy nhất giữ trạng thái thời tiết CỦA TỪNG ĐỊA ĐIỂM, dùng chung cho widget và bảng chọn. Địa điểm là mã của
 * [WeatherPlacesRepository] ([gambi.global.os26launcher.domain.model.weather.WeatherPlaces.CURRENT] hoặc mã thành phố đã thêm).
 *
 * Hợp đồng chặn tần suất (chủ dự án chốt 2026-11-13), mọi người gọi đều tuân theo, không ai tự đếm giờ. Hằng ở
 * [gambi.global.os26launcher.domain.model.weather.WeatherPolicy]:
 * - [refreshIfStale]: gọi mạng cho [placeId] nếu chưa có dữ liệu hợp lệ của nơi đó (lần tải đầu: ngay), hoặc quá 2 giờ kể từ lần thử gần nhất
 *   (thành công hoặc 429), hoặc quá 15 phút sau lỗi mạng/máy chủ/dữ liệu; sau 401 thì KHÔNG thử lại tới khi khởi động lại tiến trình. Giờ lùi
 *   (lần thử ở tương lai) coi là đã hết hạn. Không gọi mạng thì không đổi [states]. Một lúc chỉ một lượt làm mới cho cả ứng dụng.
 * - **Chỉ widget đang hiện hoặc thông báo thời tiết (WN, [snapshotForNotification]) mới kích hoạt làm mới** (mỗi widget gọi [refreshIfStale] cho địa điểm
 *   của nó ở mỗi nhịp phút): thêm/xóa/chọn địa điểm không gọi mạng; địa điểm không có widget hiện và không có thông báo đến hạn thì 0 lệnh.
 * - Không có làm mới thủ công (hệ quả của "tối đa 2 giờ một lần").
 * - Một mục bộ nhớ đệm chỉ hợp lệ khi tọa độ của nó trùng địa điểm đang tra (đổi vị trí tự động hay thành phố thủ công thì dữ liệu cũ bị bỏ).
 */
interface WeatherRepository {
    /** Trạng thái hiển thị của mọi địa điểm trong danh sách (mã địa điểm -> trạng thái). */
    val states: StateFlow<Map<String, WeatherState>>

    /** Build này có khóa API không. */
    val hasKey: Boolean

    /**
     * WN: dành cho thông báo thời tiết (chạy lúc 07:00/21:00, thường không có widget nào hiện). Chờ nạp xong, rồi NẾU đến hạn theo đúng quy tắc
     * của [refreshIfStale] (2 giờ / 15 phút / không sau 401) thì tải MỘT lần, và trả snapshot hợp lệ của [placeId] (vừa tải, hoặc cache khi chưa
     * đến hạn hay khi tải lỗi: người gọi tự kiểm tuổi/độ phủ); null nếu không có khóa, chưa xác định địa điểm hoặc chưa có dữ liệu.
     * KHÔNG BAO GIỜ lấy vị trí (không gọi cập nhật vị trí im lặng): chủ dự án chốt "không vị trí nền".
     *
     * Người gọi CHỜ tối đa [timeoutMs] (việc tải chạy ở scope ứng dụng và tiếp tục sau hạn chót); hết hạn thì trả cache hợp lệ (nếu có). Ngoại lệ
     * một lần của quy tắc 2 giờ: cache thành công nhưng chưa có đủ mốc dự báo (cache của bản trước WN) thì tải một lần.
     */
    suspend fun snapshotForNotification(placeId: String, timeoutMs: Long): WeatherSnapshot?

    /** Không chặn: tự chạy trên scope ứng dụng. Gọi rẻ (so một số nguyên) nên được gọi mỗi nhịp phút. */
    fun refreshIfStale(placeId: String)

    /** Chỉ ném [gambi.global.os26launcher.domain.model.weather.WeatherException] (hoặc hủy coroutine); không loại ngoại lệ nào khác (B1). */
    suspend fun search(query: String): List<GeoPlace>

    suspend fun reverse(lat: Double, lon: Double): GeoPlace?
}
