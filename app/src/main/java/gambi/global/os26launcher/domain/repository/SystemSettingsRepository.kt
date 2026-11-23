package gambi.global.os26launcher.domain.repository

import gambi.global.os26launcher.domain.model.SearchSettingEntry

interface SystemSettingsRepository {
    /**
     * Các mục Cài đặt hệ thống mà MÁY NÀY mở được (có trình xử lý do hệ thống cài sẵn); mục máy không hỗ trợ bị loại.
     * Chặn (gọi PackageManager), chạy ở luồng nền; kết quả nhớ theo ngôn ngữ hiện tại.
     */
    suspend fun entries(): List<SearchSettingEntry>

    /** Mở mục [id] bằng đúng component hệ thống đã chọn lúc dựng danh mục. false nếu thất bại hoặc id không có. */
    fun open(id: String): Boolean
}
