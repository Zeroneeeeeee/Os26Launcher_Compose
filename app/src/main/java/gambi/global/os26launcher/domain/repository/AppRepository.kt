package gambi.global.os26launcher.domain.repository

import android.graphics.Bitmap
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.IconStyleKey
import gambi.global.os26launcher.domain.model.PackageEvent
import gambi.global.os26launcher.domain.model.ScreenRect
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    /** App mở được của mọi user profile, đã sắp theo nhãn theo locale hiện tại. */
    val apps: Flow<List<AppItem>>

    /**
     * Sự kiện package CÓ KIỂU (M4). Khác [apps]: ở đây biết được GỠ THẬT hay chỉ TẠM KHÔNG KHẢ DỤNG.
     * Flow lạnh: mỗi lần thu đăng ký một callback với hệ thống và hủy khi ngừng thu.
     */
    val packageEvents: Flow<PackageEvent>

    /**
     * Package này còn được CÀI cho user đó hay không, kể cả khi đang bị tắt (`pm disable-user`), tạm khóa, hoặc
     * nằm trên thẻ nhớ đã tháo. Dùng để phân biệt ĐÃ GỠ với TẠM KHÔNG KHẢ DỤNG khi bắt kịp lúc mở app (M4 Task 4).
     * Chặn (gọi hệ thống), chỉ gọi từ luồng nền. Không chắc chắn thì trả true: giữ hàng an toàn hơn xóa oan.
     */
    fun isPackageInstalled(packageName: String, userSerial: Long): Boolean

    /**
     * Icon đã vẽ sẵn thành bitmap vuông [sizePx]. null nếu không lấy được.
     * Bitmap do cache sở hữu — người gọi KHÔNG được recycle.
     * (Bitmap là android.graphics, ngoại lệ có chủ ý của luật phụ thuộc: không có kiểu trung lập thay được.)
     */
    suspend fun icon(key: ComponentKey, sizePx: Int): Bitmap?

    /** P3-3: icon vẽ theo kiểu [style] chỉ định, không qua cache (khung xem trước Bộ biểu tượng). */
    suspend fun iconWithStyle(key: ComponentKey, sizePx: Int, style: IconStyleKey): Bitmap?

    /** P3-4: icon mẫu "hình của app trên nền [color]", ô vuông CHƯA cắt hình (để lưu làm icon riêng). */
    suspend fun iconOnColor(key: ComponentKey, sizePx: Int, color: Int): Bitmap?

    /** P3-4: cắt [square] theo hình icon (TL2), như icon hiển thị ngoài Home. */
    suspend fun shapeIcon(square: Bitmap, sizePx: Int): Bitmap

    /** P3-4: icon gốc của hệ thống theo kiểu đang chọn, BỎ QUA icon riêng (ô "icon cũ" của sheet đổi icon). */
    suspend fun systemIcon(key: ComponentKey, sizePx: Int): Bitmap?

    /**
     * Mở app. false nếu app không còn tồn tại hoặc hệ thống từ chối.
     * [source] là vùng của icon, để hệ thống dựng animation mở app từ đúng chỗ.
     */
    fun launch(key: ComponentKey, source: ScreenRect?): Boolean

    /** M7 Task 10 — mở màn "Thông tin ứng dụng" của hệ thống cho [key] (đúng user profile). false nếu thất bại. */
    fun openAppInfo(key: ComponentKey): Boolean

    /** M7 Task 10 — mở hộp thoại gỡ cài đặt của hệ thống cho package của [key]. false nếu không mở được. */
    fun requestUninstall(key: ComponentKey): Boolean

    /** G2c Task 11 — mở app Đồng hồ của máy (màn báo thức). false nếu máy không có app nào nhận. */
    fun openClock(): Boolean

    /** G2c Task 11 — mở app Lịch của máy. false nếu máy không có app nào nhận. */
    fun openCalendar(): Boolean
}
