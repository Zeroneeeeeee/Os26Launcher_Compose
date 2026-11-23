package gambi.global.os26launcher.domain.search

import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ContactEntry
import gambi.global.os26launcher.domain.model.SearchSettingEntry
import gambi.global.os26launcher.domain.model.ShortcutEntry

/**
 * M8d — loại kết quả tìm được, kèm luật riêng của từng loại: số ký tự truy vấn tối thiểu (sau chuẩn hóa) và số kết quả tối đa
 * mỗi lần tìm. [priority] nhỏ hơn thì đứng trước khi hai kết quả cùng hạng khớp. Thêm loại mới = thêm một dòng ở đây, một lớp
 * con của [SearchResult.Searchable], và các nhánh `when` của adapter/ViewModel.
 */
enum class SearchKind(val priority: Int, val minQueryLength: Int, val maxResults: Int) {
    APP(0, 1, Int.MAX_VALUE),
    CONTACT(1, 2, 5), // G3a: sau App, trước Cài đặt/Shortcut khi cùng hạng khớp
    SETTING(2, 2, 5),
    SHORTCUT(3, 2, 5)
}

/** M8d — một hàng của danh sách kết quả. [stableId] là khóa ổn định cho DiffUtil và để adapter biết hàng còn là hàng cũ. */
sealed interface SearchResult {
    val stableId: String

    /** Kết quả có thể tìm ra bằng truy vấn (vào [SearchIndex]). */
    sealed interface Searchable : SearchResult {
        val kind: SearchKind
    }

    data class App(val item: AppItem) : Searchable {
        override val stableId: String =
            "app:${item.key.packageName}/${item.key.className}/${item.key.userSerial}"
        override val kind: SearchKind get() = SearchKind.APP
    }

    data class Setting(val entry: SearchSettingEntry) : Searchable {
        override val stableId: String = "setting:${entry.id}"
        override val kind: SearchKind get() = SearchKind.SETTING
    }

    data class Shortcut(val entry: ShortcutEntry) : Searchable {
        override val stableId: String =
            "shortcut:${entry.key.packageName}/${entry.id}/${entry.key.userSerial}"
        override val kind: SearchKind get() = SearchKind.SHORTCUT
    }

    /**
     * G3a — một người liên hệ. [phone] = số được chọn (null: chưa tra hoặc người không có số -> ẩn nút gọi/nhắn). Chỉ mục giữ bản
     * không số; ViewModel gắn số sau khi tìm, cho tối đa [SearchKind.maxResults] người.
     */
    data class Contact(val entry: ContactEntry, val phone: String? = null) : Searchable {
        override val stableId: String = "contact:${entry.lookupKey}"
        override val kind: SearchKind get() = SearchKind.CONTACT
    }

    /** G3a — hàng mời "Hiện danh bạ" (chưa cấp quyền). [openSettings] = đã bị từ chối vĩnh viễn, bấm sẽ mở Cài đặt ứng dụng. Không nằm trong chỉ mục. */
    data class ContactsPermissionPrompt(val openSettings: Boolean) : SearchResult {
        override val stableId: String = "contacts_prompt"
    }

    /**
     * Tiêu đề nhóm ở màn ô trống (Gần đây / Gợi ý). Không nằm trong chỉ mục. [collapsed] != null: tiêu đề có nút "Thu gọn"
     * (false) / "Hiện thêm" (true) bên phải (Spotlight, "Gần đây"); null: không có nút.
     */
    data class Header(val section: Section, val collapsed: Boolean? = null) : SearchResult {
        override val stableId: String = "header:${section.name}"
    }

    /** Hàng tìm kiếm trên web (cục bộ hoặc online). */
    data class WebSuggestion(val query: String, val kind: SuggestionKind = SuggestionKind.TYPED) : SearchResult {
        override val stableId: String = "web:${kind.name}:$query"
        /** TRENDING: tìm kiếm thịnh hành ở ô trống Spotlight. */
        enum class SuggestionKind { TYPED, HISTORY, REMOTE, TRENDING }
    }

    enum class Section { RECENT, FREQUENT, TRENDING }
}
