package gambi.global.os26launcher.domain.search

import gambi.global.os26launcher.domain.model.AppItem

object SpotlightLayoutBuilder {

    class BuildResult(
        val items: List<SearchResult>,
        val appCount: Int,
        val webCount: Int
    )

    fun buildEmptyState(
        recentApps: List<AppItem>,
        systemApps: List<AppItem>,
        maxApps: Int,
        trending: List<String> = emptyList(),
        maxTrending: Int = 0,
        columns: Int = 0,
        collapsed: Boolean = false
    ): BuildResult {
        val list = ArrayList<SearchResult>()
        val combined = ArrayList<AppItem>()
        combined.addAll(recentApps)
        for (sys in systemApps) {
            if (combined.size >= maxApps) break
            if (combined.none { it.key == sys.key }) {
                combined.add(sys)
            }
        }
        val taken = combined.take(if (collapsed && columns > 0) minOf(maxApps, columns) else maxApps)
        if (taken.isNotEmpty()) {
            // Nút thu gọn/hiện thêm chỉ khi có hơn 1 hàng để thu (hoặc đang thu gọn, để mở lại).
            val toggle = if (columns > 0 && (collapsed || taken.size > columns)) collapsed else null
            list.add(SearchResult.Header(SearchResult.Section.RECENT, toggle))
            taken.mapTo(list) { SearchResult.App(it) }
        }
        // Dưới "Gần đây": tìm kiếm thịnh hành (chỉ số hàng vừa màn hình, danh sách không cuộn).
        val hot = trending.take(maxTrending.coerceAtLeast(0))
        if (hot.isNotEmpty()) {
            list.add(SearchResult.Header(SearchResult.Section.TRENDING))
            hot.mapTo(list) { SearchResult.WebSuggestion(it, SearchResult.WebSuggestion.SuggestionKind.TRENDING) }
        }
        return BuildResult(list, taken.size, hot.size)
    }

    /**
     * SP (2026-11-26): Spotlight lúc đang gõ, danh sách cuộn được. Thứ tự: một hàng app ([columns] ô, không tiêu đề) → Phím tắt →
     * Danh bạ (kèm [contactsPrompt] cuối section, không tính vào giới hạn) → Cài đặt → Đề xuất tìm kiếm ([WEB_SUGGESTIONS_MAX] gợi ý
     * từ lịch sử rồi từ mạng, hàng "tìm đúng chữ đã gõ" đứng cuối). Mỗi section tối đa [SECTION_MAX] kết quả; section rỗng thì không
     * có cả tiêu đề. Đầu vào đã lọc sẵn (app ẩn, công tắc Cài đặt tìm kiếm, độ dài truy vấn tối thiểu) và đã xếp hạng.
     */
    fun buildTypingState(
        query: String,
        apps: List<SearchResult.App>,
        shortcuts: List<SearchResult.Shortcut>,
        contacts: List<SearchResult.Contact>,
        contactsPrompt: SearchResult.ContactsPermissionPrompt?,
        settings: List<SearchResult.Setting>,
        historyQueries: List<String>,
        remoteSuggestions: List<String>,
        columns: Int
    ): BuildResult {
        val list = ArrayList<SearchResult>()
        val trimmed = query.trim()

        val allowedApps = apps.take(columns.coerceAtLeast(1))
        list.addAll(allowedApps)

        fun section(section: SearchResult.Section, rows: List<SearchResult>, extra: SearchResult? = null) {
            val taken = rows.take(SECTION_MAX)
            if (taken.isEmpty() && extra == null) return
            list.add(SearchResult.Header(section))
            list.addAll(taken)
            if (extra != null) list.add(extra)
        }
        section(SearchResult.Section.SHORTCUTS, shortcuts)
        section(SearchResult.Section.CONTACTS, contacts, contactsPrompt)
        section(SearchResult.Section.SETTINGS, settings)

        val webStart = list.size
        val webList = ArrayList<String>()
        if (trimmed.isNotEmpty()) {
            list.add(SearchResult.Header(SearchResult.Section.WEB))
            val typedLower = trimmed.lowercase()
            val maxSuggestions = WEB_SUGGESTIONS_MAX
            for (h in historyQueries) {
                if (webList.size >= maxSuggestions) break
                val hl = h.lowercase()
                if (hl.startsWith(typedLower) && hl != typedLower && h !in webList) webList.add(h)
            }
            for (r in remoteSuggestions) {
                if (webList.size >= maxSuggestions) break
                if (r.lowercase() != typedLower && r !in webList) webList.add(r)
            }
            for (w in webList) {
                val kind = if (w in historyQueries) {
                    SearchResult.WebSuggestion.SuggestionKind.HISTORY
                } else {
                    SearchResult.WebSuggestion.SuggestionKind.REMOTE
                }
                list.add(SearchResult.WebSuggestion(w, kind))
            }
            list.add(SearchResult.WebSuggestion(trimmed, SearchResult.WebSuggestion.SuggestionKind.TYPED))
        }

        return BuildResult(list, allowedApps.size, (list.size - webStart - 1).coerceAtLeast(0))
    }

    /** SP: tối đa chừng này kết quả mỗi section (Phím tắt, Danh bạ, Cài đặt) lúc đang gõ. */
    const val SECTION_MAX = 3

    /** SP: số gợi ý web (lịch sử + mạng) trước hàng chữ đã gõ. */
    const val WEB_SUGGESTIONS_MAX = 3
}
