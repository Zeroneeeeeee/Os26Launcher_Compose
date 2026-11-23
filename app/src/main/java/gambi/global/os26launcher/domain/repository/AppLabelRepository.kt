package gambi.global.os26launcher.domain.repository

import gambi.global.os26launcher.domain.model.ComponentKey
import kotlinx.coroutines.flow.Flow

/**
 * RN — tên riêng người dùng đặt cho từng app, theo [ComponentKey] (package + class + user serial). Chỉ giữ TÊN RIÊNG; tên gốc luôn do hệ
 * thống quyết định (và `LabelCache` giữ). Lớp phủ lên `AppRepository.apps` nằm ở `AppRepositoryImpl`.
 */
interface AppLabelRepository {
    /**
     * Mọi tên riêng hiện có. Phát ngay giá trị đầu (rỗng nếu chưa có hoặc file hỏng/không đọc được: lỗi đọc không bao giờ làm flow chết),
     * rồi chỉ phát khi bản đồ thực sự đổi.
     */
    val customLabels: Flow<Map<ComponentKey, String>>

    /** Ghi tên riêng cho [key]; [label] null = khôi phục (xóa mục). Ghi nguyên tử; lỗi ghi được ném lên cho người gọi báo người dùng. */
    suspend fun setCustomLabel(key: ComponentKey, label: String?)

    /** Xóa mọi tên riêng của [packageName] cho user [userSerial] (app đã gỡ thật). */
    suspend fun removePackage(packageName: String, userSerial: Long)

    /** Xóa mọi tên riêng của user [userSerial] (hồ sơ đã bị bỏ). Người gọi không được gọi cho user chính. */
    suspend fun removeUser(userSerial: Long)
}
