package gambi.global.os26launcher.domain.model

/** M8d — một mục Cài đặt hệ thống tìm được. [keywords] là chữ gốc (có thể có dấu); chỉ mục tự chuẩn hóa. */
data class SearchSettingEntry(
    val id: String,
    val label: String,
    val keywords: List<String>
)
