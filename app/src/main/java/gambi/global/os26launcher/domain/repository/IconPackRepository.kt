package gambi.global.os26launcher.domain.repository

import gambi.global.os26launcher.domain.model.IconPackInfo

/** IP — tìm gói biểu tượng đã cài. Hàm chậm (IPC + đọc XML): gọi từ luồng nền. */
interface IconPackRepository {
    /** Các gói đã cài có appfilter.xml, sắp theo tên. */
    suspend fun installedPacks(): List<IconPackInfo>

    /** Tên hiển thị của một gói, null nếu không còn cài. */
    fun labelOf(packageName: String): String?

    /** Khóa `package@versionCode` dùng trong khóa cache, null nếu gói không còn cài. */
    fun keyOf(packageName: String): String?
}
