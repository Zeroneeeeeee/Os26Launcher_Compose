package gambi.global.os26launcher.ui.search

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.core.coroutines.DefaultDispatcher
import gambi.global.os26launcher.data.search.SystemAppFiller
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.ContactEntry
import gambi.global.os26launcher.domain.model.RecentApp
import gambi.global.os26launcher.domain.model.ScreenRect
import gambi.global.os26launcher.domain.config.SearchConfig
import gambi.global.os26launcher.domain.model.SearchSettingEntry
import gambi.global.os26launcher.domain.model.ShortcutEntry
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.repository.ContactsRepository
import gambi.global.os26launcher.domain.repository.RecentAppsRepository
import gambi.global.os26launcher.domain.config.SearchConfigRepository
import gambi.global.os26launcher.domain.repository.SearchQueryHistoryRepository
import gambi.global.os26launcher.domain.repository.ShortcutRepository
import gambi.global.os26launcher.domain.repository.SystemSettingsRepository
import gambi.global.os26launcher.domain.repository.TrendingSearchRepository
import gambi.global.os26launcher.domain.repository.WebSuggestRepository
import gambi.global.os26launcher.domain.search.AppSearchMatcher
import gambi.global.os26launcher.domain.search.SearchIndex
import gambi.global.os26launcher.domain.search.SearchKind
import gambi.global.os26launcher.domain.search.SearchMode
import gambi.global.os26launcher.domain.search.SearchResult
import gambi.global.os26launcher.domain.search.SpotlightLayoutBuilder
import gambi.global.os26launcher.domain.usecase.GetHiddenAppsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * M8b — trạng thái lớp tìm kiếm của Home. `isOpen` + `query` nằm ở đây để sống qua recreate() (đổi giao diện).
 * Nguồn = app đã cài trừ app ẩn (cùng tập với tab "Chưa ẩn" của Thư viện).
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val appRepository: AppRepository,
    getHiddenApps: GetHiddenAppsUseCase,
    private val systemSettings: SystemSettingsRepository,
    private val recentApps: RecentAppsRepository,
    private val shortcutRepository: ShortcutRepository,
    private val searchPreferences: SearchConfigRepository,
    private val contactsRepository: ContactsRepository,
    private val searchQueryHistory: SearchQueryHistoryRepository,
    private val webSuggestRepository: WebSuggestRepository,
    private val trendingSearchRepository: TrendingSearchRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val isOpen = MutableStateFlow(false)
    private val query = MutableStateFlow("")
    private val searchMode = MutableStateFlow(SearchMode.SPOTLIGHT)
    private val capacityAppRows = MutableStateFlow(4)
    private val capacityWebRows = MutableStateFlow(4)
    private val capacityColumns = MutableStateFlow(4)
    private val queryHistory = MutableStateFlow<List<String>>(emptyList())
    private val remoteSuggestions = MutableStateFlow<List<String>>(emptyList())
    private val trendingSearches = MutableStateFlow<List<String>>(emptyList()) // ô trống Spotlight, dưới "Gần đây"

    // G2a B2: mở từ ô tìm kiếm của trang App Library -> truy vấn rỗng liệt kê TOÀN BỘ app (không ẩn) theo a-z thay vì dòng gợi ý.
    private val showAllWhenEmpty = MutableStateFlow(false)

    // Ô trống Spotlight: "Gần đây" thu gọn còn 1 hàng (nút trên tiêu đề). Giữ trong lúc ViewModel còn sống, không lưu xuống đĩa.
    private val recentCollapsed = MutableStateFlow(false)

    // AppRepository.apps luôn phát danh sách rỗng trước giá trị thật (xem AppLibraryViewModel) -> bỏ rỗng.
    // null = "chưa nạp" (giá trị đầu của stateIn) để suy ra LOADING. C1: KHÔNG phát null mỗi lần flow chạy lại (sau
    // WhileSubscribed 5 giây): StateFlow giữ giá trị cuối và lọc trùng, nên quay về không còn LOADING và không dựng lại.
    private val visibleApps: StateFlow<List<AppItem>?> = combine(
        appRepository.apps.filter { it.isNotEmpty() },
        getHiddenApps()
    ) { all, hidden ->
        val hiddenKeys = hidden.mapTo(HashSet()) { it.key }
        all.filter { it.key !in hiddenKeys }
    }.flowOn(defaultDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Nhóm thứ hai của combine trong [computed] (Triple cũ + cờ thu gọn "Gần đây"). */
    private class Extra(
        val first: List<RecentApp>,
        val second: SearchResult.ContactsPermissionPrompt?,
        val third: Boolean,
        val collapsed: Boolean,
        /** P3-5: "App gần đây" bật. Tắt thì ô trống không có mục Gần đây (kể cả app hệ thống lấp chỗ). */
        val recentOn: Boolean
    )

    private class Inputs(
        val apps: List<AppItem>?,
        val index: IndexBundle?,
        val query: String,
        val showAll: Boolean,
        val recents: List<RecentApp>,
        val prompt: SearchResult.ContactsPermissionPrompt?,
        val mode: SearchMode,
        val appRows: Int,
        val webRows: Int,
        val columns: Int,
        val history: List<String>,
        val remote: List<String>,
        val trending: List<String>,
        val recentCollapsed: Boolean,
        val recentOn: Boolean
    )

    // M8d: chỉ mục dựng MỘT LẦN khi danh sách app đổi (không mỗi phím), ở luồng nền.
    // Mục Cài đặt hệ thống: dựng một lần (lazy) ở IO bởi repository, rồi ghép vào chỉ mục cùng app.
    private val prefs: Flow<SearchConfig> =
        searchPreferences.config.catch { emit(SearchConfig()) }.distinctUntilChanged()

    // C9: danh mục Cài đặt chỉ dựng từ lần open() đầu tiên (không lúc khởi động lạnh); giữ trong StateFlow.
    private val settingEntries = MutableStateFlow<List<SearchSettingEntry>>(emptyList())

    // C9: false cho tới lần open() đầu tiên; chỉ mục (nguồn app) không chuẩn hóa gì trước đó.
    private val indexActive = MutableStateFlow(false)

    /** Gọi ở mỗi open(): lần đầu dựng danh mục (IO), các lần sau lấy từ cache theo ngôn ngữ của repository (StateFlow lọc trùng). */
    private fun ensureSettingsCatalog() {
        viewModelScope.launch {
            settingEntries.value = try {
                systemSettings.entries()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "system settings unavailable", e)
                emptyList()
            }
        }
    }

    init {
        viewModelScope.launch {
            query.collectLatest { q ->
                if (q.trim().isEmpty() || searchMode.value != SearchMode.SPOTLIGHT) {
                    remoteSuggestions.value = emptyList()
                    return@collectLatest
                }
                delay(250)
                // P3-5: "Gợi ý tìm kiếm" chỉ ẩn tìm kiếm thịnh hành; gợi ý web khi gõ không phụ thuộc công tắc này nữa.
                webSuggestRepository.suggest(q).collect { list ->
                    remoteSuggestions.value = list
                }
            }
        }
    }

    // M8d Task 4: shortcut đọc lúc mở lớp (cache 60 giây), chỉ khi launcher là mặc định; nếu không thì rỗng, im lặng.
    private val shortcutEntries = MutableStateFlow<List<ShortcutEntry>>(emptyList())
    private var shortcutJob: Job? = null
    private var lastShortcutBuildMs = 0L

    /** Chỉ mục + shortcut gom theo app (cho "top hit": hiện shortcut của app đứng đầu). */
    private class IndexBundle(
        val index: SearchIndex,
        val shortcutsByApp: Map<ComponentKey, List<SearchResult.Shortcut>>
    )

    // G3a: danh bạ chỉ ở RAM, chỉ khi lớp mở và đã cấp quyền (contactEntries rỗng nếu không); tắt trong Cài đặt thì không vào chỉ mục.
    private val contactEntries = MutableStateFlow<List<ContactEntry>>(emptyList())
    private val contactsGranted = MutableStateFlow(contactsRepository.hasPermission())
    private val contactsPermanentlyDenied = MutableStateFlow(false)
    private var contactsLoadJob: Job? = null
    private var contactsWatchJob: Job? = null

    init {
        // G3a: bật/tắt "Tìm trong danh bạ" (nút x, công tắc Cài đặt) -> nạp hoặc xóa tên khỏi RAM ngay, không đợi lần mở sau.
        viewModelScope.launch {
            prefs.map { it.contacts }.distinctUntilChanged().drop(1).collect { refreshContacts() }
        }
    }

    // B11: mỗi nguồn giữ một Source đã chuẩn hóa sẵn, chỉ nguồn nào đổi mới dựng lại. stateIn(WhileSubscribed) giữ giá trị cuối
    // qua các lần uiState khởi động lại (C1), nên quay về sau 5 giây không chuẩn hóa lại; StateFlow lọc trùng theo equals
    // nên danh sách giống hệt (vd nạp lại danh bạ không đổi) cũng không dựng lại. Thứ tự nguồn khi ghép: app, Cài đặt, danh bạ, shortcut.
    // appsSource null = danh sách app chưa nạp -> chỉ mục null -> LOADING (như trước).
    private val appsSource: StateFlow<SearchIndex.Source?> = flow {
        indexActive.first { it } // C9: chưa open() lần nào thì chưa chuẩn hóa gì
        emitAll(visibleApps.filterNotNull().map { apps ->
            // RN: app đã đổi tên khớp cả tên riêng (nhãn, hạng 0-2, xếp trước) lẫn tên gốc (từ khóa, hạng 3-4). Chỉ thêm khi khác nhau.
            buildSource("apps", apps.map {
                SearchIndex.Candidate(
                    SearchResult.App(it),
                    it.label,
                    if (it.originalLabel != it.label) listOf(it.originalLabel) else emptyList()
                )
            })
        })
    }.flowOn(defaultDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val settingsSource: StateFlow<SearchIndex.Source> = settingEntries.map { entries ->
        buildSource("settings", entries.map { SearchIndex.Candidate(SearchResult.Setting(it), it.label, it.keywords) })
    }.flowOn(defaultDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchIndex.Source.EMPTY)

    // G3a: chỉ khớp tên (đã bỏ dấu cùng hàm với app), không khớp số điện thoại. Rỗng (đóng lớp, mất quyền, ON_STOP) -> Source.EMPTY,
    // nên bản sao đã chuẩn hóa của tên danh bạ chết cùng contactEntries.
    private val contactsSource: StateFlow<SearchIndex.Source> = contactEntries.map { contacts ->
        buildSource("contacts", contacts.map { SearchIndex.Candidate(SearchResult.Contact(it, it.phones.firstOrNull()), it.name, phones = it.phones) })
    }.flowOn(defaultDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchIndex.Source.EMPTY)

    private class ShortcutsBundle(
        val source: SearchIndex.Source,
        val byApp: Map<ComponentKey, List<SearchResult.Shortcut>>
    )

    // Shortcut lọc theo app đang hiển thị nên nguồn này phụ thuộc cả hai (nhỏ, dựng lại khi app đổi chấp nhận được).
    // Shortcut chỉ khớp nhãn của chính nó (không khớp nhãn app cha, kẻo "cam" đổ cả shortcut của Camera ra).
    private val shortcutsSource: StateFlow<ShortcutsBundle> = combine(shortcutEntries, visibleApps) { shortcuts, apps ->
        if (apps == null) return@combine NO_SHORTCUTS
        val visibleKeys = apps.mapTo(HashSet()) { it.key }
        val results = shortcuts.filter { it.key in visibleKeys }.map { SearchResult.Shortcut(it) }
        ShortcutsBundle(
            buildSource("shortcuts", results.map { SearchIndex.Candidate(it, it.entry.label) }),
            results.groupBy { it.entry.key }
        )
    }.flowOn(defaultDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NO_SHORTCUTS)

    // Ghép các nguồn (không chuẩn hóa gì): tắt nguồn nào thì nguồn đó không vào chỉ mục (Task 6 của M8d).
    private val searchIndex: Flow<IndexBundle?> = combine(
        appsSource, settingsSource, contactsSource, shortcutsSource, prefs
    ) { apps, settings, contacts, shortcuts, p ->
        if (apps == null) return@combine null
        val index = SearchIndex(
            listOf(
                apps,
                if (p.systemSettings) settings else SearchIndex.Source.EMPTY,
                if (p.contacts) contacts else SearchIndex.Source.EMPTY,
                if (p.shortcuts) shortcuts.source else SearchIndex.Source.EMPTY
            )
        )
        IndexBundle(index, if (p.shortcuts) shortcuts.byApp else emptyMap())
    }.flowOn(defaultDispatcher)

    // G3a: hàng mời xin quyền khi chưa cấp quyền mà người dùng chưa tắt (nút "x" hoặc công tắc trong Cài đặt).
    private val promptRow: Flow<SearchResult.ContactsPermissionPrompt?> =
        combine(contactsGranted, contactsPermanentlyDenied, prefs) { granted, denied, p ->
            if (!granted && p.contacts) SearchResult.ContactsPermissionPrompt(openSettings = denied) else null
        }

    // mapLatest: phím mới hủy phép tính của phím cũ; phép tính chạy ở luồng nền, Main chỉ nhận trạng thái.
    private val computed: Flow<SearchUiState> = combine(
        combine(
            visibleApps,
            searchIndex,
            query,
            showAllWhenEmpty,
            searchMode
        ) { apps, index, q, showAll, mode -> arrayOf<Any?>(apps, index, q, showAll, mode) },
        combine(
            recentApps.entries.catch { emit(emptyList()) },
            prefs,
            promptRow,
            recentCollapsed
        ) { recents, p, prompt, collapsed ->
            Extra(if (p.recentApps) recents else emptyList(), prompt, p.suggestions, collapsed, p.recentApps)
        },
        combine(
            capacityAppRows,
            capacityWebRows,
            capacityColumns,
            queryHistory,
            combine(remoteSuggestions, trendingSearches) { rem, hot -> Pair(rem, hot) }
        ) { aR, wR, cols, hist, web ->
            arrayOf(aR, wR, cols, hist, web.first, web.second)
        }
    ) { a, extra, cap ->
        @Suppress("UNCHECKED_CAST")
        Inputs(
            apps = a[0] as List<AppItem>?,
            index = a[1] as IndexBundle?,
            query = a[2] as String,
            showAll = a[3] as Boolean,
            recents = extra.first,
            prompt = if (a[4] == SearchMode.SPOTLIGHT) null else extra.second,
            mode = a[4] as SearchMode,
            appRows = cap[0] as Int,
            webRows = cap[1] as Int,
            columns = cap[2] as Int,
            history = cap[3] as List<String>,
            remote = cap[4] as List<String>,
            // Tắt "Gợi ý và app gần đây" trong Cài đặt thì không hiện tìm kiếm thịnh hành.
            trending = if (extra.third) cap[5] as List<String> else emptyList(),
            recentCollapsed = extra.collapsed,
            recentOn = extra.recentOn
        )
    }.mapLatest { compute(it) }.flowOn(defaultDispatcher)

    val uiState: StateFlow<SearchUiState> =
        combine(computed, isOpen, recentCollapsed) { state, open, collapsed -> state.copy(isOpen = open, recentCollapsed = collapsed) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    /** Nút "Thu gọn" / "Hiện thêm" trên tiêu đề "Gần đây" (ô trống Spotlight): 2 hàng <-> 1 hàng. */
    fun toggleRecentCollapsed() {
        recentCollapsed.value = !recentCollapsed.value
    }

    fun updateCapacity(columns: Int, appRows: Int, webRows: Int) {
        capacityColumns.value = columns
        capacityAppRows.value = appRows
        capacityWebRows.value = webRows
    }

    private fun buildSource(name: String, candidates: List<SearchIndex.Candidate>): SearchIndex.Source {
        if (candidates.isEmpty()) return SearchIndex.Source.EMPTY
        return SearchIndex.Source.of(candidates)
    }

    /** Đọc lại shortcut của mọi app hiển thị (dùng lại đường của menu nhấn giữ M7). Chạy nền, có hạn chót. */
    private fun refreshShortcuts() {
        if (shortcutJob?.isActive == true) return
        val start = SystemClock.elapsedRealtime()
        shortcutJob = viewModelScope.launch(defaultDispatcher) {
            if (!prefs.first().shortcuts) return@launch // đã tắt trong Cài đặt: không đọc
            // Quyền kiểm TRƯỚC cache: đổi launcher mặc định (hoặc mất quyền) thì bỏ ngay shortcut cũ, không đợi hết 60 giây.
            if (!shortcutRepository.canReadShortcuts()) {
                shortcutEntries.value =
                    emptyList() // không phải launcher mặc định: im lặng, thử lại ở lần mở sau
                lastShortcutBuildMs = 0L
                return@launch
            }
            if (lastShortcutBuildMs != 0L && start - lastShortcutBuildMs < SHORTCUT_CACHE_MS) return@launch
            val apps = visibleApps.filterNotNull().first()
            val out = ArrayList<ShortcutEntry>()
            for (app in apps) {
                if (SystemClock.elapsedRealtime() - start > SHORTCUT_BUILD_DEADLINE_MS) break
                shortcutRepository.getShortcuts(app.key, 0)
                    .mapTo(out) { ShortcutEntry(app.key, it.id, it.shortLabel, app.label) }
            }
            lastShortcutBuildMs = SystemClock.elapsedRealtime()
            shortcutEntries.value = out
            Log.d(
                TAG,
                "shortcut index: ${out.size} entries from ${apps.size} apps in ${lastShortcutBuildMs - start} ms"
            )
        }
    }

    /**
     * Kiểu iOS 17: app đứng đầu kết quả kèm tối đa 3 shortcut của nó ngay dưới. Tổng shortcut vẫn <= 5: phần khớp nhãn
     * riêng bị cắt bớt cho vừa. Cần truy vấn >= 2 ký tự (cùng luật với khớp nhãn shortcut).
     */
    private fun withTopHitShortcuts(
        results: List<SearchResult>,
        bundle: IndexBundle,
        q: String
    ): List<SearchResult> {
        val top = results.firstOrNull() as? SearchResult.App ?: return results
        if (AppSearchMatcher.normalize(q).length < SearchKind.SHORTCUT.minQueryLength) return results
        val own = bundle.shortcutsByApp[top.item.key].orEmpty().take(MAX_TOP_HIT_SHORTCUTS)
        if (own.isEmpty()) return results
        val ownIds = own.mapTo(HashSet()) { it.stableId }
        val keptMatches = results.filter { it is SearchResult.Shortcut && it.stableId !in ownIds }
            .take(SearchKind.SHORTCUT.maxResults - own.size).mapTo(HashSet()) { it.stableId }
        val out = ArrayList<SearchResult>(results.size + own.size)
        out.add(top)
        out.addAll(own)
        for (r in results.drop(1)) {
            if (r is SearchResult.Shortcut && (r.stableId in ownIds || r.stableId !in keptMatches)) continue
            out.add(r)
        }
        return out
    }

    /**
     * Ô trống: "Gần đây" (<= 5 app mở mới nhất) rồi "Gợi ý" (<= 4 app mở >= 3 lần, trừ các app đã ở Gần đây).
     * Giao với [apps] (đã loại app ẩn) nên app đã gỡ/ẩn không xuất hiện.
     */
    private fun suggestionRows(apps: List<AppItem>, recents: List<RecentApp>): List<SearchResult> {
        if (recents.isEmpty()) return emptyList()
        val byKey = apps.associateBy { it.key }
        val live = recents.filter { it.key in byKey }
        val recent = live.sortedByDescending { it.lastLaunchedMillis }.take(MAX_RECENT)
        val recentKeys = recent.mapTo(HashSet()) { it.key }
        val frequent =
            live.filter { it.launchCount >= FREQUENT_MIN_LAUNCHES && it.key !in recentKeys }
                .sortedWith(compareByDescending<RecentApp> { it.launchCount }.thenByDescending { it.lastLaunchedMillis })
                .take(MAX_FREQUENT)
        val rows = ArrayList<SearchResult>()
        if (recent.isNotEmpty()) {
            rows.add(SearchResult.Header(SearchResult.Section.RECENT))
            recent.mapTo(rows) { SearchResult.App(byKey.getValue(it.key)) }
        }
        if (frequent.isNotEmpty()) {
            rows.add(SearchResult.Header(SearchResult.Section.FREQUENT))
            frequent.mapTo(rows) { SearchResult.App(byKey.getValue(it.key)) }
        }
        return rows
    }

    private suspend fun compute(input: Inputs): SearchUiState {
        val apps = input.apps
        val q = input.query
        val mode = input.mode
        val columns = input.columns
        val appRows = input.appRows
        val webRows = input.webRows

        if (apps == null) return SearchUiState(query = q, status = SearchStatus.LOADING, mode = mode, columns = columns, appRows = appRows, webRows = webRows)

        if (mode == SearchMode.SPOTLIGHT) {
            val maxApps = appRows * columns
            val byKey = apps.associateBy { it.key }
            val liveRecents = input.recents.filter { it.key in byKey }.sortedByDescending { it.lastLaunchedMillis }.map { byKey.getValue(it.key) }

            if (AppSearchMatcher.normalize(q).isEmpty()) {
                // P3-5: tắt "App gần đây" thì không lấp app hệ thống, mục Gần đây biến mất hẳn.
                val systemFill = if (input.recentOn) SystemAppFiller.fillSystemApps(apps, liveRecents, maxApps) else emptyList()
                val spotlightBuild = SpotlightLayoutBuilder.buildEmptyState(
                    liveRecents, systemFill, maxApps, input.trending, webRows, columns, input.recentCollapsed
                )
                return SearchUiState(
                    query = q,
                    results = spotlightBuild.items,
                    status = if (spotlightBuild.items.isEmpty()) SearchStatus.IDLE else SearchStatus.RESULTS,
                    mode = SearchMode.SPOTLIGHT,
                    columns = columns,
                    appRows = appRows,
                    webRows = webRows
                )
            } else {
                val index = input.index ?: return SearchUiState(query = q, status = SearchStatus.LOADING, mode = SearchMode.SPOTLIGHT, columns = columns, appRows = appRows, webRows = webRows)
                val rawResults = index.index.search(q)
                val matchedApps = rawResults.filterIsInstance<SearchResult.App>()
                val spotlightBuild = SpotlightLayoutBuilder.buildTypingState(
                    q, matchedApps, input.history, input.remote, appRows, webRows, columns
                )
                return SearchUiState(
                    query = q,
                    results = spotlightBuild.items,
                    status = if (spotlightBuild.items.isEmpty()) SearchStatus.NO_RESULTS else SearchStatus.RESULTS,
                    mode = SearchMode.SPOTLIGHT,
                    columns = columns,
                    appRows = appRows,
                    webRows = webRows
                )
            }
        }

        if (AppSearchMatcher.normalize(q).isEmpty()) {
            // `apps` đã sắp theo nhãn (AppRepository.apps) -> a-z.
            if (input.showAll && apps.isNotEmpty()) {
                return SearchUiState(
                    query = q,
                    results = apps.map { SearchResult.App(it) },
                    status = SearchStatus.RESULTS,
                    mode = SearchMode.LIBRARY,
                    columns = columns,
                    appRows = appRows,
                    webRows = webRows
                )
            }
            // G3a: hàng mời đứng cuối ô trống (hoặc một mình nếu không có gợi ý nào).
            val rows = suggestionRows(apps, input.recents) + listOfNotNull(input.prompt)
            return if (rows.isEmpty()) {
                SearchUiState(query = q, status = SearchStatus.IDLE, mode = SearchMode.LIBRARY, columns = columns, appRows = appRows, webRows = webRows)
            } else {
                SearchUiState(query = q, results = rows, status = SearchStatus.RESULTS, mode = SearchMode.LIBRARY, columns = columns, appRows = appRows, webRows = webRows)
            }
        }
        val index = input.index ?: return SearchUiState(query = q, status = SearchStatus.LOADING, mode = SearchMode.LIBRARY, columns = columns, appRows = appRows, webRows = webRows)
        val found = withTopHitShortcuts(index.index.search(q), index, q)
        // G3a: hàng mời cuối kết quả (từ 2 ký tự, như danh bạ); không có kết quả nào thì hàng mời thay dòng "Không có kết quả".
        val prompt =
            input.prompt?.takeIf { AppSearchMatcher.normalize(q).length >= SearchKind.CONTACT.minQueryLength }
        val results = if (prompt != null) found + prompt else found
        return SearchUiState(
            query = q,
            results = results,
            status = if (results.isEmpty()) SearchStatus.NO_RESULTS else SearchStatus.RESULTS,
            mode = SearchMode.LIBRARY,
            columns = columns,
            appRows = appRows,
            webRows = webRows
        )
    }

    private val _events = Channel<SearchEvent>(Channel.BUFFERED)
    val events: Flow<SearchEvent> = _events.receiveAsFlow()

    /** [showAll] = true khi mở từ trang App Library (truy vấn rỗng hiện toàn bộ app); mở bằng vuốt xuống/pill giữ false. */
    fun open(mode: SearchMode = SearchMode.SPOTLIGHT, showAll: Boolean = false) {
        searchMode.value = mode
        showAllWhenEmpty.value = showAll
        isOpen.value = true
        indexActive.value = true // C9
        ensureSettingsCatalog()
        refreshShortcuts()
        if (mode == SearchMode.LIBRARY) {
            refreshContacts()
        } else {
            viewModelScope.launch {
                queryHistory.value = searchQueryHistory.getHistory()
            }
            viewModelScope.launch {
                if (!prefs.first().suggestions) return@launch // đã tắt gợi ý trong Cài đặt: không gọi mạng
                trendingSearches.value = trendingSearchRepository.trending() // repo tự giữ 30 phút
            }
        }
    }

    /**
     * G3a: đọc lại quyền và (nếu lớp đang mở, đã cấp quyền, chưa tắt) nạp lại tên vào RAM. Gọi lúc mở lớp, khi quay lại từ màn quyền
     * (ON_RESUME) và khi danh bạ đổi. Lớp đóng hoặc mất quyền thì xóa tên khỏi RAM.
     */
    fun refreshContacts() {
        contactsLoadJob?.cancel()
        contactsLoadJob = viewModelScope.launch {
            val granted = contactsRepository.hasPermission()
            contactsGranted.value = granted
            if (granted) contactsPermanentlyDenied.value = false
            if (!isOpen.value || !granted || !prefs.first().contacts) {
                contactEntries.value = emptyList()
                stopContactsWatch()
                return@launch
            }
            startContactsWatch()
            val names = contactsRepository.loadNames(MAX_CONTACTS)
            // Quyền có thể mất giữa chừng (loadNames trả rỗng): coi như chưa cấp quyền.
            if (names.isEmpty() && !contactsRepository.hasPermission()) contactsGranted.value =
                false
            if (isOpen.value) contactEntries.value = names
            // CP pha 2: một truy vấn số cho cả danh bạ; tìm theo tên đã dùng được từ pha 1. loadPhones ném CancellationException
            // khi đóng lớp/ON_STOP/danh bạ đổi hủy job này, nên số của phiên cũ không bao giờ ghi vào contactEntries mới.
            if (names.isNotEmpty()) {
                val phones = contactsRepository.loadPhones(names.mapTo(HashSet()) { it.contactId })
                if (phones.isEmpty()) Log.w("G3A", "no numbers loaded for ${names.size} contacts") // chỉ số lượng; hàng mất số + nút gọi/nhắn (đánh đổi đã chấp nhận)
                ensureActive()
                if (isOpen.value && phones.isNotEmpty()) {
                    contactEntries.value = names.map { it.copy(phones = phones[it.contactId].orEmpty()) }
                }
            }
        }
    }

    private fun startContactsWatch() {
        if (contactsWatchJob?.isActive == true) return
        contactsWatchJob = viewModelScope.launch {
            contactsRepository.changes().collectLatest {
                delay(CONTACTS_CHANGE_DEBOUNCE_MS)
                refreshContacts()
            }
        }
    }

    private fun stopContactsWatch() {
        contactsWatchJob?.cancel()
        contactsWatchJob = null
    }

    /**
     * C6: Activity dừng (rời launcher bằng Recents/thông báo khi lớp tìm kiếm còn mở): bỏ tên danh bạ khỏi RAM và gỡ
     * ContentObserver, nhưng giữ `isOpen` và chuỗi gõ. ON_RESUME gọi [refreshContacts] nạp lại.
     */
    fun onHostStopped() {
        contactsLoadJob?.cancel()
        stopContactsWatch()
        contactEntries.value = emptyList()
    }

    /** G3a: bấm hàng "Hiện danh bạ". Controller xin quyền hoặc mở Cài đặt ứng dụng; cờ "đã xin" ghi sau khi đọc giá trị cũ. */
    fun onContactsPromptTapped() {
        viewModelScope.launch {
            val asked = prefs.first().contactsAsked
            _events.send(SearchEvent.RequestContactsPermission(asked))
            if (!asked) searchPreferences.update { it.copy(contactsAsked = true) }
        }
    }

    /** G3a: kết quả xin quyền. [permanentlyDenied]: bị từ chối và hệ thống không hỏi lại nữa -> hàng mời đổi sang "mở Cài đặt". */
    fun onContactsPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        if (!granted) contactsPermanentlyDenied.value = permanentlyDenied
        refreshContacts()
    }

    /** G3a: nút "x" trên hàng mời = tắt tìm danh bạ (bật lại ở Cài đặt). Không thu hồi quyền hệ thống. */
    fun dismissContactsPrompt() {
        viewModelScope.launch { searchPreferences.update { it.copy(contacts = false) } }
    }

    /** G3a: nút gọi/nhắn trên một hàng danh bạ. */
    fun onContactCall(contact: SearchResult.Contact) {
        val number = contact.phone ?: return
        if (contactsRepository.dial(number)) close() else _events.trySend(SearchEvent.ContactActionFailed)
    }

    fun onContactMessage(contact: SearchResult.Contact) {
        val number = contact.phone ?: return
        if (contactsRepository.message(number)) close() else _events.trySend(SearchEvent.ContactActionFailed)
    }

    /** Hàng danh bạ có nút gọi/nhắn không (máy có điện thoại)? */
    val canCallAndMessage: Boolean get() = contactsRepository.canCallAndMessage()

    /** Đóng và xóa truy vấn (D7). */
    fun close() {
        isOpen.value = false
        query.value = ""
        showAllWhenEmpty.value = false
        // G3a: tên người liên hệ không ở lại RAM sau khi đóng lớp.
        contactsLoadJob?.cancel()
        stopContactsWatch()
        contactEntries.value = emptyList()
    }

    fun onQueryChanged(text: String) {
        query.value = text
    }

    fun onResultTapped(result: SearchResult, source: ScreenRect?) {
        when (result) {
            is SearchResult.App -> {
                val app = result.item
                if (appRepository.launch(
                        app.key,
                        source
                    )
                ) close() else _events.trySend(SearchEvent.LaunchFailed(app.label))
            }

            is SearchResult.Header -> Unit
            is SearchResult.ContactsPermissionPrompt -> onContactsPromptTapped()
            is SearchResult.Contact -> {
                if (contactsRepository.openContact(result.entry)) close() else _events.trySend(
                    SearchEvent.ContactActionFailed
                )
            }

            is SearchResult.Shortcut -> {
                val e = result.entry
                if (shortcutRepository.startShortcut(e.key, e.id)) close() else _events.trySend(
                    SearchEvent.LaunchFailed(e.label)
                )
            }

            is SearchResult.Setting -> {
                if (systemSettings.open(result.entry.id)) close() else _events.trySend(
                    SearchEvent.LaunchFailed(
                        result.entry.label
                    )
                )
            }

            is SearchResult.WebSuggestion -> {
                viewModelScope.launch {
                    searchQueryHistory.addQuery(result.query)
                }
                _events.trySend(SearchEvent.LaunchWebSearch(result.query))
                close()
            }
        }
    }

    suspend fun loadIcon(result: SearchResult, sizePx: Int): Bitmap? = when (result) {
        is SearchResult.App -> appRepository.icon(result.item.key, sizePx)
        is SearchResult.Shortcut ->
            shortcutRepository.shortcutIcon(result.entry.key, result.entry.id, sizePx) // C8: một icon, có cache

        is SearchResult.Contact -> contactsRepository.avatar(result.entry, sizePx)
        is SearchResult.Setting, is SearchResult.Header, is SearchResult.ContactsPermissionPrompt, is SearchResult.WebSuggestion -> null // icon bánh răng cố định / tiêu đề: adapter tự xử lý
    }

    private companion object {
        const val MAX_RECENT = 5
        const val MAX_CONTACTS = 5000
        const val CONTACTS_CHANGE_DEBOUNCE_MS = 300L
        const val MAX_FREQUENT = 4
        const val FREQUENT_MIN_LAUNCHES = 3
        const val MAX_TOP_HIT_SHORTCUTS = 3
        const val SHORTCUT_CACHE_MS = 60_000L
        const val SHORTCUT_BUILD_DEADLINE_MS = 8_000L
        const val TAG = "SearchViewModel"
        private val NO_SHORTCUTS = ShortcutsBundle(SearchIndex.Source.EMPTY, emptyMap())
    }
}
