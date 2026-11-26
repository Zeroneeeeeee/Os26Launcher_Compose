package gambi.global.os26launcher.ui.home

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.core.ui.GridMetrics
import gambi.global.os26launcher.domain.layout.DockInsertPlanner
import gambi.global.os26launcher.domain.layout.FolderPlanner
import gambi.global.os26launcher.domain.layout.GridInsertPlanner
import gambi.global.os26launcher.domain.layout.GridRemovePlanner
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.AppShortcutItem
import gambi.global.os26launcher.domain.model.CategoryGroup
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.ContainerId
import gambi.global.os26launcher.domain.model.DockApp
import gambi.global.os26launcher.domain.model.GridPosition
import gambi.global.os26launcher.domain.model.HomePage
import gambi.global.os26launcher.domain.model.IconStyleKey
import gambi.global.os26launcher.domain.model.PageId
import gambi.global.os26launcher.domain.model.PlacedApp
import gambi.global.os26launcher.domain.model.PlacedFolder
import gambi.global.os26launcher.domain.model.PlacedItem
import gambi.global.os26launcher.domain.model.PlacedWidget
import gambi.global.os26launcher.domain.model.PlacedWidgetBase
import gambi.global.os26launcher.domain.model.ScreenRect
import gambi.global.os26launcher.domain.model.WidgetKind
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.repository.BackdropRepository
import gambi.global.os26launcher.domain.repository.DefaultLauncherRepository
import gambi.global.os26launcher.domain.repository.LayoutRepository
import gambi.global.os26launcher.domain.config.AppStateRepository
import gambi.global.os26launcher.domain.config.AppearanceRepository
import gambi.global.os26launcher.domain.config.GeneralConfigRepository
import gambi.global.os26launcher.domain.usecase.AddToFolderUseCase
import gambi.global.os26launcher.domain.model.AppWidgetRef
import gambi.global.os26launcher.domain.repository.AppWidgetRepository
import gambi.global.os26launcher.domain.usecase.AddAppWidgetUseCase
import gambi.global.os26launcher.domain.usecase.AddWidgetUseCase
import gambi.global.os26launcher.domain.usecase.CreateFolderUseCase
import gambi.global.os26launcher.domain.usecase.GetAppShortcutsUseCase
import gambi.global.os26launcher.domain.usecase.GetCategoryGroupsUseCase
import gambi.global.os26launcher.domain.model.LibraryContent
import gambi.global.os26launcher.domain.usecase.HideAppUseCase
import gambi.global.os26launcher.domain.usecase.LaunchShortcutUseCase
import gambi.global.os26launcher.domain.usecase.MoveItemUseCase
import gambi.global.os26launcher.domain.usecase.PruneEmptyPagesUseCase
import gambi.global.os26launcher.domain.usecase.RemoveWidgetUseCase
import gambi.global.os26launcher.domain.usecase.RenameFolderUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val backdropRepository: BackdropRepository,
    private val defaultLauncherRepository: DefaultLauncherRepository,
    private val appearance: AppearanceRepository,
    private val generalConfig: GeneralConfigRepository,
    private val appState: AppStateRepository,
    private val appRepository: AppRepository,
    private val layoutRepository: LayoutRepository,
    private val moveItemUseCase: MoveItemUseCase,
    private val hideAppUseCase: HideAppUseCase,
    private val createFolderUseCase: CreateFolderUseCase,
    private val renameFolderUseCase: RenameFolderUseCase,
    private val addToFolderUseCase: AddToFolderUseCase,
    private val getAppShortcuts: GetAppShortcutsUseCase,
    getCategoryGroups: GetCategoryGroupsUseCase,
    private val launchShortcut: LaunchShortcutUseCase,
    private val addWidgetUseCase: AddWidgetUseCase,
    private val addAppWidgetUseCase: AddAppWidgetUseCase, // G3c Task 6
    private val appWidgetRepository: AppWidgetRepository, // G3c Task 6: nhả "đang trong luồng", giải phóng id khi không ghi được hàng
    private val removeWidgetUseCase: RemoveWidgetUseCase,
    private val pruneEmptyPagesUseCase: PruneEmptyPagesUseCase, // EP
    private val iconPackRepository: gambi.global.os26launcher.domain.repository.IconPackRepository, // IP
    observeRecentApps: gambi.global.os26launcher.domain.usecase.ObserveRecentAppsUseCase, // TV-2
    private val todayWidgetsRepository: gambi.global.os26launcher.domain.today.TodayWidgetsRepository, // TV-3
    private val removeTodayWidgetUseCase: gambi.global.os26launcher.domain.usecase.RemoveTodayWidgetUseCase, // TV-3
    private val removeFromHomeUseCase: gambi.global.os26launcher.domain.usecase.RemoveFromHomeUseCase, // TF
    private val addAppAtUseCase: gambi.global.os26launcher.domain.usecase.AddAppAtUseCase // LD
) : ViewModel() {

    /** TV-2: app gần đây của Today View (đã bỏ app gỡ/ẩn, mới nhất trước, tối đa 8). `null` = Loading. */
    val todayApps: StateFlow<List<AppItem>?> =
        observeRecentApps(TODAY_RECENT_APPS).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** TV-3: danh sách widget của Today View theo thứ tự hiện. `null` = Loading. */
    val todayWidgets: StateFlow<List<gambi.global.os26launcher.domain.today.TodayWidget>?> =
        todayWidgetsRepository.widgets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** G2a B2: nội dung trang App Library (LO: thư mục hoặc A–Z). `null` = Loading. */
    val categoryGroups: StateFlow<LibraryContent?> =
        getCategoryGroups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // M7 Task 5 Step 6 (cập nhật lạc quan): DB ghi bất đồng bộ nên lúc thả tay ta phát NGAY danh sách trang đã áp
    // kế hoạch di chuyển (GridInsertPlanner, cùng thuật toán với DB). Trong lúc còn lệnh ghi đang chạy
    // ([pendingMoves] > 0) bỏ qua emission từ DB (có thể là ảnh cũ trước khi ghi) nhưng luôn nhớ bản mới nhất
    // ở [latestDbPages]; ghi xong thì [scheduleReconcile] đảm bảo UI về đúng DB. KHAI BÁO TRƯỚC khối init bên dưới:
    // coroutine thu `pages` có thể chạy ngay (Dispatchers.Main.immediate).
    // M7 Task 6: dock đi chung cơ chế này (cùng [pendingMoves]) -- thả lưới<->dock đổi CẢ pages lẫn dock trong 1 lần.
    private var latestDbPages: List<HomePage> = emptyList()
    private var latestDbDock: List<DockApp> = emptyList()
    private var pendingMoves = 0
    private var reconcileJob: Job? = null

    // M8a Task 2: gridStatus chỉ Ready khi CẢ cài đặt (cỡ lưới, nhãn) LẪN bố cục đã phát lần đầu.
    private var settingsLoaded = false
    private var dbPagesReceived = false

    // M4: bố cục đến từ LayoutRepository (đã seed + ghép nhãn). Flow của nó không có giá trị khởi tạo giả,
    // nên Loading là giá trị mặc định của HomeUiState cho tới khi pages phát lần đầu (= dữ liệu thật).
    init {
        viewModelScope.launch {
            layoutRepository.pages.collect { pages ->
                latestDbPages = pages
                dbPagesReceived = true
                if (pendingMoves > 0) return@collect
                publishPages(pages)
            }
        }
        viewModelScope.launch {
            combine(
                generalConfig.config.map { it.gridPreset }.distinctUntilChanged(),
                appearance.config.map { it.showLabels }.distinctUntilChanged()
            ) { grid, showLabels -> grid to showLabels }
                .distinctUntilChanged()
                .collect { (grid, showLabels) ->
                    settingsLoaded = true
                    _uiState.update { it.copy(grid = grid, showLabels = showLabels) }
                    // Bố cục có thể về trước cài đặt: giờ mới được Ready (đang ghi dở thì publishFromDb sau đó lo).
                    if (dbPagesReceived && pendingMoves == 0) publishPages(latestDbPages)
                }
        }
        viewModelScope.launch {
            layoutRepository.dock.collect { dock ->
                latestDbDock = dock
                if (pendingMoves > 0) return@collect
                _uiState.update { it.copy(dock = dock) }
            }
        }
    }

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    // Chỉ nạp ảnh một lần cho cả vòng đời ViewModel (Activity có thể gọi onScreenSize nhiều lần)
    private var screenSizeHandled = false

    // M8a Task 5: phiên bản ảnh nền đang hiển thị. Gán ĐỒNG BỘ trước khi chạy coroutine nạp (onResume có thể chạy
    // ngay sau onScreenSize, nếu không sẽ nạp hai lần).
    private var shownBackdropVersion = 0L

    // FX1 (B2): cỡ màn hợp lệ cuối cùng. Activity mới dựng lại (đổi sáng/tối) có homeRoot.width = 0 ở onResume đầu (layout chạy sau
    // onResume) nên không thể dựa vào cỡ do Activity truyền; ViewModel sống sót nên nhớ cỡ ở đây (đổi cỡ màn không dựng lại Activity:
    // screenSize/orientation nằm trong configChanges).
    private var lastScreenW = 0
    private var lastScreenH = 0

    // FX1: chỉ một lần nạp ảnh nền chạy tại một thời điểm; lần nạp sau hủy lần trước nên luôn là ảnh mới nhất thắng.
    private var backdropJob: Job? = null

    // FX1 (C4): ảnh nền đổi do màn Cài đặt (kể cả khi người dùng đã Back trước khi nhập xong) thì nạp lại tại chỗ. ĐẶT SAU các trường ở trên
    // vì viewModelScope chạy Dispatchers.Main.immediate. Giá trị đầu của flow tới khi screenSizeHandled còn false nên bị bỏ qua đúng;
    // onScreenSize lo lần nạp đầu. Cỡ dùng là cỡ đã nhớ (tham số 0).
    init {
        viewModelScope.launch {
            backdropRepository.backdropVersions.collect { reloadBackdropIfChanged(0, 0) }
        }
    }

    /** Gọi khi đã biết kích thước màn hình thật. Chỉ lần đầu nạp ảnh; mọi lần đều ghi nhớ cỡ. */
    fun onScreenSize(width: Int, height: Int) {
        rememberScreenSize(width, height)
        if (screenSizeHandled) return
        screenSizeHandled = true
        shownBackdropVersion = backdropRepository.backdropVersion()
        launchBackdropLoad(width, height)
    }

    private fun rememberScreenSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            lastScreenW = width
            lastScreenH = height
        }
    }

    // RR: cỡ (cửa sổ Home) của lần giải mã ảnh nền gần nhất. Bitmap được cắt theo đúng tỉ lệ này (BitmapScaler.cropRegion), nên cửa sổ toàn
    // màn đổi cỡ (xoay trên máy lớn, gập/mở, đổi cỡ màn) thì phải giải mã lại. Gán ĐỒNG BỘ như shownBackdropVersion.
    private var decodedW = 0
    private var decodedH = 0

    private fun launchBackdropLoad(width: Int, height: Int) {
        decodedW = width
        decodedH = height
        backdropJob?.cancel()
        backdropJob = viewModelScope.launch {
            // CF: lần nạp đầu phải biết nguồn nền (ảnh hay màu) trước khi chọn đường nạp, không thì nháy ảnh mặc định rồi mới sang màu.
            // Phiên bản đọc lại sau khi chờ để lần so ở onResume/backdropVersions không nạp lại thừa.
            backdropRepository.awaitWallpaperConfig()
            shownBackdropVersion = backdropRepository.backdropVersion()
            loadBackdropFromDisk(width, height)
        }
    }

    /**
     * RR: cỡ cửa sổ Home vừa đứng yên sau khi đổi (WindowSizeRecovery). Giải mã lại ảnh nền theo cỡ mới nếu khác cỡ đã giải mã, trừ khi ở
     * multi-window (split-screen giữ ảnh toàn màn + centerCrop như M5, không giải mã theo từng bước kéo thanh chia; ra khỏi split-screen thì
     * cỡ về bằng cỡ cũ nên không làm gì). Lần nạp đang chạy bị hủy, ảnh mới nhất thắng. Không đụng dữ liệu bố cục.
     */
    fun onWindowSizeSettled(width: Int, height: Int, inMultiWindow: Boolean) {
        // Chỉ nhớ cỡ cửa sổ TOÀN MÀN: cỡ split-screen mà vào lastScreenW/H thì lần nạp lại ở onResume (ảnh đổi ở Cài đặt) giải mã theo tỉ lệ nửa màn.
        if (inMultiWindow || width <= 0 || height <= 0) return
        rememberScreenSize(width, height)
        if (!screenSizeHandled) return
        if (width == decodedW && height == decodedH) return
        launchBackdropLoad(width, height)
    }

    /**
     * M8a Task 5: HomeActivity gọi ở onResume. Màn Cài đặt có thể vừa đổi ảnh nền (file khác `lastModified`): nạp lại và
     * phát trạng thái mới. Chưa biết kích thước màn (onScreenSize chưa chạy) thì bỏ qua, onScreenSize sẽ nạp.
     */
    fun reloadBackdropIfChanged(width: Int, height: Int) {
        rememberScreenSize(width, height)
        // FX1 (B2): width/height có thể bằng 0 ở onResume đầu của Activity vừa dựng lại: dùng cỡ đã nhớ
        val w = if (width > 0) width else lastScreenW
        val h = if (height > 0) height else lastScreenH
        if (!screenSizeHandled || w <= 0 || h <= 0) return
        val version = backdropRepository.backdropVersion()
        if (version == shownBackdropVersion) return
        shownBackdropVersion = version
        launchBackdropLoad(w, h)
    }

    private suspend fun loadBackdropFromDisk(width: Int, height: Int) {
        if (!backdropRepository.hasBackdrop()) {
            // WP: chưa chọn ảnh thì dùng ảnh mặc định nếu có (không có thì như cũ: Empty, nền đen)
            val default =
                if (backdropRepository.hasDefaultBackdrop()) backdropRepository.loadDefaultBackdrop(
                    width,
                    height
                ) else null
            setBackdrop(
                if (default != null) BackdropStatus.Ready else BackdropStatus.Empty,
                default,
                isDefault = default != null
            )
            return
        }
        // hasBackdrop() true mà decode ra null: file bị xóa dở hoặc hỏng → Error, không crash
        val bmp = backdropRepository.loadBackdrop(width, height)
        setBackdrop(if (bmp != null) BackdropStatus.Ready else BackdropStatus.Error, bmp)
    }

    /**
     * CF Task 4: phát mỗi khi cấu hình giao diện đổi, KHÔNG phát giá trị hiện tại (drop(1) chạy lại ở mỗi lần collect).
     * HomeActivity thu khi RESUMED để chạy lại RecreateGuard lúc config đổi ngay trên Home (adb, sau này áp theme tại chỗ):
     * bấm Home khi Home đang ở trên cùng chỉ gọi onNewIntent, không qua onResume. Flow treo, không hỏi vòng.
     */
    val appearanceChanges: Flow<Unit> = appearance.config.distinctUntilChanged().drop(1).map { }

    /** OB: đã xem xong (hoặc bỏ qua) Onboarding chưa. HomeActivity đọc MỘT lần ở lần dựng đầu. */
    suspend fun isOnboardingDone(): Boolean = appState.state.first().onboardingDone

    /** G2b: kiểu icon + màu tô hiện tại (đọc một lần, không collect). HomeActivity so với giá trị đã áp để dựng lại khi đổi ở Cài đặt. */
    suspend fun currentIconStyleKey(): IconStyleKey =
        // IP: gói tính cả versionCode (hỏi PackageManager) nên gói cập nhật/gỡ lúc Home ở nền cũng làm Home dựng lại ở onResume
        appearance.config.first().let { s ->
            val pack =
                s.iconPack?.let { withContext(Dispatchers.IO) { iconPackRepository.keyOf(it) } }
                    ?: ""
            IconStyleKey(s.iconStyle, s.iconTint, pack, IconStyleKey.bevelFor(s.iconStyle, s.iconBevel), s.glassLook.corner)
        }

    /** Icon cho danh sách debug; M3 thay bằng lưới. Cache nằm ở repository nên gọi lại rẻ. */
    suspend fun loadIcon(key: ComponentKey, sizePx: Int): Bitmap? = appRepository.icon(key, sizePx)

    /** [source] là vùng icon đã bấm trên màn hình, để animation mở app xuất phát từ đó. */
    fun onAppClicked(item: AppItem, source: ScreenRect?) {
        if (!appRepository.launch(item.key, source)) {
            _events.trySend(HomeEvent.AppLaunchFailed(item.label))
        }
    }

    /** HomeActivity gọi ở onResume: người dùng có thể vừa đổi launcher mặc định ở màn cài đặt. */
    fun refreshDefaultLauncher() {
        val isDefault = defaultLauncherRepository.isDefaultLauncher()
        _uiState.update { it.copy(isDefaultLauncher = isDefault) }
        // P2-4 (2026-11-19): bỏ hộp thoại hỏi áp wallpaper sau khi thành launcher mặc định; Cài đặt hỏi khi chọn ảnh.
    }

    // Không recycle bitmap cũ: ImageView có thể còn đang vẽ nó, để GC thu hồi.
    private fun setBackdrop(status: BackdropStatus, bitmap: Bitmap?, isDefault: Boolean = false) {
        _uiState.update {
            it.copy(
                backdropStatus = status,
                backdrop = bitmap,
                backdropIsDefault = isDefault
            )
        }
    }

    // M7 Task 4: vào/thoát chế độ chỉnh sửa. Task 4 tạm nối nhấn giữ icon -> enterEditMode(); menu
    // TT#7 (đổi tên/gỡ/thông tin) sẽ nối vào Task 10, chưa thay thế lời gọi này.
    fun enterEditMode() {
        _uiState.update { it.copy(editMode = true) }
    }

    /**
     * EP: thoát edit mode thì dọn trang trống (cả trang ở giữa). Chỉ khi trước đó ĐANG ở edit mode và không có phiên kéo (F3:
     * `onStop` cũng gọi hàm này mỗi lần rời launcher). Không cập nhật lạc quan (F1): UI có thể thấy trống một trang mà DB vẫn còn
     * hàng app không khả dụng, nên để emission của Room bỏ trang. Ghi qua [launchLayoutWrite] để xếp sau lần thả cuối.
     */
    fun exitEditMode() {
        val before = _uiState.value
        _uiState.update { it.copy(editMode = false) }
        if (before.editMode && before.draggingItemId == null) pruneEmptyPages()
    }

    private fun pruneEmptyPages() {
        launchLayoutWrite(
            optimistic = { it },
            write = {
                pruneEmptyPagesUseCase()
                true
            },
            failureLog = "pruneEmptyPages failed"
        )
    }

    // M7 Task 10: nhấn giữ icon lúc CHƯA edit mode -> mở menu (trạng thái Shortcut), KHÔNG vào edit mode nữa.
    fun onIconLongPressed(
        itemId: Long,
        container: Long,
        key: ComponentKey,
        anchor: ScreenRect,
        shortcutIconSizePx: Int
    ) {
        var opened = false
        _uiState.update {
            if (it.editMode || it.draggingItemId != null) {
                it
            } else {
                opened = true
                it.copy(iconMenu = IconMenuState(itemId, container, key, anchor))
            }
        }
        if (!opened) return
        // Nạp shortcut bất đồng bộ (getShortcuts không ném lỗi, thất bại = rỗng). Menu đã đóng/đổi sang icon khác thì bỏ.
        viewModelScope.launch {
            val shortcuts = getAppShortcuts(key, shortcutIconSizePx)
            _uiState.update { s ->
                val menu = s.iconMenu
                // C12: app con trong thư mục đều có itemId = -1 nên itemId một mình không phân biệt hai app; so thêm container và key để
                // kết quả tải chậm của app A không áp vào menu của app B vừa mở sau đó.
                if (menu != null && menu.itemId == itemId && menu.container == container && menu.key == key) {
                    s.copy(iconMenu = menu.copy(shortcuts = shortcuts))
                } else {
                    s
                }
            }
        }
    }

    fun closeIconMenu() {
        _uiState.update { if (it.iconMenu == null) it else it.copy(iconMenu = null) }
    }

    /** Bấm 1 mục cố định của menu. Mọi mục đều đóng menu trước, rồi mới làm việc (lỗi hệ thống -> Toast). */
    fun onIconMenuAction(action: IconMenuAction) {
        val menu = _uiState.value.iconMenu ?: return
        closeIconMenu()
        when (action) {
            IconMenuAction.AppInfo -> if (!appRepository.openAppInfo(menu.key)) _events.trySend(
                HomeEvent.MenuActionFailed
            )

            IconMenuAction.HideApp -> if (menu.container > 0L) hideAppInFolder(menu.key) else onQuickHideTapped(
                menu.itemId
            )

            IconMenuAction.EditHome -> enterEditMode()
            IconMenuAction.Uninstall -> if (!appRepository.requestUninstall(menu.key)) _events.trySend(
                HomeEvent.MenuActionFailed
            )
        }
    }

    /** RA: app của ô [itemId] trên lưới/dock, để hộp thoại xác nhận xóa hiện tên. null = không phải app (widget, thư mục) hoặc đang kéo. */
    fun appForQuickRemove(itemId: Long): AppItem? {
        val s = _uiState.value
        if (s.draggingItemId != null) return null
        return s.pages.firstNotNullOfOrNull { page -> page.items.firstOrNull { it.itemId == itemId } }
            ?.let { (it as? PlacedApp)?.app }
            ?: s.dock.firstOrNull { it.itemId == itemId }?.app
    }

    /** RA: app [key] trong thư mục đang mở (hộp thoại xác nhận xóa trong panel thư mục). */
    fun appInOpenFolder(key: ComponentKey): AppItem? {
        val s = _uiState.value
        if (s.draggingItemId != null) return null
        val folderId = s.openFolderId ?: return null
        return folderIn(s.pages, s.dock, folderId)?.previewApps?.firstOrNull { it.key == key }
    }

    /** RA: "Xóa ứng dụng" ở hộp thoại xác nhận: mở trình gỡ cài đặt của hệ thống (app gỡ xong thì lưới tự cập nhật qua LauncherApps). */
    fun uninstallApp(key: ComponentKey) {
        if (!appRepository.requestUninstall(key)) _events.trySend(HomeEvent.MenuActionFailed)
    }

    fun onIconMenuShortcut(shortcut: AppShortcutItem) {
        val menu = _uiState.value.iconMenu ?: return
        closeIconMenu()
        if (!launchShortcut(menu.key, shortcut.id)) _events.trySend(HomeEvent.MenuActionFailed)
    }

    // M7 Task 5 Step 4: IconGridView báo bắt đầu/huỷ kéo. Trạng thái "đang kéo" là dữ liệu trong UiState,
    // không phải field trong view (RecyclerView có thể thay view bất cứ lúc nào — xem plan Task 5).
    fun onDragStarted(itemId: Long) {
        // M7 Task 10 (đường B): kéo bắt đầu lúc menu nhấn giữ đang mở -> đóng menu và vào edit mode trong CÙNG 1 lần phát.
        // Đã ở edit mode thì hai trường này vốn đã đúng, không đổi gì.
        _uiState.update { it.copy(draggingItemId = itemId, iconMenu = null, editMode = true) }
    }

    fun onDragCancelled() {
        _uiState.update { it.copy(draggingItemId = null, ghostPage = false) }
    }

    // M7 Task 7 (E2): người dùng giữ ngón tay ở mép phải của trang thật cuối cùng -> hiện trang trống tạm. Chỉ khi đang
    // kéo, chưa có ghost, và trang cuối còn icon khác ngoài icon đang kéo (không chồng thêm trang trống lên 1 trang trống).
    // Trả true nếu ghost được tạo (HomeActivity cần biết để lật sang nó).
    fun onDragEdgeBeyondLastPage(): Boolean {
        val s = _uiState.value
        val dragging = s.draggingItemId ?: return false
        if (s.ghostPage || s.gridStatus != GridStatus.Ready) return false
        val last = s.pages.lastOrNull() ?: return false
        if (last.items.none { it.itemId != dragging }) return false
        _uiState.update { it.copy(ghostPage = true) }
        return true
    }

    // Người dùng kéo ngược về 1 trang thật: bỏ trang tạm ngay, chưa hề ghi gì xuống DB.
    fun onGhostPageLeft() {
        _uiState.update { if (it.ghostPage) it.copy(ghostPage = false) else it }
    }

    /**
     * G2a A3a (D6/D9): ẩn app [key] đang nằm trong thư mục đang mở panel ("−" hoặc menu nhấn giữ trong panel). Cập nhật lạc
     * quan: bỏ app khỏi thư mục; còn <= 1 app thì đóng panel (DB giải tán thư mục, UI đối chiếu sau khi ghi xong).
     */
    fun hideAppInFolder(key: ComponentKey, hide: Boolean = true) {
        val s = _uiState.value
        if (s.draggingItemId != null) return
        val folderId = s.openFolderId ?: return
        launchLayoutWrite(
            optimistic = { st ->
                val folder = folderIn(st.pages, st.dock, folderId)
                if (folder == null) {
                    st
                } else {
                    val keep = folder.previewApps.indices.filter { folder.previewApps[it].key != key }
                    val (pages, dock) = replaceFolder(st.pages, st.dock, folderId) {
                        it.copy(
                            previewApps = keep.map { i -> it.previewApps[i] },
                            childIds = keep.map { i -> it.childIds[i] },
                            appCount = keep.size
                        )
                    }
                    st.copy(pages = pages, dock = dock, openFolderId = if (keep.size <= 1) null else st.openFolderId)
                }
            },
            write = {
                if (hide) hideAppUseCase(key, key.userSerial) else removeFromHomeUseCase(key) // TF: hide=false = "Xóa khỏi MH chính"
                true
            },
            failureLog = "hideAppInFolder failed folder=$folderId"
        )
    }

    /**
     * G2a A3a (D5): thả app [itemId] vào giữa ô thư mục [folderId]. Cập nhật lạc quan: bỏ app khỏi lưới/dock (ô cũ để trống,
     * KHÔNG dồn), thêm vào cuối thư mục. DB từ chối (đầy / không hợp lệ) thì UI về lại DB.
     */
    fun onAddToFolder(itemId: Long, folderId: Long) {
        launchLayoutWrite(
            optimistic = { s ->
                val fromChild =
                    findChild(s.pages, s.dock, itemId)?.second // G2a A3b: nguồn là con của thư mục khác
                val dragged = (s.pages.flatMap { it.items }
                    .firstOrNull { it.itemId == itemId } as? PlacedApp)?.app
                    ?: s.dock.firstOrNull { it.itemId == itemId }?.app
                    ?: fromChild
                val (basePages, baseDock) =
                    if (fromChild != null) detachChild(s.pages, s.dock, itemId) else s.pages to s.dock
                val folder = folderIn(basePages, baseDock, folderId)
                if (dragged == null || folder == null || folder.appCount >= FolderPlanner.MAX_ITEMS) {
                    s.copy(draggingItemId = null, ghostPage = false)
                } else {
                    val (pages, dock) = replaceFolder(
                        basePages.map { page -> page.copy(items = page.items.filter { it.itemId != itemId }) },
                        baseDock.filter { it.itemId != itemId },
                        folderId
                    ) {
                        it.copy(
                            previewApps = it.previewApps + dragged,
                            childIds = it.childIds + itemId,
                            appCount = it.appCount + 1
                        )
                    }
                    s.copy(pages = pages, dock = dock, draggingItemId = null, ghostPage = false)
                }
            },
            write = { addToFolderUseCase(itemId, folderId) },
            failureLog = "addToFolder failed item=$itemId folder=$folderId"
        )
    }

    /**
     * FM-3: thả app con vào chỗ mới trong panel thư mục [folderId]: [orderedChildIds] là thứ tự mới của các app nhìn thấy. Cập nhật lạc quan
     * (thư mục ở lưới hoặc dock) và bỏ `draggingItemId`; DB từ chối thì UI về lại DB.
     */
    fun reorderFolder(folderId: Long, orderedChildIds: List<Long>) {
        launchLayoutWrite(
            optimistic = { s ->
                val (pages, dock) = replaceFolder(s.pages, s.dock, folderId) { f ->
                    if (orderedChildIds.size != f.childIds.size || orderedChildIds.toSet() != f.childIds.toSet()) {
                        f
                    } else {
                        val byId = f.childIds.zip(f.previewApps).toMap()
                        f.copy(childIds = orderedChildIds, previewApps = orderedChildIds.map { byId.getValue(it) })
                    }
                }
                s.copy(pages = pages, dock = dock, draggingItemId = null, ghostPage = false)
            },
            write = { layoutRepository.reorderFolder(folderId, orderedChildIds) },
            failureLog = "reorderFolder failed folder=$folderId"
        )
    }

    /** G2a A2: mở panel thư mục. Bỏ qua id tạm (<= 0, thư mục vừa tạo chưa đối chiếu DB) và lúc đang kéo / menu nhấn giữ mở. */
    fun openFolder(folderId: Long) {
        if (folderId <= 0L) return
        _uiState.update {
            if (it.draggingItemId != null || it.iconMenu != null) it else it.copy(
                openFolderId = folderId
            )
        }
    }

    /** G2a B2: Library đã dừng hẳn ở trạng thái mở/đóng (sau cử chỉ/animation), hoặc cần đóng do trạng thái khác (D14). */
    fun setLibraryOpen(open: Boolean) {
        _uiState.update {
            if (it.libraryOpen == open) it else it.copy(
                libraryOpen = open,
                openCategoryGroup = if (open) it.openCategoryGroup else null
            )
        }
    }

    /** TV-1: Today View đã dừng hẳn ở trạng thái mở/đóng (sau cử chỉ/animation), hoặc cần đóng do trạng thái khác (như D14 của Library). */
    fun setTodayOpen(open: Boolean) {
        // TV-3: Today đóng (vuốt về Home, Back, nút Home) thì thoát luôn chế độ sửa của nó (chủ dự án 2026-11-20).
        _uiState.update { if (it.todayOpen == open) it else it.copy(todayOpen = open, todayEditMode = if (open) it.todayEditMode else false) }
    }

    /** TV-3: nhấn giữ trên Today -> chế độ sửa (chỉ khi Today đang mở). */
    fun enterTodayEditMode() {
        _uiState.update { if (!it.todayOpen || it.todayEditMode) it else it.copy(todayEditMode = true) }
    }

    fun exitTodayEditMode() {
        _uiState.update { if (!it.todayEditMode) it else it.copy(todayEditMode = false) }
    }

    /** TV-3: thêm widget vào cuối Today ([kindId] = `WidgetKind.id` hoặc `TodayWidget.RECENTS`). */
    fun addTodayWidget(kindId: String, wide: Boolean) {
        viewModelScope.launch { todayWidgetsRepository.add(kindId, wide) }
    }

    fun removeTodayWidget(id: Long) {
        viewModelScope.launch { removeTodayWidgetUseCase(id) }
    }

    /** TV-3: thả widget sau khi kéo đổi chỗ trên Today: [ids] là thứ tự mới. */
    fun reorderTodayWidgets(ids: List<Long>) {
        viewModelScope.launch { todayWidgetsRepository.reorder(ids) }
    }

    /** G2a B3: bấm ô nhóm trong Library -> mở panel nhóm. Chỉ khi Library đang mở. */
    fun openCategoryGroup(group: CategoryGroup) {
        _uiState.update { if (!it.libraryOpen) it else it.copy(openCategoryGroup = group) }
    }

    fun closeCategoryGroup() {
        _uiState.update { if (it.openCategoryGroup == null) it else it.copy(openCategoryGroup = null) }
    }

    fun closeFolder() {
        _uiState.update { if (it.openFolderId == null) it else it.copy(openFolderId = null) }
    }

    /** G2a A2: đổi tên thư mục. Cập nhật lạc quan tên trong `pages`, rồi ghi DB. */
    fun renameFolder(folderId: Long, rawTitle: String) {
        if (folderId <= 0L) return
        val title = FolderPlanner.normalizeTitle(rawTitle)
        launchLayoutWrite(
            optimistic = { s ->
                // P2-6: thư mục có thể nằm ở lưới hoặc trong dock.
                val (pages, dock) = replaceFolder(s.pages, s.dock, folderId) { it.copy(title = title) }
                s.copy(pages = pages, dock = dock)
            },
            write = {
                renameFolderUseCase(folderId, title)
                true
            },
            failureLog = "renameFolder failed id=$folderId"
        )
    }

    /**
     * G2a A1b: thả app [draggedId] đè giữa app [targetId] đủ lâu -> gộp thành thư mục. Cập nhật lạc quan: bỏ nguồn khỏi lưới
     * (ô cũ để trống, KHÔNG dồn), đổi đích thành thư mục với id tạm âm (DB thay bằng id thật khi đối chiếu). DB từ chối
     * (false) thì UI về lại DB.
     */
    fun onFolderIntent(draggedId: Long, targetId: Long) {
        launchLayoutWrite(
            optimistic = { s ->
                val fromChild =
                    findChild(s.pages, s.dock, draggedId)?.second // G2a A3b: nguồn là con của một thư mục
                val (basePages, baseDock) =
                    if (fromChild != null) detachChild(s.pages, s.dock, draggedId) else s.pages to s.dock
                val all = basePages.flatMap { it.items }
                val dragged = (all.firstOrNull { it.itemId == draggedId } as? PlacedApp)?.app
                    ?: baseDock.firstOrNull { it.itemId == draggedId }?.app // nguồn từ dock (dock đọc theo thứ tự rồi căn giữa lại)
                    ?: fromChild
                // Đích là app trên lưới hoặc trong dock: thư mục mới thế đúng chỗ của đích.
                val gridTarget = all.firstOrNull { it.itemId == targetId } as? PlacedApp
                val dockIndex = baseDock.indexOfFirst { it.itemId == targetId }
                val targetApp = gridTarget?.app ?: baseDock.getOrNull(dockIndex)?.app
                if (dragged == null || targetApp == null) {
                    s.copy(draggingItemId = null, ghostPage = false)
                } else {
                    val folder = PlacedFolder(
                        itemId = -targetId,
                        position = gridTarget?.position ?: GridPosition(dockIndex, 0),
                        title = "",
                        previewApps = listOf(targetApp, dragged),
                        childIds = listOf(targetId, draggedId),
                        appCount = 2
                    )
                    s.copy(
                        pages = basePages.map { page ->
                            page.copy(items = page.items.filter { it.itemId != draggedId }
                                .map { if (it.itemId == targetId) folder else it })
                        },
                        dock = baseDock.filter { it.itemId != draggedId }
                            .map { if (it.itemId == targetId) DockApp(folder.itemId, null, folder) else it },
                        draggingItemId = null,
                        ghostPage = false
                    )
                }
            },
            write = { createFolderUseCase(draggedId, targetId) },
            failureLog = "createFolder failed dragged=$draggedId target=$targetId"
        )
    }

    // M7 Task 5/6: IconGridView.onDrop() / DockView.onDrop() gọi khi thả tay. Lưới chính luôn nhận thả; dock có thể
    // từ chối nhưng DockView đã chặn ngay ở onDrop (không tới đây), DB vẫn kiểm lại và trả false nếu lệch.
    // Phát NGAY trạng thái lạc quan (pages + dock + bỏ draggingItemId trong CÙNG 1 lần phát để icon hiện ở chỗ mới
    // tức thì), rồi mới ghi DB.
    fun onItemDropped(
        itemId: Long,
        targetContainer: Long,
        targetPageId: Long?,
        cell: GridPosition
    ) {
        val state = _uiState.value
        // Thả lên trang tạm (PageId.NEW): dựng trang mới ở cuối (id = PageId.NEW, ổn định với ghost nên view không bị
        // thay) để kế hoạch lạc quan đặt item vào; DB tạo hàng thật rồi emission DB thay bằng id thật.
        val basePages = if (targetPageId == PageId.NEW) {
            state.pages + HomePage(state.pages.size, PageId.NEW, emptyList())
        } else {
            state.pages
        }
        val optimistic =
            planOptimistic(basePages, state.dock, itemId, targetContainer, targetPageId, cell)
        // ghostPage = false cho MỌI lần thả: thả lên ghost thì trang đã thành trang thật tạm trong `pages`, thả chỗ khác
        // thì ghost bị bỏ (E2). false từ DB = DB từ chối (ví dụ dock đã đủ): UI đang lệch DB -> về lại DB.
        launchLayoutWrite(
            optimistic = {
                it.copy(
                    pages = optimistic?.pages ?: it.pages,
                    dock = optimistic?.dock ?: it.dock,
                    draggingItemId = null,
                    ghostPage = false
                )
            },
            write = { moveItemUseCase(itemId, targetContainer, targetPageId, cell) },
            failureLog = "moveItem failed id=$itemId"
        )
    }

    // M7 Task 8: bấm dấu "−" trên 1 icon -> ẩn nhanh. Lưới: các icon SAU nó cùng trang dồn lên 1 bậc (Ruling G2, cùng
    // GridRemovePlanner với DB); dock: bỏ item (dock đọc theo thứ tự rồi căn giữa nên tự "dồn").
    /**
     * LD — thả app kéo từ App Library xuống lưới ([targetContainer] = HOME, trang [targetPageId], `PageId.NEW` = trang tạm) hoặc dock (slot `cell.x`).
     * KHÔNG lạc quan (như `addWidgetAt`): hàng mới chưa có id thật, DB phát lại bố cục ngay sau khi ghi. Bị từ chối thì chỉ ghi log (UI đã cho ảnh
     * nổi mờ đi / bay vào ô; DB phát lại bố cục cũ). Trang tạm bị bỏ cờ như mọi lần thả.
     */
    fun onLibraryAppDropped(app: AppItem, targetContainer: Long, targetPageId: Long?, cell: GridPosition) {
        _uiState.update { it.copy(draggingItemId = null, ghostPage = false) } // LD Task 4: phiên kéo (id giả) kết thúc
        viewModelScope.launch {
            try {
                if (!addAppAtUseCase(app.key, targetContainer, targetPageId, cell)) {
                    Log.w(TAG, "addAppAt rejected ${app.key.packageName} container=$targetContainer page=$targetPageId cell=$cell")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "addAppAt failed ${app.key.packageName}", e)
                _events.trySend(HomeEvent.MenuActionFailed)
            }
        }
    }

    fun onQuickHideTapped(itemId: Long, hide: Boolean = true) {
        val s = _uiState.value
        if (s.draggingItemId != null) return
        val key =
            s.pages.firstNotNullOfOrNull { page -> page.items.firstOrNull { it.itemId == itemId } }
                ?.let { (it as? PlacedApp)?.app?.key }
                ?: s.dock.firstOrNull { it.itemId == itemId }?.app?.key
                ?: return
        launchLayoutWrite(
            optimistic = {
                it.copy(
                    pages = removeAndCompact(it.pages, itemId),
                    dock = it.dock.filter { d -> d.itemId != itemId })
            },
            write = {
                if (hide) hideAppUseCase(key, key.userSerial) else removeFromHomeUseCase(key) // TF: hide=false = "Xóa khỏi MH chính"
                // EP Q5: ẩn ngoài edit mode (menu nhấn giữ) không có lần thoát edit mode nào sau đó, nên dọn trang trống ngay.
                if (!s.editMode) pruneEmptyPagesUseCase()
                true
            },
            failureLog = "hideApp failed id=$itemId"
        )
    }

    /**
     * G2c — xóa widget [itemId] (gọi SAU khi người dùng xác nhận ở hộp thoại, Task 9; `onQuickHideTapped` cố ý bỏ qua widget để không có
     * đường xóa mà không hỏi). Lạc quan: bỏ widget khỏi trang, KHÔNG dồn icon (ô trống để lại, cùng luật ẩn app). Trang trống: dọn ngay nếu ngoài edit mode, không thì lúc thoát edit mode (EP).
     * Không ghi `hidden_apps`, không đụng app nào.
     */
    /**
     * G2c Task 13 — thả widget kéo từ bảng chọn (CHƯA có hàng DB) xuống [cell] của trang [targetPageId] (`PageId.NEW` = trang tạm). Lạc quan:
     * thêm một [PlacedWidget] với id tạm [tempItemId] (chính id của phiên kéo, nên ô của nó ẩn tới khi ảnh bay hạ cánh xong) rồi dùng đúng
     * [planGridOptimistic] như kéo widget có sẵn để đẩy icon; ghi DB bằng [AddWidgetUseCase] (một transaction). Id tạm được thay bằng id
     * thật khi đối chiếu DB: lưới coi là "thay tại chỗ cùng ô" nên không thu rồi phình.
     */
    fun onNewWidgetDropped(
        kindId: String,
        spanX: Int,
        spanY: Int,
        tempItemId: Long,
        targetPageId: Long,
        cell: GridPosition
    ) {
        val kind = WidgetKind.fromId(kindId)?.takeIf { it.allows(spanX, spanY) }
        if (kind == null) {
            onDragCancelled()
            return
        }
        val state = _uiState.value
        val basePages = if (targetPageId == PageId.NEW) {
            state.pages + HomePage(state.pages.size, PageId.NEW, emptyList())
        } else {
            state.pages
        }
        val targetIndex = basePages.indexOfFirst { it.pageId == targetPageId }
        val temp = PlacedWidget(tempItemId, GridPosition(cell.x, cell.y, spanX, spanY), kind)
        val optimisticPages = if (targetIndex < 0) {
            null
        } else {
            val withTemp =
                basePages.mapIndexed { i, page -> if (i == targetIndex) page.copy(items = page.items + temp) else page }
            planGridOptimistic(withTemp, tempItemId, targetPageId, cell) { null }
                ?.map { page ->
                    page.copy(
                        items = page.items.sortedWith(
                            compareBy(
                                { it.position.y },
                                { it.position.x })
                        )
                    )
                }
        }
        launchLayoutWrite(
            optimistic = {
                it.copy(
                    pages = optimisticPages ?: it.pages,
                    draggingItemId = null,
                    ghostPage = false
                )
            },
            write = { addWidgetUseCase(kind, spanX, spanY, targetPageId, cell) },
            failureLog = "addWidgetAt failed kind=$kindId page=$targetPageId"
        )
    }

    /** G2c Task 11: bấm widget (ngoài edit mode) mở app Đồng hồ / Lịch của máy; máy không có app thì Toast chung. */
    fun onWidgetTapped(kind: WidgetKind) {
        val opened = when (kind) {
            WidgetKind.CLOCK -> appRepository.openClock()
            WidgetKind.CALENDAR -> appRepository.openCalendar()
            WidgetKind.WEATHER -> true // G3e: bấm widget thời tiết mở bảng Thời tiết, HomePagerController chặn trước khi tới đây
            WidgetKind.PHOTO -> true // G3b (ngoại lệ điều phối cho phép, 1 dòng): HomePagerController mở PhotoWidgetActivity trước khi tới đây
            WidgetKind.RECENTS -> true // WF: bấm ô app trong widget tự mở app; thẻ không có hành động riêng
            WidgetKind.BATTERY -> true // BW: HomeActivity mở màn dùng pin trước khi tới đây
        }
        if (!opened) _events.trySend(HomeEvent.MenuActionFailed)
    }

    fun onRemoveWidget(itemId: Long) {
        val s = _uiState.value
        if (s.draggingItemId != null) return
        if (s.pages.none { page -> page.items.any { it.itemId == itemId && it is PlacedWidgetBase } }) return
        launchLayoutWrite(
            optimistic = { st -> st.copy(pages = st.pages.map { page -> page.copy(items = page.items.filter { it.itemId != itemId }) }) },
            write = {
                removeWidgetUseCase(itemId)
                if (!s.editMode) pruneEmptyPagesUseCase() // EP: cùng luật Q5 khi xóa widget ngoài edit mode
                true
            },
            failureLog = "removeWidget failed id=$itemId"
        )
    }

    /**
     * G2c — thêm widget [kind] cỡ [spanX] x [spanY], tìm chỗ trống đầu tiên bắt đầu từ trang số [preferredPageIndex] (trang đang xem).
     * KHÔNG lạc quan: hàng mới chưa có id thật, id tạm khác id thật sẽ làm lưới thu rồi phình lại ô vừa thêm; DB phát lại rất nhanh. Xong
     * thì phát [HomeEvent.WidgetAdded] để pager cuộn tới trang đã đặt.
     */
    fun onAddWidget(kind: WidgetKind, spanX: Int, spanY: Int, preferredPageIndex: Int) {
        val preferred =
            _uiState.value.pages.getOrNull(preferredPageIndex)?.pageId?.takeIf { it > 0L }
        viewModelScope.launch {
            try {
                val pageId = addWidgetUseCase(kind, spanX, spanY, preferred)
                _events.trySend(if (pageId != null) HomeEvent.WidgetAdded(pageId) else HomeEvent.MenuActionFailed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "addWidget failed kind=${kind.id} ${spanX}x$spanY", e)
                _events.trySend(HomeEvent.MenuActionFailed)
            }
        }
    }

    /**
     * G3c Task 6 — ghi hàng cho widget app đã cấp id, gắn và cấu hình xong ([appWidgetId]): chỗ trống đầu tiên bắt đầu từ trang số [preferredPageIndex], trang mới nếu
     * hết chỗ. Phát [HomeEvent.WidgetAdded] để pager cuộn tới. Không lạc quan (như [onAddWidget]). Việc ghi chạy trong `NonCancellable` (ngắn): bị hủy giữa
     * chừng sau commit thì hàng đã có mà id lại bị giải phóng. Dù thành công hay không, id hết "đang trong luồng"; không ghi được thì giải phóng id (L-bảng).
     */
    fun onAddAppWidget(provider: AppWidgetRef, appWidgetId: Int, spanX: Int, spanY: Int, preferredPageIndex: Int) {
        val preferred = _uiState.value.pages.getOrNull(preferredPageIndex)?.pageId?.takeIf { it > 0L }
        viewModelScope.launch {
            var placed = false
            try {
                val pageId = withContext(NonCancellable) {
                    addAppWidgetUseCase(provider, appWidgetId, spanX, spanY, preferred).also { placed = it != null } // trong khối: hủy lúc trả về không làm mất cờ
                }
                _events.trySend(if (pageId != null) HomeEvent.WidgetAdded(pageId) else HomeEvent.AppWidgetAddFailed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "addAppWidget failed ${spanX}x$spanY ${e.javaClass.name}")
                _events.trySend(HomeEvent.AppWidgetAddFailed)
            } finally {
                appWidgetRepository.clearInFlight(appWidgetId)
                if (!placed) appWidgetRepository.release(listOf(appWidgetId))
            }
        }
    }

    private fun removeAndCompact(pages: List<HomePage>, itemId: Long): List<HomePage> =
        pages.map { page ->
            if (page.items.none { it.itemId == itemId }) return@map page
            val moves = GridRemovePlanner.plan(
                page.items.map {
                    GridInsertPlanner.Slot(
                        it.itemId,
                        it.position.x,
                        it.position.y,
                        it.position.spanX,
                        it.position.spanY
                    )
                },
                itemId,
                _uiState.value.grid.columns,
                _uiState.value.grid.rows
            ).associateBy { it.itemId }
            val items = page.items.filter { it.itemId != itemId }.map { placed ->
                moves[placed.itemId]?.let {
                    placed.withPosition(
                        placed.position.copy(
                            x = it.x,
                            y = it.y
                        )
                    )
                } ?: placed
            }
            page.copy(items = items.sortedWith(compareBy({ it.position.y }, { it.position.x })))
        }

    /**
     * Khuôn chung cho mọi thao tác ghi bố cục từ UI (thả icon, ẩn nhanh): phát NGAY trạng thái lạc quan [optimistic] rồi
     * mới ghi DB bằng [write] (trả false = DB từ chối). Trong lúc còn lệnh ghi ([pendingMoves] > 0) bỏ qua emission DB
     * (nhưng luôn nhớ bản mới nhất); lỗi/từ chối thì về lại DB; ghi xong thì [scheduleReconcile] đảm bảo UI khớp DB.
     */
    private fun launchLayoutWrite(
        optimistic: (HomeUiState) -> HomeUiState,
        write: suspend () -> Boolean,
        failureLog: String
    ) {
        pendingMoves++
        _uiState.update(optimistic)
        viewModelScope.launch {
            var needRepublish = false
            try {
                if (!write()) needRepublish = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, failureLog, e)
                needRepublish = true
            } finally {
                pendingMoves--
                if (needRepublish) publishFromDb()
                if (pendingMoves == 0) scheduleReconcile()
            }
        }
    }

    private fun gridStatusFor(pages: List<HomePage>): GridStatus = when {
        !settingsLoaded || !dbPagesReceived -> GridStatus.Loading
        pages.all { it.items.isEmpty() } -> GridStatus.Empty
        else -> GridStatus.Ready
    }

    private fun publishPages(pages: List<HomePage>) {
        val status = gridStatusFor(pages)
        _uiState.update { it.copy(pages = pages, gridStatus = status) }
    }

    private fun publishFromDb() {
        val pages = latestDbPages
        val status = gridStatusFor(pages)
        _uiState.update { it.copy(pages = pages, dock = latestDbDock, gridStatus = status) }
    }

    // Sau khi ghi xong, thường emission DB mới về ngay và tự publish. Nếu sau [RECONCILE_DELAY_MS] vẫn chưa
    // (hoặc emission đã về từ lúc còn pending nên bị bỏ qua) mà UI còn khác DB thì ép về DB.
    private fun scheduleReconcile() {
        reconcileJob?.cancel()
        reconcileJob = viewModelScope.launch {
            delay(RECONCILE_DELAY_MS)
            val s = _uiState.value
            if (pendingMoves == 0 && (s.pages != latestDbPages || s.dock != latestDbDock)) publishFromDb()
        }
    }

    private class OptimisticLayout(val pages: List<HomePage>, val dock: List<DockApp>)

    /**
     * Áp kế hoạch di chuyển lên pages + dock hiện tại (không đụng DB), 4 nhánh theo (nguồn, đích) = (lưới|dock) x
     * (lưới|dock). Trả null khi không tính được (item lạ, trang đích lạ, dock từ chối) -> UI chờ DB như cũ.
     * Dùng đúng [GridInsertPlanner]/[DockInsertPlanner] như `LayoutReconciler` nên kết quả khớp DB. Thứ tự item trong
     * trang sắp theo (y, x) đúng như `LauncherItemDao.observeAll` (ORDER BY pageId, gridY, gridX) để emission DB
     * xác nhận sau đó bằng đúng giá trị này và DiffUtil không phải rebind lần nữa. Trang mới (nếu cascade tạo)
     * dùng pageId tạm âm cho tới khi DB trả id thật.
     */
    private fun planOptimistic(
        pages: List<HomePage>,
        dock: List<DockApp>,
        itemId: Long,
        targetContainer: Long,
        targetPageId: Long?,
        cell: GridPosition
    ): OptimisticLayout? {
        val fromGrid =
            pages.firstNotNullOfOrNull { page -> page.items.firstOrNull { it.itemId == itemId } }
        val fromDock = dock.firstOrNull { it.itemId == itemId }
        // G2a A3b: nguồn là con của một thư mục (kéo ra khỏi panel). Bỏ nó khỏi thư mục TRƯỚC khi lập kế hoạch chèn: còn 1 app thì
        // app đó thay thư mục tại cùng ô, đúng như DB (dissolveFolderIfNeeded) nên vị trí chèn khớp.
        val fromFolder =
            if (fromGrid == null && fromDock == null) findChild(pages, dock, itemId) else null
        if (fromGrid == null && fromDock == null && fromFolder == null) return null
        val app = (fromGrid as? PlacedApp)?.app ?: fromDock?.app
        ?: fromFolder?.second // null = đang kéo thư mục (G2a A1b)
        val movingFolder = (fromGrid as? PlacedFolder) ?: fromDock?.folder
        val (basePages, baseDock) =
            if (fromFolder != null) detachChild(pages, dock, itemId) else pages to dock

        if (targetContainer == ContainerId.DOCK) {
            val entry = when {
                app != null -> DockApp(itemId, app)
                movingFolder != null -> DockApp(itemId, null, movingFolder)
                else -> return null // widget không vào dock
            }
            val order = DockInsertPlanner.plan(
                baseDock.map { it.itemId },
                itemId,
                cell.x,
                GridMetrics.DOCK_SLOTS
            ) ?: return null
            val byId = baseDock.associateBy { it.itemId } + (itemId to entry)
            val newPages = if (fromGrid != null) {
                basePages.map { page -> page.copy(items = page.items.filter { it.itemId != itemId }) } // ô cũ để trống, KHÔNG dồn
            } else {
                basePages
            }
            return OptimisticLayout(newPages, order.map { byId.getValue(it) })
        }

        if (targetPageId == null) return null
        val newPages = planGridOptimistic(basePages, itemId, targetPageId, cell) { pos ->
            app?.let { PlacedApp(itemId, pos, it) } ?: movingFolder?.withPosition(pos)
        } ?: return null
        val newDock =
            if (fromDock != null) baseDock.filter { it.itemId != itemId } else baseDock // dock KHÔNG tự lấp (Q10)
        return OptimisticLayout(newPages, newDock)
    }

    /** Thư mục [folderId] trên lưới hoặc trong dock. */
    private fun folderIn(pages: List<HomePage>, dock: List<DockApp>, folderId: Long): PlacedFolder? =
        pages.firstNotNullOfOrNull { page -> page.items.firstOrNull { it.itemId == folderId } as? PlacedFolder }
            ?: dock.firstOrNull { it.itemId == folderId }?.folder

    /**
     * Thay thư mục [folderId] (lưới hoặc dock) bằng kết quả [transform]; null = bỏ ô. Trong dock, kết quả là app thì ô thành ô app
     * (D9 còn 1 app).
     */
    private fun replaceFolder(
        pages: List<HomePage>,
        dock: List<DockApp>,
        folderId: Long,
        transform: (PlacedFolder) -> PlacedItem?
    ): Pair<List<HomePage>, List<DockApp>> {
        val newPages = pages.map { page ->
            if (page.items.none { it.itemId == folderId && it is PlacedFolder }) page
            else page.copy(items = page.items.mapNotNull { if (it.itemId == folderId && it is PlacedFolder) transform(it) else it })
        }
        val newDock = dock.mapNotNull { d ->
            val folder = d.folder
            if (d.itemId != folderId || folder == null) d
            else when (val r = transform(folder)) {
                is PlacedApp -> DockApp(r.itemId, r.app)
                is PlacedFolder -> DockApp(r.itemId, null, r)
                else -> null
            }
        }
        return newPages to newDock
    }

    /** G2a A3b: thư mục (lưới hoặc dock) chứa app con [childId] và chính app đó; null nếu không có. */
    private fun findChild(pages: List<HomePage>, dock: List<DockApp>, childId: Long): Pair<PlacedFolder, AppItem>? {
        val folders = pages.flatMap { it.items }.filterIsInstance<PlacedFolder>() + dock.mapNotNull { it.folder }
        for (folder in folders) {
            val idx = folder.childIds.indexOf(childId)
            if (idx >= 0) return folder to folder.previewApps[idx]
        }
        return null
    }

    /**
     * G2a A3b: bỏ app con [childId] khỏi thư mục của nó (lưới hoặc dock) và áp D9 cho UI lạc quan (còn 1 app -> app đó thay thư mục
     * tại cùng ô, hết -> bỏ thư mục, còn từ 2 -> giữ thư mục). Khớp `LayoutReconciler.dissolveFolderIfNeeded`.
     */
    private fun detachChild(
        pages: List<HomePage>,
        dock: List<DockApp>,
        childId: Long
    ): Pair<List<HomePage>, List<DockApp>> {
        val folder = findChild(pages, dock, childId)?.first ?: return pages to dock
        return replaceFolder(pages, dock, folder.itemId) { f ->
            val keep = f.childIds.indices.filter { f.childIds[it] != childId }
            when {
                keep.isEmpty() -> null
                keep.size == 1 -> PlacedApp(f.childIds[keep[0]], f.position, f.previewApps[keep[0]])
                else -> f.copy(
                    previewApps = keep.map { f.previewApps[it] },
                    childIds = keep.map { f.childIds[it] },
                    appCount = keep.size
                )
            }
        }
    }

    private fun planGridOptimistic(
        pages: List<HomePage>,
        itemId: Long,
        targetPageId: Long,
        cell: GridPosition,
        newPlaced: (GridPosition) -> PlacedItem?
    ): List<HomePage>? {
        val targetIndex = pages.indexOfFirst { it.pageId == targetPageId }
        if (targetIndex < 0) return null
        val placedById = HashMap<Long, PlacedItem>()
        pages.forEach { page -> page.items.forEach { placedById[it.itemId] = it } }

        val plan = GridInsertPlanner.plan(
            pages.map { page ->
                GridInsertPlanner.PageSlots(
                    page.pageId,
                    page.items.map {
                        GridInsertPlanner.Slot(
                            it.itemId,
                            it.position.x,
                            it.position.y,
                            it.position.spanX,
                            it.position.spanY
                        )
                    })
            },
            itemId,
            targetIndex,
            cell.x,
            cell.y,
            _uiState.value.grid.columns,
            _uiState.value.grid.rows
        )
        if (plan.rejected) return null // G2c: widget chồng widget khác -> DB cũng từ chối, UI không đổi rồi về DB
        if (plan.placements.isEmpty()) return pages

        val arrivalsByPage = plan.placements.groupBy { it.pageIndex }
        val movedIds = plan.placements.mapTo(HashSet()) { it.itemId }
        return (0 until pages.size + plan.newPages).map { i ->
            val old = pages.getOrNull(i)
            val kept = old?.items.orEmpty().filter { it.itemId !in movedIds }
            val arrived = arrivalsByPage[i].orEmpty().mapNotNull { pl ->
                // Item đến từ dock / thư mục không có trong placedById: dựng mới (1x1) từ app hoặc thư mục của chính nó.
                val src = placedById[pl.itemId]
                val pos = (src?.position ?: GridPosition(pl.x, pl.y)).copy(x = pl.x, y = pl.y)
                src?.withPosition(pos) ?: if (pl.itemId == itemId) newPlaced(GridPosition(pl.x, pl.y)) else null
            }
            HomePage(
                index = i,
                pageId = old?.pageId ?: -(i - pages.size + 1).toLong(),
                items = (kept + arrived).sortedWith(compareBy({ it.position.y }, { it.position.x }))
            )
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
        const val RECONCILE_DELAY_MS = 400L

        /** TV-2: số app của thẻ "app gần đây" trên Today View (2 hàng × 4, chủ dự án 2026-11-20). */
        const val TODAY_RECENT_APPS = 8
    }
}
