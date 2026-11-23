package gambi.global.os26launcher.ui.home.compose

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.domain.model.AppShortcutItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.PlacedApp
import gambi.global.os26launcher.domain.model.PlacedFolder
import gambi.global.os26launcher.domain.model.PlacedWidgetBase
import gambi.global.os26launcher.ui.home.IconMenuAction
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.glass.BlurredBackdropCache
import gambi.global.os26launcher.core.perf.PerfSwitches
import gambi.global.os26launcher.core.glass.GlassTuning
import gambi.global.os26launcher.core.glass.compose.FrostedBackdrop
import gambi.global.os26launcher.core.glass.compose.FrostedWallpaperLayer
import gambi.global.os26launcher.core.glass.compose.rememberGlassLook
import gambi.global.os26launcher.core.glass.compose.rememberLayerSnapshot
import gambi.global.os26launcher.core.glass.compose.LocalDsBackdrop
import gambi.global.os26launcher.core.glass.compose.LocalDsFrostedBackdrop
import gambi.global.os26launcher.core.glass.compose.rememberFrostedBackdrop
import gambi.global.os26launcher.core.ui.GridMetrics
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.HomePage
import gambi.global.os26launcher.domain.model.PageId
import gambi.global.os26launcher.domain.model.ScreenRect
import gambi.global.os26launcher.ui.home.GridStatus
import gambi.global.os26launcher.ui.home.HomeUiState
import gambi.global.os26launcher.ui.home.PageMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import gambi.global.os26launcher.domain.model.WidgetKind
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * JH1 Task 2 — Home Compose: ảnh nền, các trang lưới, pill, dock. Bố cục như `activity_home.xml`: cột dọc trong vùng trừ thanh hệ thống
 * (inset "bỏ qua visibility" như `SystemBarsController`, để ẩn/hiện thanh không đẩy dock): pager trọng số 6, pill 48 dp, dock trọng số 1 lề 8 dp.
 *
 * Kính: lớp `layerBackdrop` CHỈ là ảnh nền (đặt trước phần vẽ của `Image`, bài học JC Task 2), như kính View chỉ lấy mẫu `backdrop_image`. Widget
 * nằm trong pager đọc lớp này mà không tự ghi chính nó, và vị trí lấy mẫu đi theo widget khi lướt.
 *
 * Cỡ ô tính MỘT lần từ vùng pager ([GridMetrics.measure]) cho cả lưới và dock (dock ép không nhãn), như `DockController.updateDockCells`.
 *
 * JH2: cử chỉ nhấn giữ / kéo ở gốc ([homeGestures]), trạng thái kéo [HomeDragState], ảnh nổi [HomeDragLayer] trên cùng, thanh nút edit mode ở
 * đỉnh, trang tạm (`ghostPage`) nối vào cuối danh sách trang khi đang kéo.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class) // systemBarsIgnoringVisibility: inset giữ nguyên khi thanh ẩn/hiện (như SystemBarsController)
internal fun HomeScreen(
    state: HomeUiState,
    /** PG3: lớp nền mờ tĩnh của ảnh nền ([rememberFrostedBackdrop] ở `HomeActivity`, dùng chung với ảnh kéo ở tầng Activity). */
    frosted: FrostedBackdrop,
    icons: HomeIcons,
    showSearch: Boolean,
    showDots: Boolean,
    actions: HomeDragActions,
    onAppClick: (AppItem, ScreenRect?) -> Unit,
    onSearchClick: () -> Unit,
    onRemoveApp: (Long) -> Unit,
    onRemoveWidget: (Long) -> Unit,
    /** P2-9e: mục "Hình nền" của menu "Sửa" (mở màn hình nền trong Cài đặt). */
    onWallpapers: () -> Unit,
    badges: NotificationBadges.State = NotificationBadges.State(),
    /** JH3a D3: vuốt xuống mở tìm kiếm được không (công tắc + tìm kiếm chưa mở); edit mode, đang kéo, lưới chưa sẵn thì HomeScreen tự chặn. */
    searchSwipeEnabled: Boolean = false,
    onSearchSwipe: () -> Unit = {},
    overlays: HomeOverlayActions = HomeOverlayActions(),
    /** JH3c: tiến độ mở App Library (0 = đóng, 1 = mở), đọc lúc vẽ nên không dựng lại Home mỗi khung kéo. */
    libraryProgress: () -> Float = { 0f },
    /** TV: tiến độ mở màn Today (0 = đóng, 1 = mở), đọc lúc vẽ: lớp Home trượt sang PHẢI (ngược Library). */
    todayProgress: () -> Float = { 0f },
    /** JH3c: trang hiện tại cho cử chỉ App Library của Activity (chỉ mở ở trang thật cuối, đứng yên). */
    pagerInfo: HomePagerInfo = HomePagerInfo(),
    /** JH3d + P2-9e: mục "Thêm tiện ích" của menu "Sửa" (null = không có mục), bấm widget ngoài edit mode, và yêu cầu lật tới trang (pageId) khi widget vừa thêm. */
    onAddWidget: (() -> Unit)? = null,
    onWidgetClick: (WidgetKind, Long) -> Unit = { _, _ -> },
    /** P3-7: gói (packageName, userSerial) chỉ còn app đã ẩn: widget app của chúng trên Home ẩn tạm (giữ chỗ). */
    hiddenWidgetPackages: Set<Pair<String, Long>> = emptySet(),
    pageScrollRequests: Flow<Long> = emptyFlow(),
    /** P2-5: người dùng đã ẩn thanh điều hướng (PB Task 4) → dải đáy màn cũng nhấn giữ vào edit mode được. Đọc lúc chạm xuống. */
    isNavBarHidden: () -> Boolean = { false },
    /** LD Task 4: trạng thái kéo-thả cho Activity (kéo app từ Library sang; ảnh nổi vẽ ở Activity, trên lớp Library). */
    onDragState: (HomeDragState) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pagerSize by remember { mutableStateOf(IntSize.Zero) }
    // 2026-11-23 (chủ dự án, so trực tiếp với iOS): lề TRÊN của lưới icon tính từ MÉP TRÊN MÀN HÌNH (không phải từ đỉnh pager, đã bị thanh
    // trạng thái/editToolbarClearancePx đẩy xuống [gridTopPx] dòng 344-345) = 1.7 × chiều cao thanh trạng thái. Pager đã bắt đầu ở [gridTopPx]
    // từ mép màn, nên phần padding CỘNG THÊM bên trong GridMetrics chỉ còn: max(0, 1.7×statusBar − gridTopPx).
    val gridStatusBarPx = WindowInsets.systemBarsIgnoringVisibility.getTop(density)
    val gridTopPx = maxOf(gridStatusBarPx, editToolbarClearancePx(density))
    val gridTopPaddingOverridePx = (1.7f * gridStatusBarPx - gridTopPx).coerceAtLeast(0f).toInt()
    val cells = remember(pagerSize, state.grid, state.showLabels, density.density, gridTopPaddingOverridePx) {
        GridMetrics.from(state.grid, state.showLabels).measure(pagerSize.width, pagerSize.height, density.density, gridTopPaddingOverridePx)
    }
    // 2026-11-24 (chủ dự án): thanh "Sửa"/"Xong" của edit mode căn GIỮA theo chiều dọc trong khoảng mép trên màn hình → mép trên hàng icon đầu
    // tiên (= [gridTopPx] + cells.paddingTop, tuyệt đối từ mép màn), không còn sát mép trên như trước.
    val editToolbarPillHeightPx = dimensionResource(R.dimen.home_edit_pill_touch).let { with(density) { it.toPx() } }
    val editToolbarTopPaddingPx = run {
        val firstRowTopPx = gridTopPx + (cells?.paddingTop ?: 0)
        maxOf(0f, firstRowTopPx / 2f - editToolbarPillHeightPx / 2f)
    }
    // Trang tạm ở cuối khi đang kéo (M7 Task 7): HomePagerController.renderPages nối HomePage(size, PageId.NEW, rỗng).
    val pages = remember(state.pages, state.ghostPage) {
        // PG4: công tắc đo `noWidgets` (chỉ bản `perf`) bỏ widget khỏi phần vẽ.
        val shown = if (PerfSwitches.noWidgets) state.pages.map { p -> p.copy(items = p.items.filterNot { it is PlacedWidgetBase }) } else state.pages
        if (state.ghostPage) shown + HomePage(shown.size, PageId.NEW, emptyList()) else shown
    }
    val pagerState = rememberPagerState { pages.size }
    val drag = remember { HomeDragState(scope, density.density) }
    LaunchedEffect(drag) { onDragState(drag) }
    var editMenuOpen by remember { mutableStateOf(false) } // P2-9e: menu "Sửa" của thanh edit mode
    val barInsets = WindowInsets.systemBarsIgnoringVisibility
    // PG1 G1: cổng mở Thư viện / Today đọc thẳng PagerState lúc chạm xuống (targetPage và "tay đang kéo" đổi liên tục khi cuộn, chép trong
    // SideEffect thì có thể cũ đúng lúc lướt nhanh). Trượt nốt sau khi lớp phủ tiếp quản chạy trong scope của màn này.
    val pagerDragged = pagerState.interactionSource.collectIsDraggedAsState()
    DisposableEffect(pagerState) {
        pagerInfo.pagerState = pagerState
        pagerInfo.pagerDragged = pagerDragged
        pagerInfo.drag = drag // PG2: bảng chọn widget (lớp phủ ngoài HomeScreen) lái phiên kéo widget mới
        pagerInfo.settleTo = { page ->
            scope.launch {
                val busy = pagerState.isScrollInProgress
                // Đang trượt đúng về trang đó, hoặc đã đứng yên ở đó: không làm gì (không cắt cú bám trang đang chạy).
                if ((busy && pagerState.targetPage == page) ||
                    (!busy && pagerState.currentPage == page && abs(pagerState.currentPageOffsetFraction) < SETTLED_FRACTION)
                ) return@launch
                if (page in 0 until pagerState.pageCount) {
                    pagerState.animateScrollToPage(page, animationSpec = tween(PageMotion.FULL_MS, easing = EaseOutQuint))
                }
            }
        }
        onDispose {
            if (pagerInfo.pagerState === pagerState) {
                pagerInfo.pagerState = null
                pagerInfo.pagerDragged = null
                pagerInfo.settleTo = null
                pagerInfo.drag = null
            }
        }
    }
    SideEffect {
        pagerInfo.currentPage = pagerState.currentPage
        pagerInfo.scrolling = pagerState.isScrollInProgress
        pagerInfo.realPageCount = state.pages.size
        drag.actions = actions
        drag.pagerState = pagerState
        drag.cells = cells
        drag.pages = pages
        drag.realPageCount = state.pages.size
        drag.dock = state.dock
        drag.navBarHidden = isNavBarHidden
        drag.statusBarTop = barInsets.getTop(density).toFloat()
        drag.navBarBottom = barInsets.getBottom(density).toFloat()
        drag.pillVisible = showSearch || showDots
        if (!state.editMode || drag.session != null) drag.toolbarRects.clear()
    }
    val edit = HomeEditContext(state.editMode, drag, onRemoveApp, onRemoveWidget, badges, onOpenFolder = overlays.openFolder, onWidgetClick = onWidgetClick,
        hiddenWidgetPackages = hiddenWidgetPackages)
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val menuProgress = remember { Animatable(0f) }
    val folderProgress = remember { Animatable(0f) }
    // RA3 (2026-11-24): thẻ xác nhận xóa app blur + tối lớp Home phía sau Y HỆT lúc mở thư mục (không chỉ tối như menu nhấn giữ icon) —
    // removeOverlayProgress (RemoveAppDialog.kt) góp vào overlayP/blur dưới đây cùng folderProgress.
    val overlayP = maxOf(menuProgress.value, folderProgress.value, removeOverlayProgress)
    val homeBackdrop = rememberLayerBackdrop()
    // 2026-11-23: menu nhấn giữ cũng lấy mẫu lớp Home (thấy icon phía sau nhoè qua menu, như iOS 26), không chỉ ảnh nền.
    val folderLayerOn = state.openFolderId != null || folderProgress.value > 0f || state.iconMenu != null || menuProgress.value > 0f ||
        removeOverlayProgress > 0f
    // PG4: công tắc đo (chỉ bản `perf`, `PerfSwitches`): bỏ nền của kính thật / lớp nền mờ tĩnh để so số đo.
    val glassBackdrop = if (PerfSwitches.noRealGlass) null else backdrop
    val frostedBackdrop = if (PerfSwitches.noFrosted) null else frosted.backdropOrNull
    // PG3 Task 2 Step 3 (Q3): dưới API 31 kính thật không chạy, nên bảng thư mục lấy mẫu ẢNH CHỤP lớp Home (chụp một lần lúc mở, làm mờ ngoài
    // main thread bằng bộ đệm riêng) để vẫn thấy icon phía sau; ảnh chụp chưa xong thì dùng lớp nền mờ của ảnh nền. Máy ≥ 31 không chụp.
    val legacyGlass = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    val folderSnapshot = rememberLayerSnapshot(homeBackdrop, capture = legacyGlass && state.openFolderId != null, keep = legacyGlass && folderLayerOn)
    val folderFrosted = rememberFrostedBackdrop(folderSnapshot)
    val folderBlurCache = remember { BlurredBackdropCache() }
    val folderLook by rememberGlassLook()
    SideEffect { folderBlurCache.blurRadiusPx = maxOf(GlassTuning.blurRadiusPx(folderLook), FOLDER_SNAPSHOT_MIN_BLUR_PX) }
    LaunchedEffect(folderSnapshot) { if (folderSnapshot == null) folderBlurCache.trim() } // đóng thư mục: nhả bản mờ của ảnh chụp
    // App của menu: tìm theo key trong trang, dock, thư mục (menu của app con trong thư mục có itemId = -1).
    val menuApp = state.iconMenu?.let { m -> findApp(state, m.key) }
    val openFolder = state.openFolderId?.let { id ->
        state.pages.firstNotNullOfOrNull { p -> p.items.firstOrNull { it.itemId == id } as? PlacedFolder }
            ?: state.dock.firstOrNull { it.itemId == id }?.folder
    }
    val folderOrigin = openFolder?.let { f ->
        pages.getOrNull(pagerState.currentPage)?.items?.firstOrNull { it.itemId == f.itemId }?.let { drag.iconRectOf(it.position) }
            ?: drag.dockIconRectOf(f.itemId)
    }
    SideEffect { drag.menuOwner = state.iconMenu?.let { it.itemId to it.container } }
    val currentState by rememberUpdatedState(state)
    val swipeEnabled by rememberUpdatedState(searchSwipeEnabled)
    val onSwipe by rememberUpdatedState(onSearchSwipe)
    val statusTopPx = WindowInsets.systemBarsIgnoringVisibility.getTop(density)
    val searchSwipe = remember(drag) {
        HomeSearchSwipe(
            enabled = {
                val s = currentState
                swipeEnabled && !s.editMode && drag.session == null && s.gridStatus == GridStatus.Ready &&
                    s.iconMenu == null && s.openFolderId == null // SearchOverlayController: menu / thư mục mở thì không mở tìm kiếm
            },
            startZone = { (statusTopPx + SEARCH_START_TOP_OFFSET_DP * density.density)..drag.dockRect.top },
            onTrigger = { onSwipe() }
        )
    }

    // Trang tạm vừa có trong danh sách: lật sang (HomePagerController 275-300). Rời trang tạm khi đang kéo: báo VM (onGhostPageLeft).
    LaunchedEffect(pages.size) {
        if (drag.ghostFlipPending && state.ghostPage) {
            drag.ghostFlipPending = false
            // Job riêng của scope: pages.size đổi giữa lúc lật (key effect đổi) không được huỷ animation, nếu không pager bị lệch.
            val target = pages.size - 1
            scope.launch { drag.flipTo(target) }
        }
    }
    // Chỉ bỏ trang tạm khi pager đã dừng hẳn trên một trang thật: bỏ giữa lúc đang lật về (currentPage đổi ở nửa đường)
    // làm số trang giảm khi đang cuộn → animation dừng lệch.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }.collect { (page, scrolling) ->
            val s = currentState
            if (!scrolling && s.ghostPage && drag.session != null && page < s.pages.size) actions.onGhostPageLeft()
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.distinctUntilChanged().filter { !it }.collect {
            drag.onPagerIdle(System.currentTimeMillis())
        }
    }
    // JH3d D3: widget vừa thêm (HomeEvent.WidgetAdded) → chờ danh sách trang có trang đó rồi lật tới (HomePagerController.scrollToPage).
    LaunchedEffect(pageScrollRequests) {
        pageScrollRequests.collect { pageId ->
            val index = withTimeoutOrNull(PAGE_WAIT_MS.milliseconds) {
                snapshotFlow { currentState.pages.indexOfFirst { it.pageId == pageId } }.first { it >= 0 }
            } ?: return@collect
            pagerState.animateScrollToPage(index, animationSpec = tween(PageMotion.FULL_MS, easing = EaseOutQuint))
        }
    }
    // Pill sang chấm khi lướt, ở edit mode hay đang kéo; về ô tìm kiếm sau 600 ms đứng yên (HomePagerController 338-348).
    // P2-11: ngón tay đang trên pill dạng chấm cũng giữ dạng chấm.
    var pillTouching by remember { mutableStateOf(false) }
    val busy = pillTouching || pagerState.isScrollInProgress || state.editMode || drag.session != null
    var pillActive by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        if (busy) {
            pillActive = true
        } else {
            delay(PILL_IDLE_MS.milliseconds)
            pillActive = false
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(colorResource(R.color.ds_bg_base))
            .onSizeChanged {
                drag.rootWidth = it.width.toFloat()
                drag.rootHeight = it.height.toFloat()
                rootSize = it
            }
            .homeGestures(drag, isEditMode = { currentState.editMode }, haptic = haptic, search = searchSwipe)
    ) {
        // Lớp Home (ảnh nền + lưới + dock) ghi thành [homeBackdrop] khi thư mục mở: kính bảng thư mục lấy mẫu lớp này nên thấy Home bị bảng đè lên (kiểu
        // iOS), không chỉ ảnh nền. Đóng thư mục thì không ghi (đỡ một lớp vẽ mỗi khung).
        Box(
            Modifier
                .fillMaxSize()
                // JH3b + sửa 2026-11-20: menu hoặc thư mục mở thì CẢ lớp Home (ảnh nền + lưới + dock) mờ và tối, như iOS (View: BlurredSnapshotScrim).
                // blur cần API 31+, dưới đó chỉ tối. Hai modifier này đứng NGOÀI layerBackdrop nên lớp ghi cho kính bảng thư mục là Home CHƯA mờ:
                // qua bảng vẫn thấy rõ icon phía sau, kính của bảng tự làm mờ theo thông số kính.
                // P2-2 (2026-11-19): menu nhấn giữ chỉ LÀM TỐI (icon_scrim, đen 60%), không nhoè; thư mục mở vẫn nhoè + tối.
                // RA4 (2026-11-24, chủ dự án): thẻ xác nhận xóa app KHÔNG nhoè lớp Home (lưới vẫn rõ), chỉ tối nhẹ (drawRect dưới); kính chỉ ở
                // chính thẻ. Nhoè ở đây chỉ còn cho thư mục.
                .then(
                    if (folderProgress.value > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        Modifier.blur((FOLDER_BLUR_DP * folderProgress.value).dp)
                    else Modifier
                )
                .drawWithContent {
                    drawContent()
                    if (overlayP > 0f) {
                        drawRect(
                            Color.Black.copy(
                                alpha = maxOf(
                                    OVERLAY_DIM * menuProgress.value,
                                    FOLDER_DIM * folderProgress.value,
                                    FOLDER_DIM * removeOverlayProgress
                                )
                            )
                        )
                    }
                }
                .then(if (folderLayerOn) Modifier.layerBackdrop(homeBackdrop) else Modifier)
        ) {
            FrostedWallpaperLayer(frosted, state.backdrop, Modifier.fillMaxSize()) // PG3: không vẽ gì ra màn, chỉ ghi lớp nền mờ
            if (legacyGlass) FrostedWallpaperLayer(folderFrosted, folderSnapshot, Modifier.fillMaxSize(), cache = folderBlurCache)
            Wallpaper(state.backdrop, Modifier.fillMaxSize().layerBackdrop(backdrop))
            // PG4 G3 (đo 2026-11-23, `tools/perf/measure-home.ps1`, bản `perf`): kính thật của dock / widget KHÔNG tắt khi lướt trang (comment cũ ở
            // đây ghi đã tắt nhưng code chưa từng tắt). Oppo PKD130: đủ kính Janky 0,9–1,3 %, p95 12–13 ms, so với bỏ kính 0,5 %, p95 10–12 ms
            // (trong ngưỡng). Số "87 % khung giật" cũ là bản debug: bản debug cùng kịch bản đo 36 %, p95 106 ms. Đánh giá hiệu năng trên bản `perf`.
            CompositionLocalProvider(LocalDsBackdrop provides glassBackdrop, LocalDsFrostedBackdrop provides frostedBackdrop) {
                // TL1 Task 5 (như SystemBarsController.applySystemBarInsets): đỉnh lưới chừa sẵn chỗ cho hộp chạm của các pill edit mode, để lưới
                // không dịch khi vào edit mode: đỉnh = max(thanh trạng thái, editToolbarClearance). Cạnh dưới = inset dưới.
                val bars = WindowInsets.systemBarsIgnoringVisibility
                val topPx = maxOf(bars.getTop(density), editToolbarClearancePx(density))
                // PB Task 4: người dùng bật "ẩn thanh điều hướng" → bỏ lề dưới dành cho nav bar (systemBarsIgnoringVisibility luôn trả kích thước
                // như đang hiện, kể cả khi đã ẩn), để dock trôi xuống hết cạnh màn. Tắt thì giữ lề như trước (đề phòng nav bar hiện lại).
                val navHidden = isNavBarHidden()
                val barsSides = if (navHidden) WindowInsetsSides.Horizontal else WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                // Khung pager/pill/dock (toạ độ gốc) KHÔNG tính dịch của lớp trượt Library/Today: boundsInRoot() có tính graphicsLayer, mà đổi
                // translationX không layout lại nên callback có thể đã ghi khung lệch ở một khung hình giữa hoạt ảnh và giữ nguyên (icon nổi của
                // menu, kéo thả lệch sau khi mở/đóng Library). Đo trong lớp ([inner]) rồi cộng gốc chưa dịch ([outer]).
                val coords = remember { HomeLayerCoords() }
                fun syncRects() {
                    coords.rectOf(coords.pager)?.let { drag.pagerRect = it }
                    coords.rectOf(coords.pill)?.let {
                        drag.pillRect = it
                        pagerInfo.pillRect = it
                    }
                    coords.rectOf(coords.dock)?.let { drag.dockRect = it }
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coords.outer = it; syncRects() }
                        // JH3c D4: lớp Home (trang, pill, dock; không gồm ảnh nền) trượt sang trái theo tiến độ mở App Library, ẩn khi mở hẳn.
                        .graphicsLayer {
                            // TV: Today đẩy Home sang phải. Hai lớp phủ không mở chồng nhau nên nhiều nhất một tiến độ khác 0.
                            val p = libraryProgress()
                            val t = todayProgress()
                            translationX = size.width * (t - p)
                            alpha = if (p >= 1f || t >= 1f) 0f else 1f
                        }
                        .onGloballyPositioned { coords.inner = it; syncRects() }
                        .windowInsetsPadding(bars.only(barsSides))
                        .padding(top = with(density) { topPx.toDp() })
                ) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        // JH2 Task 1 (H5): dừng trang theo đường easeOutQuint của TunedPagerSnapHelper; Compose không cho thời lượng theo từng cú vuốt nên lấy FULL_MS.
                        flingBehavior = PagerDefaults.flingBehavior(pagerState, snapAnimationSpec = tween(PageMotion.FULL_MS, easing = EaseOutQuint)),
                        key = { pages.getOrNull(it)?.pageId ?: it.toLong() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(PAGER_WEIGHT)
                            .onSizeChanged {
                                pagerSize = it
                                pagerInfo.pagerWidth = it.width
                                pagerInfo.pagerHeight = it.height
                            }
                            .onGloballyPositioned { coords.pager = it; syncRects() }
                    ) { index ->
                        val page = pages.getOrNull(index)
                        // 2026-11-23 (chủ dự án: nhiều trang thì giật): pager chỉ dựng trang hiện tại + 1 trang mỗi bên (beyondViewportPageCount = 1).
                        // Trang đệm nằm ngoài màn vẫn vẽ ô thư mục/widget kính thật (RenderEffect mỗi khung), nên chỉ trang ĐANG HIỆN (một phần) được
                        // lấy mẫu nền; trang đệm vẽ bề mặt tĩnh tới khi trượt vào.
                        val onScreen by remember(index) {
                            derivedStateOf { abs(index - (pagerState.currentPage + pagerState.currentPageOffsetFraction)) < 1f }
                        }
                        CompositionLocalProvider(LocalDsBackdrop provides if (onScreen) backdrop else null) {
                            if (page != null && cells != null) HomeGridPage(page, cells, icons, edit, onAppClick, Modifier.fillMaxSize())
                        }
                    }
                    HomeSearchPill(
                        pageCount = pages.size,
                        position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                        active = pillActive,
                        showSearch = showSearch,
                        showDots = showDots,
                        onSearchClick = onSearchClick,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .onGloballyPositioned { coords.pill = it; syncRects() },
                        // P2-11: pill dạng chấm xin trang: lật mượt tới đó (lần xin mới nhắm lại đích từ chỗ đang trượt, như setCurrentItem của ViewPager2).
                        onPageRequest = { target ->
                            scope.launch {
                                pagerState.animateScrollToPage(target.coerceIn(0, pages.size - 1), animationSpec = tween(PageMotion.FULL_MS, easing = EaseOutQuint))
                            }
                        },
                        onDotsTouch = { pillTouching = it },
                        onDotsInteractive = {
                            drag.pillDotsInteractive = it
                            pagerInfo.pillDotsInteractive = it
                        }
                    )
                    // 2026-11-23 (iOS-style): lề dock (2 cạnh bên + đáy) = X/2, X = khe cách đều của lưới Home phía trên (cells.iconSpacingX —
                    // khe trái màn/icon đầu = khe giữa 2 icon = khe icon cuối/phải màn). Dock KHÔNG tự tính gap riêng (xem HomeDock): dùng thẳng
                    // X nên icon dock thẳng cột với icon lưới bên trên; lề dock = X/2 để bộ ba lề (lưới-icon, dock-icon, dock-màn) liền mạch theo
                    // đúng tỉ lệ nửa khe.
                    val dockMargin = with(density) { ((cells?.iconSpacingX ?: 0) / 2f).toDp() }
                    HomeDock(
                        dock = state.dock,
                        cells = cells,
                        icons = icons,
                        edit = edit,
                        onAppClick = onAppClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(DOCK_WEIGHT)
                            .padding(horizontal = dockMargin)
                            .onGloballyPositioned { coords.dock = it; syncRects() }
                    )
                    // Lề đáy NGOÀI phần trọng số: LinearLayout của activity_home.xml chia trọng số sau khi trừ margin, nên pager và dock cao đúng như View.
                    Spacer(Modifier.height(dockMargin))
                }
            }
        }
        CompositionLocalProvider(LocalDsBackdrop provides glassBackdrop, LocalDsFrostedBackdrop provides frostedBackdrop) {
            if (state.editMode && drag.session == null) {
                HomeEditToolbar(
                    onEdit = { editMenuOpen = !editMenuOpen },
                    onDone = actions::exitEditMode,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = with(density) { editToolbarTopPaddingPx.toDp() }),
                    onPillBounds = { i, r ->
                        if (r == null) drag.toolbarRects.remove(i) else drag.toolbarRects[i] = r
                    }
                )
            }
            // P2-9e: menu "Sửa" chỉ sống trong edit mode (thoát edit mode hay bắt đầu kéo thì đóng).
            val menuAnchor = drag.toolbarRects[0]
            val showMenu = editMenuOpen && state.editMode && drag.session == null && menuAnchor != null
            SideEffect { drag.editMenuOpen = showMenu }
            LaunchedEffect(state.editMode) { if (!state.editMode) editMenuOpen = false }
            if (showMenu && menuAnchor != null) {
                HomeEditMenu(
                    anchor = menuAnchor,
                    items = buildList {
                        if (onAddWidget != null) add(HomeEditMenuItem(R.string.wp_add_widget, R.drawable.wp_ic_widgets, onAddWidget))
                        add(HomeEditMenuItem(R.string.home_wallpapers, R.drawable.ds_ic_wallpaper, onWallpapers))
                    },
                    onDismiss = { editMenuOpen = false }
                )
            }
            // JH3b: menu nhấn giữ và bảng thư mục, trên Home đã làm mờ; ảnh nổi khi kéo vẫn ở trên cùng.
            if (cells != null) {
                HomeFolderPanel(
                    backdrop = homeBackdrop,
                    folder = openFolder,
                    originIcon = folderOrigin,
                    showLabels = state.showLabels,
                    icons = icons,
                    edit = edit,
                    rootWidth = rootSize.width.toFloat(),
                    rootHeight = rootSize.height.toFloat(),
                    progress = folderProgress,
                    onAppClick = onAppClick,
                    onRemoveApp = overlays.onRemoveFolderApp,
                    onRename = overlays.onRenameFolder,
                    onClose = overlays.closeFolder,
                    frostedBackdrop = if (PerfSwitches.noFrosted) null else folderFrosted.backdropOrNull ?: frosted.backdropOrNull
                )
            }
            HomeIconMenu(
                menu = state.iconMenu,
                app = menuApp,
                icons = icons,
                iconSizePx = cells?.iconSize ?: 0,
                progress = menuProgress,
                backdrop = homeBackdrop,
                onAction = overlays.onIconMenuAction,
                onShortcut = overlays.onIconMenuShortcut,
                onDismiss = overlays.closeIconMenu
            )
            // RA2 (2026-11-24): thẻ xác nhận xóa app (dấu "−" lưới/dock/thư mục) vẽ NGAY TRONG cây này, cùng homeBackdrop với menu nhấn giữ ở
            // trên — có kính thật (khúc xạ + mờ lấy mẫu Home), không còn Dialog cửa sổ riêng (chỉ làm mờ cửa sổ hệ thống).
            RemoveAppOverlay(homeBackdrop)
            // LD Task 4: ảnh nổi vẽ ở HomeActivity, trên lớp App Library (kéo app từ Library ra phải thấy ngay khi Library còn hiện).
        }
    }
}

/** `home_edit_pill_touch + home_edit_pill_gap + home_edit_badge_overhang − GridMetrics.PADDING_DP`, như `SystemBarsController.editToolbarClearancePx`. */
@Composable
private fun editToolbarClearancePx(density: Density): Int = with(density) {
    dimensionResource(R.dimen.home_edit_pill_touch).roundToPx() +
        dimensionResource(R.dimen.home_edit_pill_gap).roundToPx() +
        dimensionResource(R.dimen.home_edit_badge_overhang).roundToPx() -
        (GridMetrics.PADDING_DP * density.density).toInt()
}

@Composable
private fun Wallpaper(bitmap: Bitmap?, modifier: Modifier) {
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image != null) {
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(colorResource(R.color.ds_bg_base)))
    }
}

// Trọng số như activity_home.xml (pager 6 = GridMetrics.DEFAULT_ROWS, dock 1).
private const val PAGER_WEIGHT = 6f
private const val FOLDER_SNAPSHOT_MIN_BLUR_PX = 4 // PG3: sàn bán kính mờ (px của bản nhỏ 25 %) cho ảnh chụp sau bảng thư mục, như sàn 24 dp của kính thật
private const val SETTLED_FRACTION = 0.01f // PG1 G1: lệch dưới 1 % bề rộng trang coi như đã đứng ở trang
private const val DOCK_WEIGHT = 1f
private const val PILL_IDLE_MS = 600L
private const val PAGE_WAIT_MS = 2_000L
private const val SEARCH_START_TOP_OFFSET_DP = 48f // SearchOverlayController: mép trên thuộc về hệ thống

/** Như PathInterpolator(0.22, 1, 0.36, 1) của TunedPagerSnapHelper / PagerFlipAnimator. */
internal val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** JH3b — việc của menu nhấn giữ và bảng thư mục, nối vào `HomeViewModel` ở Activity. */
internal class HomeOverlayActions(
    val onIconMenuAction: (IconMenuAction) -> Unit = {},
    val onIconMenuShortcut: (AppShortcutItem) -> Unit = {},
    val closeIconMenu: () -> Unit = {},
    val openFolder: (Long) -> Unit = {},
    val closeFolder: () -> Unit = {},
    val onRenameFolder: (Long, String) -> Unit = { _, _ -> },
    val onRemoveFolderApp: (ComponentKey) -> Unit = {}
)

/** App có [key] ở đâu đó trên Home (trang, dock, trong thư mục). */
private fun findApp(state: HomeUiState, key: ComponentKey): AppItem? {
    for (d in state.dock) {
        if (d.app?.key == key) return d.app
        d.folder?.previewApps?.firstOrNull { it.key == key }?.let { return it }
    }
    for (page in state.pages) for (item in page.items) {
        when (item) {
            is PlacedApp -> if (item.app.key == key) return item.app
            is PlacedFolder -> item.previewApps.firstOrNull { it.key == key }?.let { return it }
            else -> Unit
        }
    }
    return null
}

private const val OVERLAY_BLUR_DP = 16f
private const val OVERLAY_DIM = 0.25f // P2-2: tối dần theo tiến độ menu; 0,6 (`icon_scrim`) quá tối so với iOS 26, chủ dự án hạ 2026-11-23
// Thư mục mở, theo ảnh iOS 26 chủ dự án gửi 2026-11-23: nền Home mờ mạnh nhưng gần như không tối. Bằng FOLDER_MIN_BLUR_DP của
// HomeFolderPanel để trong bảng mờ ngang ngoài, chỉ sáng hơn (bảng lấy mẫu lớp Home chưa tối).
private const val FOLDER_BLUR_DP = 18f // chủ dự án chốt sau khi thử 24/12/8/4 dp (2026-11-23)
private const val FOLDER_DIM = 0.15f

/**
 * Toạ độ các khối của lớp Home trượt (Library/Today). [outer] đứng TRƯỚC graphicsLayer (chưa dịch), [inner] đứng SAU (cùng gốc, trong lớp):
 * khung một con = vị trí của nó trong [inner] + gốc gốc-màn-hình của [outer], nên không phụ thuộc translationX lúc callback chạy.
 */
private class HomeLayerCoords {
    var outer: LayoutCoordinates? = null
    var inner: LayoutCoordinates? = null
    var pager: LayoutCoordinates? = null
    var pill: LayoutCoordinates? = null
    var dock: LayoutCoordinates? = null

    fun rectOf(child: LayoutCoordinates?): Rect? {
        val o = outer ?: return null
        val i = inner ?: return null
        val c = child ?: return null
        if (!o.isAttached || !i.isAttached || !c.isAttached) return null
        return i.localBoundingBoxOf(c, clipBounds = false).translate(o.positionInRoot())
    }
}

/** JH3c — trạng thái pager mà Activity đọc lúc chạm xuống (cử chỉ App Library chạy ngoài Compose). */
internal class HomePagerInfo {
    var currentPage = 0
    var scrolling = false
    var realPageCount = 0
    var pagerWidth = 0
    var pagerHeight = 0
    /** PG1 G1: PagerState của Home đang hiện (gán/bỏ trong [HomeScreen]); null = chưa dựng. Đọc ở luồng chính lúc có sự kiện chạm. */
    var pagerState: PagerState? = null

    /** PG1 G1: tay đang kéo pager (`interactionSource.collectIsDraggedAsState`). Lật trang khi kéo icon dùng `animateScrollToPage`, không tính. */
    var pagerDragged: State<Boolean>? = null

    /** PG1 G1: cho pager trượt nốt tới trang [Int] (bản Compose của `finishSettleOnLastPage/FirstPage`); no-op nếu đã ở / đang về đúng trang. */
    var settleTo: ((Int) -> Unit)? = null

    /** PG2: trạng thái kéo-thả của Home đang hiện (gán/bỏ trong [HomeScreen]); bảng chọn widget dùng để mở phiên kéo widget mới. */
    var drag: HomeDragState? = null

    /**
     * PG1 G1 (FX7 D2/H1 + TV-1 của repo XML): trang pager "sẽ đứng" — đích đang trượt tới khi pager còn cuộn (sau fling hoặc lật trang), trang hiện tại khi
     * đứng yên; null khi tay đang kéo pager (cú vuốt đổi trang thường không được nhường cho Thư viện / Today) hoặc chưa có pager.
     * Trước đây cổng đòi pager đứng yên, nên cú vuốt nhanh tới lúc pager còn trượt nốt bị pager nhận và phải vuốt thêm lần nữa.
     */
    private fun restingPage(): Int? {
        val ps = pagerState ?: return if (scrolling) null else currentPage
        if (pagerDragged?.value == true) return null
        return if (ps.isScrollInProgress) ps.targetPage else ps.currentPage
    }

    /** Pager đang ở / đang trượt nốt tới trang THẬT cuối (trang tạm không tính): điều kiện mở Thư viện lúc chạm xuống. */
    val settledOnLastPage: Boolean get() = realPageCount > 0 && restingPage() == realPageCount - 1

    /** P2-11: pill dạng chấm đang nhận cử chỉ, và khung của nó (toạ độ gốc = cửa sổ). */
    var pillDotsInteractive = false
    var pillRect = Rect.Zero

    /** P2-11 (`SearchPillView.claimsTouchAt`): chạm bắt đầu tại ([x], [y]) (toạ độ cửa sổ) là cử chỉ của pill dạng chấm: không làm ứng viên mở Library / Today. */
    fun pillClaims(x: Float, y: Float): Boolean = pillDotsInteractive && pillRect.contains(Offset(x, y))

    /** TV: đứng yên ở trang thật đầu tiên (điều kiện mở màn Today, như [settledOnLastPage] cho Library). */
    val settledOnFirstPage: Boolean get() = realPageCount > 0 && restingPage() == 0
}
