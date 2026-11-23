package gambi.global.os26launcher.domain.model

/**
 * G3a — một người liên hệ trong chỉ mục tìm kiếm. Chỉ sống trong RAM khi lớp tìm kiếm đang mở; KHÔNG bao giờ ghi ra
 * DataStore/Room/file/log. [lookupKey] bền hơn [contactId] khi danh bạ gộp/tách nên dùng cho `Contacts.getLookupUri`.
 */
data class ContactEntry(
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
    /** CP: số gốc như provider trả, số mặc định đứng đầu, tối đa 10. Chỉ RAM, không bao giờ log. */
    val phones: List<String> = emptyList()
) {
    /** CP: không để tên/số lọt vào log qua toString của data class. */
    override fun toString(): String = "ContactEntry(id=$contactId)"
}
