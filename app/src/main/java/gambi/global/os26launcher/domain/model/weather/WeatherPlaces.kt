package gambi.global.os26launcher.domain.model.weather

import kotlin.math.roundToLong

/** WX — cách xác định "Vị trí hiện tại" (màn 1.5). Chữ hiển thị ở `strings.xml`, enum chỉ giữ giá trị (lưu bằng tên: thêm giá trị sau không cần di trú). */
enum class LocationMode {
    /** Vị trí gần đúng lấy một lần (như G3e), cần quyền. */
    AUTO,

    /** Thành phố người dùng chọn, không cần quyền. */
    MANUAL;

    companion object {
        val DEFAULT = AUTO

        /** Tên lạ hoặc null -> [DEFAULT]. */
        fun fromName(name: String?): LocationMode = entries.find { it.name == name } ?: DEFAULT
    }
}

/**
 * WX (D3) — danh sách địa điểm người dùng theo dõi. Luôn có một mục "Vị trí hiện tại" ([CURRENT], mục đầu, không xóa được) mà nguồn do
 * [mode] quyết định: [LocationMode.AUTO] -> [autoPlace], [LocationMode.MANUAL] -> [manualPlace]. Giữ riêng hai giá trị để đổi chế độ không mất cái kia.
 * [saved] là các thành phố thêm tay, tối đa [MAX_SAVED]; tổng kể cả mục hiện tại tối đa [MAX_PLACES] (chủ dự án chốt 2026-11-13).
 *
 * Tọa độ ở mọi [GeoPlace] đã làm tròn 2 số thập phân (dữ liệu cá nhân, như G3e).
 */
data class WeatherPlaces(
    val mode: LocationMode = LocationMode.DEFAULT,
    val autoPlace: GeoPlace? = null,
    val manualPlace: GeoPlace? = null,
    val saved: List<GeoPlace> = emptyList(),
    /** WX Task 6: giờ treo tường (ms) của lần lấy vị trí tự động gần nhất thành công (bấm tay hoặc im lặng); 0 = chưa có. Chỉ dùng để chặn tần suất lấy vị trí. */
    val autoFixAt: Long = 0L
) {
    /** Địa điểm của mục "Vị trí hiện tại"; null = chưa xác định (chưa lấy được vị trí / chưa chọn thành phố). */
    val current: GeoPlace? get() = if (mode == LocationMode.AUTO) autoPlace else manualPlace

    /** Số mục đang chiếm chỗ: mục "Vị trí hiện tại" luôn tính một, kể cả khi chưa xác định. */
    val count: Int get() = 1 + saved.size

    val isFull: Boolean get() = count >= MAX_PLACES

    /** Địa điểm của [placeId] ([CURRENT] hoặc mã thành phố đã thêm); null nếu không có / chưa xác định. */
    fun resolve(placeId: String): GeoPlace? =
        if (placeId == CURRENT) current else saved.firstOrNull { placeIdOf(it) == placeId }

    companion object {
        /** Tổng tối đa, kể cả "Vị trí hiện tại". CHỈNH TAY (chủ dự án chốt 2026-11-13). */
        const val MAX_PLACES = 3
        const val MAX_SAVED = MAX_PLACES - 1

        /** Mã cố định của mục "Vị trí hiện tại". */
        const val CURRENT = "current"

        /** Mã của một thành phố đã thêm: tọa độ theo phần trăm độ (số nguyên), ổn định và không phụ thuộc locale; hai nơi cùng tọa độ cho cùng mã (chặn trùng). */
        fun placeIdOf(place: GeoPlace): String =
            "${(place.lat * 100.0).roundToLong()}_${(place.lon * 100.0).roundToLong()}"

        /** Làm tròn 2 số thập phân (~1 km) như `OwmParser.roundCoord`; domain không phụ thuộc `data`. */
        fun rounded(place: GeoPlace): GeoPlace = place.copy(
            lat = (place.lat * 100.0).roundToLong() / 100.0,
            lon = (place.lon * 100.0).roundToLong() / 100.0
        )
    }
}

/** Kết quả [gambi.global.os26launcher.domain.weather.WeatherPlacesRepository.add]. */
enum class AddPlaceResult { ADDED, DUPLICATE, FULL }
