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

    fun buildTypingState(
        query: String,
        matchedApps: List<SearchResult.App>,
        historyQueries: List<String>,
        remoteSuggestions: List<String>,
        maxAppRows: Int,
        maxWebRows: Int,
        columns: Int
    ): BuildResult {
        val list = ArrayList<SearchResult>()
        val trimmed = query.trim()

        // Kiểu iOS Spotlight: không tiêu đề nhóm. Một hàng app (thẻ kính), rồi thẻ gợi ý web; hàng "tìm đúng chữ đã gõ" đứng cuối.
        val allowedApps = matchedApps.take(maxAppRows * columns)
        list.addAll(allowedApps)

        val webList = ArrayList<String>()
        if (trimmed.isNotEmpty() && maxWebRows > 0) {
            val typedLower = trimmed.lowercase()
            val maxSuggestions = maxWebRows - 1 // chừa 1 chỗ cho hàng chữ đã gõ
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

        return BuildResult(list, allowedApps.size, list.size - allowedApps.size)
    }
}
