package gambi.global.os26launcher.domain.repository

import android.graphics.Bitmap
import gambi.global.os26launcher.domain.model.ContactEntry
import kotlinx.coroutines.flow.Flow

/** G3a — nơi DUY NHẤT chạm `ContactsContract`. Chỉ đọc; không lưu gì; mọi lỗi quyền coi như "chưa cấp quyền". */
interface ContactsRepository {
    fun hasPermission(): Boolean

    /** Tên của tối đa [limit] người (chỉ người trong nhóm hiển thị), sắp theo khóa sắp xếp của Contacts. Rỗng nếu chưa có quyền. Chạy nền. */
    suspend fun loadNames(limit: Int): List<ContactEntry>

    /** CP pha 2: mọi số của các người trong [ids] (một truy vấn). Số mặc định (super primary) đứng đầu, rồi số chính, rồi theo _ID; tối đa 10 số mỗi người, bỏ số trùng. Người không có số vắng mặt. Chạy nền, hủy được. */
    suspend fun loadPhones(ids: Set<Long>): Map<Long, List<String>>

    /** Phát mỗi khi danh bạ đổi; chỉ đăng ký khi có collector. */
    fun changes(): Flow<Unit>

    /** Ảnh đại diện tròn (thumbnail, hoặc vòng tròn xám với chữ cái đầu khi không có ảnh). Chạy nền. */
    suspend fun avatar(entry: ContactEntry, sizePx: Int): Bitmap

    fun openContact(entry: ContactEntry): Boolean
    fun dial(number: String): Boolean
    fun message(number: String): Boolean

    /** Máy có điện thoại (gọi/nhắn)? false thì ẩn hai nút. */
    fun canCallAndMessage(): Boolean
}
