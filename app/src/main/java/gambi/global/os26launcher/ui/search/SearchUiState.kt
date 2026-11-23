package gambi.global.os26launcher.ui.search

import gambi.global.os26launcher.domain.search.SearchMode
import gambi.global.os26launcher.domain.search.SearchResult

/** M8b — IDLE: truy vấn rỗng; LOADING: danh sách app chưa nạp; NO_RESULTS: có truy vấn mà không khớp. */
enum class SearchStatus { IDLE, LOADING, NO_RESULTS, RESULTS }

data class SearchUiState(
    val isOpen: Boolean = false,
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val status: SearchStatus = SearchStatus.LOADING,
    val mode: SearchMode = SearchMode.SPOTLIGHT,
    val columns: Int = 4,
    val appRows: Int = 4,
    val webRows: Int = 4,
    /** Ô trống Spotlight: người dùng đã thu gọn "Gần đây" còn 1 hàng. */
    val recentCollapsed: Boolean = false
)

sealed interface SearchEvent {
    /** Mở app thất bại (vd vừa bị gỡ); lớp tìm kiếm giữ mở. */
    data class LaunchFailed(val label: String) : SearchEvent

    /** G3a: người dùng bấm hàng "Hiện danh bạ". [askedBefore] = đã từng xin quyền; controller quyết xin lại hay mở Cài đặt ứng dụng. */
    data class RequestContactsPermission(val askedBefore: Boolean) : SearchEvent

    /** G3a: mở danh bạ / gọi / nhắn thất bại; lớp giữ mở. */
    data object ContactActionFailed : SearchEvent

    /** Mở tìm kiếm web. */
    data class LaunchWebSearch(val query: String) : SearchEvent
}
