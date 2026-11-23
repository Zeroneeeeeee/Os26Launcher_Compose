package gambi.global.os26launcher.domain.photo

import gambi.global.os26launcher.domain.model.photo.CropRect
import gambi.global.os26launcher.domain.model.photo.PhotoImportResult
import gambi.global.os26launcher.domain.model.photo.PhotoWidgetConfig
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.InputStream

/** Nguồn byte của một ảnh (do `ui` bọc `Uri`); có thể mở nhiều lần (đọc EXIF, đọc cỡ, giải mã). `null` = không mở được. Domain không biết `Uri`. */
fun interface PhotoSource {
    fun open(): InputStream?
}

/**
 * G3b — ảnh và vùng cắt của từng widget Ảnh, lưu NGOÀI Room, khóa theo `itemId` hàng `launcher_items` (plan D1, cùng mẫu WX;
 * `AUTOINCREMENT` nên id không bao giờ dùng lại). Ảnh nằm trong `noBackupFilesDir` (không sao lưu). Không bao giờ log đường dẫn/URI.
 *
 * Mọi hàm ghi là suspend, chạy I/O ở luồng nền, không ném (lỗi chỉ trả kết quả thất bại).
 */
interface PhotoWidgetRepository {
    /** Cấu hình từng widget (`itemId` -> ảnh). Map bất biến thay nguyên khối; rỗng cho tới khi đọc xong file. Widget không có mục nào = chưa có ảnh. */
    val widgets: StateFlow<Map<Long, PhotoWidgetConfig>>

    /** Trả về khi đã đọc xong file (kể cả file không có / hỏng). */
    suspend fun awaitLoaded()

    /** Cấu hình của [itemId] (rỗng nếu chưa có). Đọc rẻ, gọi được từ luồng chính. */
    fun config(itemId: Long): PhotoWidgetConfig

    /**
     * File ảnh nguồn đã lưu (hoặc ảnh nháp nếu [staged]); null nếu không tồn tại / id không hợp lệ. Chỉ kiểm tồn tại, không đọc nội dung.
     * Dùng để giải mã; không bao giờ log kết quả.
     */
    fun photoFile(itemId: Long, photoId: String, staged: Boolean = false): File?

    /** Xóa mọi ảnh nháp của [itemId] (phiên cắt bỏ dở). Gọi khi Activity mở lúc không có phiên nào đang chạy. */
    suspend fun clearStaging(itemId: Long)

    /** Chép và thu nhỏ ảnh vào vùng nháp (chưa thuộc widget cho tới [commitCrop]). Phải gọi NGAY khi nhận URI (URI chỉ tạm). */
    suspend fun importPhoto(itemId: Long, source: PhotoSource): PhotoImportResult

    /**
     * Cam kết ảnh [photoId] (đang ở nháp hoặc đã thuộc widget) với vùng cắt [crop] cho khung tỉ lệ [aspect]. Ảnh nháp được chuyển vào widget; ảnh
     * đã có thì cập nhật vùng cắt và tăng `version`. false nếu [crop]/[aspect] không hợp lệ, ảnh không tồn tại, hoặc widget đã đủ ảnh.
     */
    suspend fun commitCrop(itemId: Long, photoId: String, crop: CropRect, aspect: Float): Boolean

    /** Bỏ ảnh nháp [photoId] (xóa file). */
    suspend fun discardStaged(itemId: Long, photoId: String)

    /** Xóa ảnh [photoId] khỏi widget (cả file). */
    suspend fun removePhoto(itemId: Long, photoId: String)

    /** Widget [itemId] đã bị xóa: xóa mục cấu hình và cả thư mục ảnh. Không làm gì nếu không có. */
    suspend fun forget(itemId: Long)

    /**
     * Quét mồ côi, chạy ngoài luồng chính: xóa thư mục ảnh và mục cấu hình của widget KHÔNG còn trong [existingItemIds] (mọi `itemId` đang có
     * trong bố cục, không chỉ widget ảnh). **Không bao giờ chạm** thư mục có `itemId` còn trong tập này; tên thư mục không phải số thì bỏ qua.
     * Người gọi chỉ được gọi sau khi bố cục đã nạp xong (tập rỗng giả sẽ xóa hết).
     */
    suspend fun sweep(existingItemIds: Set<Long>)
}
