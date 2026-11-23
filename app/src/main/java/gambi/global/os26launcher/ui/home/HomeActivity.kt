package gambi.global.os26launcher.ui.home

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.ViewTreeObserver
import gambi.global.os26launcher.core.glass.GlassEnvironment
import gambi.global.os26launcher.core.glass.compose.LocalDsFrostedBackdrop
import gambi.global.os26launcher.core.glass.compose.rememberFrostedBackdrop
import gambi.global.os26launcher.domain.config.GeneralConfigRepository
import gambi.global.os26launcher.domain.model.IconStyleKey
import gambi.global.os26launcher.ui.home.controller.RecreateGuard
import gambi.global.os26launcher.ui.onboarding.OnboardingActivity
import gambi.global.os26launcher.ui.onboarding.OnboardingRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.ui.reassertEdgeToEdge
import gambi.global.os26launcher.data.search.WebSearchLauncher
import gambi.global.os26launcher.domain.config.HomeScreenConfig
import gambi.global.os26launcher.domain.config.HomeScreenConfigRepository
import gambi.global.os26launcher.domain.model.GridPosition
import gambi.global.os26launcher.domain.repository.DefaultLauncherRepository
import gambi.global.os26launcher.domain.repository.NotificationBadgeRepository
import gambi.global.os26launcher.domain.search.SearchMode
import gambi.global.os26launcher.ui.home.HomeEvent
import gambi.global.os26launcher.ui.home.HomeViewModel
import gambi.global.os26launcher.ui.home.LibraryOverlayController
import gambi.global.os26launcher.ui.home.compose.HomePagerInfo
import gambi.global.os26launcher.ui.home.compose.LocalHostedWidgets
import gambi.global.os26launcher.ui.widget.host.HostedWidgetViews
import gambi.global.os26launcher.core.appwidget.OsAppWidgetHost
import gambi.global.os26launcher.core.appwidget.AppWidgetAssets
import gambi.global.os26launcher.domain.repository.AppWidgetRepository
import gambi.global.os26launcher.domain.model.WidgetKind
import gambi.global.os26launcher.ui.widget.host.AppWidgetFlowController
import gambi.global.os26launcher.ui.widget.host.AppWidgetFlowState
import gambi.global.os26launcher.ui.weather.WeatherSheetController
import gambi.global.os26launcher.ui.home.compose.HomeWidgetPicker
import gambi.global.os26launcher.ui.widget.WeatherWidgetHub
import gambi.global.os26launcher.ui.photo.PhotoWidgetActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import gambi.global.os26launcher.ui.home.compose.HomeDragActions
import gambi.global.os26launcher.ui.home.compose.HomeIcons
import gambi.global.os26launcher.ui.home.compose.HomeScreen
import gambi.global.os26launcher.ui.home.compose.showRemoveWidgetDialog
import gambi.global.os26launcher.ui.home.compose.dismissRemoveDialog
import gambi.global.os26launcher.ui.home.controller.DefaultLauncherPromptController
import gambi.global.os26launcher.ui.home.controller.confirmQuickRemove
import gambi.global.os26launcher.ui.home.controller.confirmMenuUninstall
import gambi.global.os26launcher.ui.home.controller.confirmFolderRemove
import gambi.global.os26launcher.ui.home.IconMenuAction
import gambi.global.os26launcher.ui.home.compose.HomeOverlayActions
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.ScreenRect
import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt
import gambi.global.os26launcher.ui.home.compose.NotificationBadges
import gambi.global.os26launcher.ui.home.compose.library.HomeLibrary
import gambi.global.os26launcher.ui.home.compose.library.HomeLibraryState
import gambi.global.os26launcher.ui.home.compose.library.LibraryDragGesture
import gambi.global.os26launcher.ui.home.compose.today.HomeToday
import gambi.global.os26launcher.ui.home.compose.today.HomeTodayState
import gambi.global.os26launcher.ui.home.compose.today.TodayActions
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs
import gambi.global.os26launcher.ui.search.SearchEvent
import gambi.global.os26launcher.ui.search.compose.SearchActions
import gambi.global.os26launcher.ui.search.compose.SearchOverlay
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import gambi.global.os26launcher.ui.search.SearchViewModel
import gambi.global.os26launcher.ui.settings.SettingsActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * JH1 — CHỈ BẢN DEBUG. Home Compose chạy song song Home View (JH1 D1): cùng `HomeViewModel` thật, cùng ảnh nền, trang, dock. Không phải
 * launcher, không đổi gì của Home View. JH2: edit mode, kéo-thả. JH3a: huy hiệu thông báo, hỏi launcher mặc định / áp ảnh nền, Back và phím
 * Home theo thứ tự của Home View, lớp tìm kiếm (JS2: `SearchOverlay` Compose, trên cùng; trước đây `SearchOverlayView` thêm bằng `addContentView`, JH3a D1).
 *
 * Là `AppCompatActivity` (JH3a) vì `DefaultLauncherPromptController` cần nó.
 *
 * Mở: `adb shell am start -n gambi.global.os26launcher/gambi.global.os26launcher.debug.ComposeHomeActivity`.
 */
@AndroidEntryPoint
class HomeActivity : AppCompatActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()

    @Inject
    lateinit var homeScreenConfig: HomeScreenConfigRepository

    @Inject
    lateinit var notificationBadges: NotificationBadgeRepository

    @Inject
    lateinit var defaultLauncherRepository: DefaultLauncherRepository

    @Inject
    lateinit var appWidgetHost: OsAppWidgetHost

    @Inject
    lateinit var generalConfig: GeneralConfigRepository // PB Task 4: ẩn thanh điều hướng

    private lateinit var recreateGuard: RecreateGuard // JH4 D4: dựng lại khi kiểu icon / giao diện đổi ở Cài đặt
    private var navBarHidden = false

    @Inject
    lateinit var appWidgetFlowState: AppWidgetFlowState

    @Inject
    lateinit var appWidgetRepository: AppWidgetRepository

    @Inject
    lateinit var appWidgetAssets: AppWidgetAssets

    @Inject
    lateinit var getHiddenOnlyPackages: gambi.global.os26launcher.domain.usecase.GetHiddenOnlyPackagesUseCase // P3-7

    private lateinit var hostedWidgets: HostedWidgetViews
    private lateinit var appWidgetFlow: AppWidgetFlowController
    private lateinit var widgetPicker: HomeWidgetPicker // JS3: bảng chọn widget Compose
    private lateinit var weatherSheet: WeatherSheetController
    private val pageScrollRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    private lateinit var library: HomeLibraryState // JS4: App Library Compose
    private val libraryProgress = mutableFloatStateOf(0f)
    private lateinit var today: HomeTodayState // TV: màn widget bên trái trang 1
    private lateinit var libraryDrag: LibraryDragGesture // LD Task 3: nhấn giữ / kéo app trong Library

    /** LD Task 4: trạng thái kéo-thả của HomeScreen (HomeScreen báo lên khi dựng). */
    private var homeDrag by androidx.compose.runtime.mutableStateOf<gambi.global.os26launcher.ui.home.compose.HomeDragState?>(null)
    private val todayModel = gambi.global.os26launcher.ui.home.compose.today.TodayGridModel() // TV-3: thứ tự, kéo đổi chỗ, thêm/xóa của lưới Today
    private val icons by lazy { HomeIcons(viewModel::loadIcon) } // JH1 D7: dùng chung cho Home, Today và bảng chọn widget
    private val pagerInfo = HomePagerInfo()

    private var rootWidth = 0
    private var rootHeight = 0
    private var sizeReported = false
    private lateinit var defaultPrompt: DefaultLauncherPromptController
    private val searchProgress = mutableFloatStateOf(0f) // JS2: tiến độ mở lớp tìm kiếm (nhoè Home phía sau)
    private val searchActions by lazy {
        SearchActions(
            onQueryChanged = searchViewModel::onQueryChanged,
            onDismiss = searchViewModel::close,
            onCapacityChanged = searchViewModel::updateCapacity,
            onTapped = searchViewModel::onResultTapped,
            onContactCall = searchViewModel::onContactCall,
            onContactMessage = searchViewModel::onContactMessage,
            onPromptDismiss = searchViewModel::dismissContactsPrompt,
            onHeaderToggle = { searchViewModel.toggleRecentCollapsed() },
            canCallAndMessage = searchViewModel.canCallAndMessage,
            loadIcon = searchViewModel::loadIcon
        )
    }

    // G3a (như SearchOverlayController): xin quyền Danh bạ từ lớp tìm kiếm.
    private val contactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val permanent = !granted && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS)
        searchViewModel.onContactsPermissionResult(granted, permanent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Os26Launcher)
        super.onCreate(savedInstanceState)
        // Kiểu "dark" = icon thanh hệ thống TRẮNG bất kể sáng/tối (Home luôn trên ảnh nền, như iOS); mặc định `auto` cho icon tối ở chế độ sáng.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        defaultPrompt = DefaultLauncherPromptController(
            this, defaultLauncherRepository,
            isOnboardingDone = viewModel::isOnboardingDone,
            onDefaultChanged = viewModel::refreshDefaultLauncher
        )
        hostedWidgets = HostedWidgetViews(this, appWidgetHost)
        weatherSheet = WeatherSheetController(this) // G3e: dựng ở onCreate (xin quyền vị trí đăng ký trước STARTED)
        // G3c Task 6: dựng ở onCreate (ActivityResultLauncher trước STARTED); ghi hàng do ViewModel lo.
        appWidgetFlow = AppWidgetFlowController(
            this, appWidgetHost, appWidgetFlowState, appWidgetRepository,
            onCommit = { request, id ->
                viewModel.onAddAppWidget(request.provider, id, request.spanX, request.spanY, request.preferredPageIndex)
            },
            onMessage = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        )
        // JS3 + P2-9: bảng chọn widget Compose kiểu iOS (thay WidgetPickerController View, JH3d D1); kéo thẻ ra lưới chưa hỗ trợ (JH4 D5).
        widgetPicker = HomeWidgetPicker(
            this, viewModel, pagerInfo, appWidgetRepository, appWidgetAssets, icons, appWidgetFlow::start,
            hiddenOnlyPackages = { getHiddenOnlyPackages() } // P3-7: widget của app ẩn không lộ trong bảng chọn
        )
        library = HomeLibraryState(
            this, viewModel,
            homeState = { viewModel.uiState.value },
            isDragging = { viewModel.uiState.value.draggingItemId != null },
            isSearchOpen = { searchViewModel.uiState.value.isOpen },
            isPagerSettledOnLastPage = { pagerInfo.settledOnLastPage },
            onHomeProgress = { libraryProgress.floatValue = it },
            onSearchClick = { if (!searchViewModel.uiState.value.isOpen) searchViewModel.open(SearchMode.LIBRARY, showAll = true) },
            isTodayActive = { today.isActive },
            pillClaims = pagerInfo::pillClaims
        )
        today = HomeTodayState(
            this, viewModel,
            homeState = { viewModel.uiState.value },
            isSearchOpen = { searchViewModel.uiState.value.isOpen },
            isPagerSettledOnFirstPage = { pagerInfo.settledOnFirstPage },
            isLibraryActive = { library.isActive },
            pillClaims = pagerInfo::pillClaims // chạm trên pill dạng chấm không mở Today (như Library)
        )
        libraryDrag = createLibraryDrag() // LD Task 3
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val search by searchViewModel.uiState.collectAsStateWithLifecycle()
            val toggles by homeScreenConfig.config.collectAsStateWithLifecycle(initialValue = HomeScreenConfig())
            // BG (như NotificationBadgesController): cài đặt huy hiệu + số đếm.
            val todayApps by viewModel.todayApps.collectAsStateWithLifecycle() // TV-2
            // P3-7: widget trên Home của app đã ẩn: ẩn tạm (giữ chỗ, giữ cấu hình), bỏ ẩn là hiện lại.
            val hiddenWidgetPackages by remember { getHiddenOnlyPackages.observe() }.collectAsStateWithLifecycle(initialValue = emptySet())
            val badges by remember {
                combine(homeScreenConfig.config, notificationBadges.counts) { c, counts ->
                    NotificationBadges.State(c.badgesEnabled, c.badgeStyle, counts, c.badgeDisabledApps)
                }
            }.collectAsStateWithLifecycle(initialValue = NotificationBadges.State())

            // JH3a D4 — Back theo thứ tự HomeBackController (LIFO): đăng ký sau thì được hỏi trước. Gốc: chưa là launcher mặc định thì đóng.
            BackHandler(enabled = !defaultLauncherRepository.isDefaultLauncher()) { finish() }
            BackHandler(enabled = state.libraryOpen) { library.closeIfOpen() }
            BackHandler(enabled = state.libraryOpen && library.editing) { library.exitEditing() } // LD Task 2: Back thoát chế độ sửa trước khi đóng Library
            BackHandler(enabled = state.todayOpen) { today.closeIfOpen() } // TV: cùng lớp "nền" với Library (không mở chồng nhau)
            BackHandler(enabled = state.todayEditMode) { viewModel.exitTodayEditMode() } // TV-3: Back thoát chế độ sửa của Today trước khi đóng Today
            BackHandler(enabled = state.openCategoryGroup != null) { viewModel.closeCategoryGroup() }
            BackHandler(enabled = state.editMode) { viewModel.exitEditMode() }
            BackHandler(enabled = state.openFolderId != null) { viewModel.closeFolder() }
            BackHandler(enabled = state.iconMenu != null) { viewModel.closeIconMenu() }
            BackHandler(enabled = search.isOpen) { searchViewModel.close() }

            LaunchedEffect(state.editMode) { setStatusBarHidden(state.editMode) }
            LaunchedEffect(state) { library.render(state) }
            LaunchedEffect(state) { today.render(state) }
            LaunchedEffect(state.editMode, state.todayEditMode) {
                widgetPicker.onEditMode(state.editMode || state.todayEditMode) // G2c Task 9: thoát edit mode thì đóng bảng chọn (TV-3: cả chế độ sửa của Today)
            }
            LaunchedEffect(state.backdrop) {
                GlassEnvironment.from(this@HomeActivity).notifyBackdropChanged() // AN H1: kính View (Thư viện, bảng chọn) vẽ lại theo ảnh nền mới
            }
            LaunchedEffect(toggles.todayEnabled) { // TV (Q6): tắt thì đóng màn đang mở (render đọc `enabled`)
                today.enabled = toggles.todayEnabled
                if (!toggles.todayEnabled) today.closeIfOpen()
            }
            LaunchedEffect(toggles.libraryEnabled) {
                library.enabled = toggles.libraryEnabled
                if (!toggles.libraryEnabled) library.closeIfOpen() // LO D2
            }

            // PG3: lớp nền mờ tĩnh của ảnh nền (HomeScreen ghi lớp, ảnh kéo ở tầng Activity cũng đọc).
            val frosted = rememberFrostedBackdrop(state.backdrop)
            Box(Modifier.fillMaxSize()) {
            // JS2: lớp tìm kiếm mở thì Home + Library phía sau nhoè (API 31+), như ảnh chụp mờ của SearchOverlayView.
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val sp = searchProgress.floatValue
                    renderEffect = if (sp > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val r = SEARCH_BLUR_DP * density * sp
                        BlurEffect(r, r)
                    } else {
                        null
                    }
                }
            ) {
                CompositionLocalProvider(LocalHostedWidgets provides hostedWidgets) {
                HomeScreen(
                    state = state,
                    frosted = frosted,
                    icons = icons,
                    showSearch = toggles.searchPill,
                    showDots = toggles.pageDots,
                    actions = dragActions,
                    onAppClick = viewModel::onAppClicked,
                    onSearchClick = {
                        // M8c D3: edit mode / kéo-thả không mở.
                        val s = viewModel.uiState.value
                        if (!search.isOpen && !s.editMode && s.draggingItemId == null) searchViewModel.open(SearchMode.SPOTLIGHT)
                    },
                    onRemoveApp = { viewModel.confirmQuickRemove(this@HomeActivity, it) },
                    onRemoveWidget = ::confirmRemoveWidget,
                    // P2-9e: mục "Hình nền" của menu "Sửa" mở màn hình nền trong Cài đặt.
                    onWallpapers = {
                        startActivity(
                            Intent(this@HomeActivity, SettingsActivity::class.java)
                                .putExtra(SettingsActivity.EXTRA_SCREEN, gambi.global.os26launcher.ui.settings.SettingsScreenId.WALLPAPER.name)
                        )
                    },
                    badges = badges,
                    searchSwipeEnabled = toggles.swipeDownSearch && !search.isOpen && !state.todayOpen,
                    onSearchSwipe = { searchViewModel.open() },
                    overlays = overlays,
                    libraryProgress = { libraryProgress.floatValue },
                    todayProgress = { today.swipeProgress.floatValue },
                    pagerInfo = pagerInfo,
                    onAddWidget = { widgetPicker.show() },
                    onWidgetClick = ::onWidgetClick,
                    hiddenWidgetPackages = hiddenWidgetPackages,
                    pageScrollRequests = pageScrollRequests,
                    isNavBarHidden = { navBarHidden },
                    onDragState = { homeDrag = it }, // LD Task 4
                    modifier = Modifier.onSizeChanged { onRootSize(it.width, it.height) }
                )
                // TV Task 3: màn Today phía trên Home (trong LocalHostedWidgets để widget app vẽ được), trượt vào từ trái.
                HomeToday(
                    state = today,
                    model = todayModel,
                    apps = todayApps,
                    editing = state.todayEditMode,
                    backdrop = state.backdrop,
                    cells = if (today.swipeProgress.floatValue > 0f) todayCells(state) else null,
                    icons = icons,
                    actions = todayActions
                )
                }
                // JS4: App Library Compose phía trên Home (trước đây là View thêm bằng addContentView, JH3c).
                HomeLibrary(library, icons, state.backdrop)
                // LD Task 4: ảnh nổi kéo-thả trên cả Library (kéo app từ Library ra Home), toạ độ gốc = cửa sổ như HomeScreen.
                homeDrag?.let {
                    // PG3: ô thư mục đang kéo vẫn có kính mờ tĩnh.
                    CompositionLocalProvider(LocalDsFrostedBackdrop provides frosted.backdropOrNull) {
                        gambi.global.os26launcher.ui.home.compose.HomeDragLayer(it, it.cells, icons)
                    }
                }
            }
                SearchOverlay(search, searchActions, state.backdrop, onProgress = { searchProgress.floatValue = it })
                // PG2: bảng chọn widget là lớp phủ trong cửa sổ Home (không còn Dialog), trên Home / Today / Thư viện, để thẻ kéo được ra lưới.
                widgetPicker.Render()
            }
            dsDialogs.Render() // JS1: hộp thoại Compose (gợi ý launcher mặc định...)
        }
        reassertEdgeToEdge(window)
        if (savedInstanceState == null) startOnboardingIfNeeded() // OB (JH4 D4): không mở lại khi recreate()
        setupRecreateGuard()
        // PB Task 4: thu không gắn STARTED: đổi ở Cài đặt thì quay về Home đã đúng ngay khung đầu.
        lifecycleScope.launch {
            generalConfig.config.map { it.navBarHidden }.distinctUntilChanged().collect {
                navBarHidden = it
                applyNavBar()
            }
        }
        applyBarStyling()
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) searchViewModel.refreshContacts()
            if (event == Lifecycle.Event.ON_STOP) searchViewModel.onHostStopped() // C6
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.events.collect(::handleEvent) }
                launch { viewModel.categoryGroups.collect(library::onContent) }
                launch { viewModel.todayWidgets.collect(todayModel::setWidgets) } // TV-3
                launch { searchViewModel.events.collect(::handleSearchEvent) }
            }
        }
    }


    /** JH3b: menu nhấn giữ và bảng thư mục. Gỡ cài đặt (menu) và "−" trong thư mục hỏi trước như Home View (RemoveAppConfirm). */
    private val overlays by lazy {
        HomeOverlayActions(
            onIconMenuAction = { if (it == IconMenuAction.Uninstall) viewModel.confirmMenuUninstall(this) else viewModel.onIconMenuAction(it) },
            onIconMenuShortcut = viewModel::onIconMenuShortcut,
            closeIconMenu = viewModel::closeIconMenu,
            openFolder = viewModel::openFolder,
            closeFolder = viewModel::closeFolder,
            onRenameFolder = viewModel::renameFolder,
            onRemoveFolderApp = { viewModel.confirmFolderRemove(this, it) }
        )
    }

    /** JH2 D4: việc ghi của kéo-thả đi thẳng vào HomeViewModel (cập nhật lạc quan đã nằm trong VM). */
    private val dragActions = object : HomeDragActions {
        override fun enterEditMode() = viewModel.enterEditMode()
        override fun exitEditMode() = viewModel.exitEditMode()
        override fun onDragStarted(itemId: Long) = viewModel.onDragStarted(itemId)
        override fun onDragCancelled() = viewModel.onDragCancelled()
        override fun onLibraryAppDropped(app: gambi.global.os26launcher.domain.model.AppItem, targetContainer: Long, targetPageId: Long?, cell: GridPosition) =
            viewModel.onLibraryAppDropped(app, targetContainer, targetPageId, cell) // LD Task 4
        override fun onItemDropped(itemId: Long, targetContainer: Long, targetPageId: Long?, cell: GridPosition) =
            viewModel.onItemDropped(itemId, targetContainer, targetPageId, cell)

        override fun onFolderIntent(draggedId: Long, targetId: Long) = viewModel.onFolderIntent(draggedId, targetId)
        override fun onAddToFolder(itemId: Long, folderId: Long) = viewModel.onAddToFolder(itemId, folderId)
        override fun onDragEdgeBeyondLastPage(): Boolean = viewModel.onDragEdgeBeyondLastPage()
        override fun onGhostPageLeft() = viewModel.onGhostPageLeft()
        override fun onIconLongPressed(itemId: Long, container: Long, key: ComponentKey, anchor: Rect) = viewModel.onIconLongPressed(
            itemId, container, key,
            ScreenRect(anchor.left.roundToInt(), anchor.top.roundToInt(), anchor.right.roundToInt(), anchor.bottom.roundToInt()),
            resources.getDimensionPixelSize(R.dimen.shortcut_menu_icon_size)
        )

        override fun onMenuEditHome() = viewModel.onIconMenuAction(IconMenuAction.EditHome)
        override fun closeFolder() = viewModel.closeFolder()
        override fun onReorderFolder(folderId: Long, orderedChildIds: List<Long>) = viewModel.reorderFolder(folderId, orderedChildIds)
        override fun onNewWidgetDropped(kindId: String, spanX: Int, spanY: Int, tempItemId: Long, targetPageId: Long, cell: GridPosition) =
            viewModel.onNewWidgetDropped(kindId, spanX, spanY, tempItemId, targetPageId, cell) // PG2
    }

    /** Như `HomeActivity.handleEvent`. */
    private fun handleEvent(event: HomeEvent) {
        val message = when (event) {
            is HomeEvent.AppLaunchFailed -> getString(R.string.app_launch_failed, event.label)
            HomeEvent.MenuActionFailed -> getString(R.string.menu_action_failed)
            HomeEvent.AppWidgetAddFailed -> getString(R.string.aw_add_failed)
            is HomeEvent.WidgetAdded -> {
                pageScrollRequests.tryEmit(event.pageId) // JH3d D3
                return
            }
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /** Như `SearchOverlayController.handleEvent`. */
    private fun handleSearchEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.LaunchFailed -> Toast.makeText(this, getString(R.string.app_launch_failed, event.label), Toast.LENGTH_LONG).show()
            SearchEvent.ContactActionFailed -> Toast.makeText(this, R.string.search_contacts_action_failed, Toast.LENGTH_LONG).show()
            is SearchEvent.RequestContactsPermission -> requestContacts(event.askedBefore)
            is SearchEvent.LaunchWebSearch -> WebSearchLauncher.launchWebSearch(this, event.query)
        }
    }

    private fun requestContacts(askedBefore: Boolean) {
        val permanentlyDenied = askedBefore && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS)
        if (!permanentlyDenied) {
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
            return
        }
        searchViewModel.onContactsPermissionResult(granted = false, permanentlyDenied = true)
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } catch (e: ActivityNotFoundException) {
            handleSearchEvent(SearchEvent.ContactActionFailed)
        }
    }

    /** Dấu "−" trên widget: hỏi như `IconGridView.confirmRemoveWidget` rồi mới gỡ. */
    private fun confirmRemoveWidget(itemId: Long) = confirmRemove { viewModel.onRemoveWidget(itemId) }

    /** Hộp xác nhận gỡ widget, dùng chung cho lưới Home và màn Today (TV Task 4). UI sync U3: cùng kiểu thẻ iOS với hộp xóa app. */
    private fun confirmRemove(onConfirm: () -> Unit) = showRemoveWidgetDialog(this, onRemove = onConfirm)

    /** TV Task 4 — việc của màn Today. */
    private val todayActions by lazy {
        TodayActions(
            onSearchClick = { if (!searchViewModel.uiState.value.isOpen) searchViewModel.open(SearchMode.SPOTLIGHT) },
            onWidgetClick = ::onWidgetClick, // Thời tiết: bảng chọn địa điểm cho đúng widget (id trong dải Today); Ảnh: màn "Ảnh của tiện ích"
            onAppClick = viewModel::onAppClicked,
            onEnterEditMode = viewModel::enterTodayEditMode,
            onDone = viewModel::exitTodayEditMode,
            onAdd = { widgetPicker.show(forToday = true) },
            // Dấu "−": hỏi xác nhận (cùng thẻ iOS của widget trên Home, câu riêng của Today) rồi mới xóa.
            onRemove = { id -> showRemoveWidgetDialog(this, R.string.today_widget_remove_message) { viewModel.removeTodayWidget(id) } },
            onReorder = viewModel::reorderTodayWidgets
        )
    }

    /** Edit mode ẩn thanh trạng thái, vuốt thì hiện tạm (SystemBarsController 72-91). Inset "bỏ qua visibility" nên lưới không dịch. */
    private fun setStatusBarHidden(hidden: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    /** JH3a D4 — phím Home khi đang ở Home (HomeActivity.onNewIntent): đóng tìm kiếm, thoát edit mode. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // HB (2026-11-10): nút Home đóng cả menu nhấn giữ và bảng thư mục, như Back (quy tắc HB: mọi lớp tạm đóng ở cả Back và Home).
            viewModel.closeIconMenu()
            if (viewModel.uiState.value.openFolderId != null) viewModel.closeFolder()
            if (searchViewModel.uiState.value.isOpen) searchViewModel.close()
            viewModel.exitEditMode()
            library.closeIfOpen()
            today.closeIfOpen() // TV
            appWidgetFlow.abort() // G3c L3
            widgetPicker.closeIfOpen()
            weatherSheet.closeIfOpen()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDefaultLauncher()
        defaultPrompt.onResume() // PB Task 3: chưa là launcher mặc định → hỏi
        appWidgetFlow.onResume() // G3c D9: mở activity cấu hình đang chờ từ RESUMED
        recreateGuard.onResume() // JH4 D4
        applyBarStyling() // icon thanh hệ thống trắng: áp lại khi quay về (hệ thống/Activity khác có thể đã đổi)
        if (navBarHidden) applyNavBar() // PB Task 4
        // Cài đặt có thể vừa đổi ảnh nền (M8a Task 5, như BackdropController.onResume).
        if (sizeReported) viewModel.reloadBackdropIfChanged(rootWidth, rootHeight)
    }

    override fun onPause() {
        super.onPause()
        defaultPrompt.onPause()
    }

    /** Home View thoát edit mode ở onStop, trừ khi đang đổi cấu hình (HomeActivity 354). */
    override fun onStart() {
        super.onStart()
        // G3c D6: AppWidgetHost.startListening từ onStart (tài liệu); máy không có tính năng widget thì bỏ qua.
        try {
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)) appWidgetHost.startListening()
        } catch (e: RuntimeException) {
            android.util.Log.w("HomeActivity", "startListening failed ${e.javaClass.name}")
        }
    }

    override fun onStop() {
        uncoverAfterOnboarding() // OB: Onboarding đã che Home
        try {
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)) appWidgetHost.stopListening()
        } catch (e: RuntimeException) {
            android.util.Log.w("HomeActivity", "stopListening failed ${e.javaClass.name}")
        }
        super.onStop()
        if (!isChangingConfigurations && !appWidgetFlow.isActive) viewModel.exitEditMode() // JH3d D5
    }

    override fun onDestroy() {
        hostedWidgets.clear()
        widgetPicker.closeIfOpen()
        weatherSheet.onDestroy()
        defaultPrompt.onDestroy()
        dismissRemoveDialog()
        super.onDestroy()
    }

    /**
     * JH3c D2 — cử chỉ App Library nhận chạm TRƯỚC cây view (như `HomeTouchRouter`): TAKEOVER thì gửi một `ACTION_CANCEL` giả xuống (Compose
     * dọn nhấn giữ / pager) rồi nuốt; CONSUME thì nuốt. Lớp tìm kiếm đang mở thì không xét.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!::library.isInitialized || !::today.isInitialized || !::libraryDrag.isInitialized || searchViewModel.uiState.value.isOpen) return super.dispatchTouchEvent(ev)
        // TV: hai controller cùng xem mọi chạm (Home 1 trang thì cả hai là ứng viên lúc DOWN, chiều vuốt quyết bên nào). Library hỏi trước; bên nào
        // tiếp quản thì bên kia nhận một ACTION_CANCEL giả để bỏ ứng viên (canOpen của mỗi bên đã chặn mở chồng khi bên kia đang hiện).
        // LD Task 3: nhấn giữ app trong Library xét TRƯỚC (DOWN vẫn đi tiếp; đã giữ / đang kéo ra thì nuốt hết cử chỉ).
        if (libraryDrag.onTouch(ev) == LibraryDragGesture.Result.CONSUME) return true
        val libraryResult = library.onTouch(ev)
        if (libraryResult != LibraryOverlayController.Result.PASS) {
            if (libraryResult == LibraryOverlayController.Result.TAKEOVER) cancelInto(today::onTouch, ev)
            val handled = finishTakeover(libraryResult, ev)
            if (libraryResult == LibraryOverlayController.Result.TAKEOVER) settlePagerAfterTakeover(lastPage = true)
            return handled
        }
        val todayResult = today.onTouch(ev)
        if (todayResult == LibraryOverlayController.Result.TAKEOVER) cancelInto(library::onTouch, ev)
        val handled = finishTakeover(todayResult, ev)
        if (todayResult == LibraryOverlayController.Result.TAKEOVER) settlePagerAfterTakeover(lastPage = false)
        return handled
    }

    /**
     * PG1 G1 (FX7 D2/H1 + TV-1 của repo XML, `HomeTouchRouter` gọi `finishSettleOnLastPage/FirstPage`): Thư viện / Today vừa tiếp quản, có thể đúng
     * lúc pager còn trượt nốt. `ACTION_CANCEL` giả vừa gửi làm pager bám về trang GẦN NHẤT (có thể là trang áp chót): cho pager trượt nốt tới trang
     * thật cuối / đầu bên dưới lớp phủ, để đóng lớp phủ thì Home ở đúng trang. Đăng sau (post) để chạy sau khi Compose đã xử lý CANCEL; đã ở / đang về
     * đúng trang thì không làm gì (`HomePagerInfo.settleTo`).
     */
    private fun settlePagerAfterTakeover(lastPage: Boolean) {
        window.decorView.post {
            val count = pagerInfo.realPageCount
            if (count <= 0) return@post
            pagerInfo.settleTo?.invoke(if (lastPage) count - 1 else 0)
        }
    }

    /** PASS: cây view xử lý; TAKEOVER: gửi một `ACTION_CANCEL` giả xuống (Compose dọn nhấn giữ / pager) rồi nuốt; CONSUME: nuốt. */
    private fun finishTakeover(result: LibraryOverlayController.Result, ev: MotionEvent): Boolean = when (result) {
        LibraryOverlayController.Result.PASS -> super.dispatchTouchEvent(ev)
        LibraryOverlayController.Result.TAKEOVER -> {
            val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            true
        }
        LibraryOverlayController.Result.CONSUME -> true
    }

    /**
     * LD Task 3 — giữ yên trên app của Library: rung, vào chế độ sửa, rồi huỷ chạm đang dở ở cây view (cú bấm không thành, danh sách không cuộn) và ở
     * hai controller vuốt. Gọi từ Handler (ngoài `dispatchTouchEvent`) nên gửi thẳng `window.superDispatchTouchEvent`.
     */
    private fun createLibraryDrag() = LibraryDragGesture(
        library,
        object : LibraryDragGesture.Host {
            override fun onHold(app: gambi.global.os26launcher.domain.model.AppItem, down: MotionEvent) {
                window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                library.enterEditing()
                val cancel = MotionEvent.obtain(down).apply { action = MotionEvent.ACTION_CANCEL }
                window.superDispatchTouchEvent(cancel)
                library.onTouch(cancel)
                today.onTouch(cancel)
                cancel.recycle()
            }

            // LD Task 4: bàn giao phiên kéo sang Home. startExternal bật draggingItemId + edit mode → render đóng Library (và thoát chế độ sửa của
            // Library); từ đây mọi MOVE/UP của cử chỉ đi thẳng vào HomeDragState (toạ độ cửa sổ = toạ độ gốc HomeScreen, cả hai tràn viền).
            override fun onDragOut(app: gambi.global.os26launcher.domain.model.AppItem, icon: android.graphics.RectF, x: Float, y: Float) {
                val drag = homeDrag ?: return
                drag.startExternal(app, Offset(x, y), androidx.compose.ui.geometry.Rect(icon.left, icon.top, icon.right, icon.bottom))
            }

            override fun onDragMove(x: Float, y: Float) {
                homeDrag?.move(Offset(x, y))
            }

            override fun onDragEnd(x: Float, y: Float, cancelled: Boolean) {
                val drag = homeDrag ?: return
                if (cancelled) drag.cancel() else drag.drop()
            }
        },
        android.view.ViewConfiguration.get(this),
        resources.displayMetrics.density
    )

    /** Gửi bản `ACTION_CANCEL` của [ev] cho controller [target] (bỏ ứng viên của nó). */
    private fun cancelInto(target: (MotionEvent) -> LibraryOverlayController.Result, ev: MotionEvent) {
        val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
        target(cancel)
        cancel.recycle()
    }

    /** JH4 D4 — như HomeActivity (View): kiểu icon đổi hoặc giao diện đổi khi Home đang hiện → dựng lại một lần (RecreateGuard). */
    private fun setupRecreateGuard() {
        recreateGuard = RecreateGuard(lifecycleScope, ::recreate)
        var appliedIconStyle: IconStyleKey? = null
        lifecycleScope.launch { appliedIconStyle = viewModel.currentIconStyleKey() }
        recreateGuard.add { appliedIconStyle?.let { viewModel.currentIconStyleKey() != it } ?: false }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.appearanceChanges.collect { recreateGuard.onResume() }
            }
        }
    }

    /** PB Task 4: công tắc "Ẩn thanh điều hướng"; vuốt từ mép hiện tạm. Inset "bỏ qua visibility" nên dock không dịch. */
    private fun applyNavBar() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (navBarHidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.navigationBars())
        } else {
            controller.show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && navBarHidden) applyNavBar() // PB Task 4: hộp thoại / bảng hệ thống đóng có thể đã hiện lại thanh
    }

    /** JH4 D4: gốc nội dung (ComposeView và các View thêm bằng addContentView), che lại trong lúc chờ Onboarding như `binding.root` của Home View. */
    private val contentRoot: View get() = findViewById(android.R.id.content)

    /**
     * OB: lần dựng đầu (không phải recreate) mà chưa xem Onboarding thì mở nó.
     *
     * Trong lúc đọc cờ, Home vẽ một khung TỐI TRỐNG (nội dung INVISIBLE, nền cửa sổ `ds_bg_base`) thay vì không vẽ gì: Home phải vẽ
     * khung đầu để hệ thống gỡ splash của chính nó. Nếu Home chưa vẽ mà đã mở Onboarding cùng task, hệ thống chuyển splash sang
     * Onboarding rồi không gỡ (đo Live 4 2026-11-17: `startingMoved=true`, `STARTING_WINDOW_SHOWN` dù đã vẽ) → splash đè vĩnh viễn.
     * Không cần Onboarding thì hiện nội dung ngay; cần thì hiện lại khi Home đã bị che (onStop). Quá [ONBOARDING_HOLD_MAX_MS] thì hiện.
     *
     * Đặt mặc định ở 0.4: PermissionController gửi HOME, hệ thống dựng Home MỚI trong task Home (kết quả không về Onboarding cũ).
     * Home mới thấy [OnboardingRuntime.consumePendingDefaultLauncher] thì mở Onboarding thẳng ở 0.5, và dọn task cũ (Home cũ +
     * Onboarding ở 0.4) để Đa nhiệm không còn bản Home cũ.
     */
    private fun startOnboardingIfNeeded() {
        val launchedAsHome = intent?.hasCategory(Intent.CATEGORY_HOME) == true
        if (launchedAsHome) removeStaleAppTasks()
        coverForOnboarding()
        contentRoot.postDelayed({ uncoverAfterOnboarding() }, ONBOARDING_HOLD_MAX_MS)
        lifecycleScope.launch {
            val done = try {
                withTimeoutOrNull(ONBOARDING_HOLD_MAX_MS) { viewModel.isOnboardingDone() } ?: true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("HomeActivity", "read onboarding flag failed ${e.javaClass.name}")
                true
            }
            val atDone = !done && OnboardingRuntime.consumePendingDefaultLauncher()
            if (done) {
                uncoverAfterOnboarding()
            } else {
                // Đọc cờ thường xong TRƯỚC khung đầu (không phải chờ): mở Onboarding sau khi Home đã vẽ khung tối, xem KDoc.
                afterFirstFrame {
                    defaultPrompt.skipNextResume() // PB D1: Onboarding vừa hỏi ở 0.4, lần về Home đầu không hỏi lại
                    startActivity(OnboardingActivity.intent(this@HomeActivity, startAtDone = atDone))
                }
            }
        }
    }

    /**
     * OB: chạy [action] một lần, sau khi cửa sổ Home đã vẽ khung đầu (cộng [FIRST_FRAME_SETTLE_MS] để hệ thống kịp nhận "đã vẽ" và gỡ
     * splash). Activity đã hủy thì bỏ.
     */
    private fun afterFirstFrame(action: () -> Unit) {
        val decor = window.decorView
        var fired = false
        val listener = object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                if (fired) return
                fired = true
                val self = this
                decor.post {
                    decor.viewTreeObserver.removeOnDrawListener(self) // không gỡ được ngay trong onDraw
                    decor.postDelayed({ if (!isDestroyed) action() }, FIRST_FRAME_SETTLE_MS)
                }
            }
        }
        decor.viewTreeObserver.addOnDrawListener(listener)
        decor.invalidate()
    }

    private var onboardingCovered = false

    private fun coverForOnboarding() {
        onboardingCovered = true
        window.setBackgroundDrawable(ColorDrawable(getColor(R.color.ds_bg_base)))
        contentRoot.visibility = View.INVISIBLE
    }

    private fun uncoverAfterOnboarding() {
        if (!onboardingCovered) return
        onboardingCovered = false
        contentRoot.visibility = View.VISIBLE
    }

    /**
     * OB: launcher chỉ nên có một task Home; task khác của app (Home cũ mở từ icon trước khi thành mặc định) thì dọn.
     * HC Task 2: chỉ dọn task có gốc là Home (bản Home cũ) hoặc đang có Onboarding trên cùng (Onboarding ở 0.4 mở từ Cài đặt); task Cài
     * đặt bình thường (mở từ icon app) giữ nguyên.
     */
    private fun removeStaleAppTasks() {
        val am = getSystemService(ActivityManager::class.java) ?: return
        val home = HomeActivity::class.java.name
        val onboarding = OnboardingActivity::class.java.name
        for (task in am.appTasks) {
            try {
                val info = task.taskInfo ?: continue
                val id = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.taskId else @Suppress("DEPRECATION") info.persistentId
                val stale = info.baseActivity?.className == home || info.topActivity?.className == onboarding
                if (id != taskId && stale) {
                    task.finishAndRemoveTask()
                }
            } catch (e: IllegalArgumentException) {
                // Task đã mất giữa lúc liệt kê: bỏ qua
            }
        }
    }

    /** Như `SystemBarsController.applySystemBarStyling`: thanh trong suốt (không lớp tương phản xám), màu icon theo sáng/tối. */
    private fun applyBarStyling() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        // Như iOS: Home luôn nằm trên ảnh nền (nhãn icon trắng) nên icon thanh trạng thái/điều hướng luôn TRẮNG, không theo sáng/tối.
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    @Deprecated("AppWidgetFlowController dùng startActivityForResult (như HomeActivity).")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        appWidgetFlow.onActivityResult(requestCode, resultCode, data)
    }

    /** TV-1: cỡ ô của lưới Home ép 4 cột (bố cục Today là 4 cột cố định), đo trên kích thước pager như dock (TodayController.updateCells). */
    private fun todayCells(state: HomeUiState): gambi.global.os26launcher.core.ui.GridMetrics.Cells? =
        gambi.global.os26launcher.core.ui.GridMetrics.from(state.grid, state.showLabels).copy(columns = TODAY_COLUMNS)
            .measure(pagerInfo.pagerWidth, pagerInfo.pagerHeight, resources.displayMetrics.density)

    /** JH3d D4 — bấm widget tự vẽ ngoài edit mode, như `HomePagerController.onWidgetClick`. */
    private fun onWidgetClick(kind: WidgetKind, itemId: Long) {
        when (kind) {
            WidgetKind.WEATHER -> {
                WeatherWidgetHub.tappedWidgetId = itemId
                weatherSheet.show()
            }
            WidgetKind.PHOTO -> startActivity(PhotoWidgetActivity.intent(this, itemId))
            else -> viewModel.onWidgetTapped(kind)
        }
    }

    /** Như `HomeActivity`: lần có cỡ đầu → `onScreenSize` (giải mã ảnh nền theo cỡ này); các lần sau → `onWindowSizeSettled`. */
    private fun onRootSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        rootWidth = width
        rootHeight = height
        if (!sizeReported) {
            sizeReported = true
            viewModel.onScreenSize(width, height)
        } else {
            if (viewModel.uiState.value.iconMenu != null) viewModel.closeIconMenu() // RR: vị trí menu tính lúc mở
            viewModel.onWindowSizeSettled(width, height, isInMultiWindowMode)
        }
    }

    companion object {
        private const val SEARCH_BLUR_DP = 24f // JS2: bán kính nhoè Home khi lớp tìm kiếm mở hẳn
        private const val TODAY_COLUMNS = 4 // TV-1: lưới Today luôn 4 cột

        /** OB: che nội dung Home (khung tối) tối đa chừng này (ms) khi chờ đọc cờ / mở Onboarding. */
        private const val ONBOARDING_HOLD_MAX_MS = 1500L

        /** OB: chờ thêm sau khung đầu của Home trước khi mở Onboarding (để splash được gỡ, không bị chuyển sang Onboarding). */
        private const val FIRST_FRAME_SETTLE_MS = 50L
    }
}
