package gambi.global.os26launcher.ui.home.compose.library

import android.app.Activity
import android.content.res.Configuration
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.CategoryGroup
import gambi.global.os26launcher.domain.model.CategoryGroupApps
import gambi.global.os26launcher.domain.model.LibraryContent
import gambi.global.os26launcher.domain.model.ScreenRect
import gambi.global.os26launcher.ui.home.GridStatus
import gambi.global.os26launcher.ui.home.HomeUiState
import gambi.global.os26launcher.ui.home.HomeViewModel
import gambi.global.os26launcher.ui.home.LibraryOverlayController

/**
 * JS4 — trạng thái và cử chỉ của App Library Compose (thay `ComposeLibraryHost`, JH3c). Không có View: [HomeLibrary] vẽ từ các state ở
 * đây. Cử chỉ vuốt mở/đóng giữ nguyên `LibraryOverlayController` (gọi từ `HomeActivity.dispatchTouchEvent`, D2 của JH3c), tiến độ ghi vào
 * [swipeProgress] và báo Home qua [onHomeProgress] để lớp Home trượt sang trái.
 */
internal class HomeLibraryState(
    private val activity: Activity,
    private val viewModel: HomeViewModel,
    private val homeState: () -> HomeUiState,
    private val isDragging: () -> Boolean,
    private val isSearchOpen: () -> Boolean,
    private val isPagerSettledOnLastPage: () -> Boolean,
    private val onHomeProgress: (Float) -> Unit,
    val onSearchClick: () -> Unit,
    /** TV: màn Today đang hiện/kéo/chạy spring: không mở Library chồng lên (Home chỉ có 1 trang thì cả hai cùng là ứng viên lúc DOWN). */
    private val isTodayActive: () -> Boolean = { false },
    /** P2-11: chạm bắt đầu tại (x, y) (toạ độ cửa sổ) là cử chỉ của pill dạng chấm. */
    private val pillClaims: (Float, Float) -> Boolean = { _, _ -> false }
) {
    /** 0 = Home, 1 = Library mở hẳn. */
    val swipeProgress = mutableFloatStateOf(0f)

    /** Nội dung theo kiểu hiển thị; `null` = đang nạp. */
    var content by mutableStateOf<LibraryContent?>(null)
        private set

    /** Nhóm đang mở (kèm danh sách app); `null` = màn nhóm đóng. */
    var openGroup by mutableStateOf<CategoryGroupApps?>(null)
        private set

    /** Tiến độ màn nhóm mỗi khung (HomeLibrary ghi), để cử chỉ không đóng Library khi màn nhóm đang mở/đang chạy. */
    var panelProgress = 0f

    /** Khung màn hình của ô 2 × 2 mỗi nhóm (HomeLibrary ghi khi đặt chỗ), để màn nhóm phình ra từ đúng ô. */
    val tileRects = HashMap<CategoryGroup, ScreenRect>()

    val night: Boolean
        get() = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    var enabled = true
    private var pagerQualifiedAtDown = false
    private var rendered = false

    private val controller = LibraryOverlayController(
        host = object : LibraryOverlayController.Host {
            override val width: Int get() = activity.window.decorView.width
            override fun canOpen(): Boolean = canOpenLibrary()
            override fun canClose(): Boolean = homeState().openCategoryGroup == null && panelProgress <= 0f
            override fun onProgress(progress: Float) {
                swipeProgress.floatValue = progress
                onHomeProgress(progress)
            }
            override fun onSettled(open: Boolean) = viewModel.setLibraryOpen(open)
        },
        slopPx = ViewConfiguration.get(activity).scaledTouchSlop.toFloat(),
        flingVelocityPxPerSec = FLING_DP_PER_SEC * activity.resources.displayMetrics.density
    )

    /** TV: Library đang hiện một phần hoặc mở hẳn, hay đang kéo/chạy spring (Today dùng để không mở chồng). */
    val isActive: Boolean get() = homeState().libraryOpen || controller.isAnimatingOrDragging || swipeProgress.floatValue > 0f

    fun onGroupClick(group: CategoryGroup) = viewModel.openCategoryGroup(group)

    fun onAppClick(app: AppItem, rect: ScreenRect) = viewModel.onAppClicked(app, rect)

    /**
     * LD Task 2 — chế độ sửa của Library (nhấn giữ yên một app): icon rung, dấu "−" hỏi gỡ cài đặt, bấm icon không mở app. Thoát: chạm chỗ trống, Back,
     * Library đóng ([render], [onTouch]).
     */
    var editing by mutableStateOf(false)
        private set

    /** LD Task 3: [LibraryDragGesture] đã nhận "giữ yên" trên một app (rung do cử chỉ lo). */
    fun enterEditing() {
        editing = true
    }

    /**
     * LD Task 3 — vùng chạm của các app đang hiện (toạ độ cửa sổ, đã cắt theo khung cuộn), do ô app ghi khi đặt chỗ và gỡ khi rời composition.
     * [LibraryDragGesture] tra ở đây lúc chạm xuống. [inGroup] = ô của màn nhóm (khi màn nhóm mở chỉ xét ô của nó).
     */
    class AppHit(val app: AppItem, val hit: android.graphics.RectF, val icon: () -> android.graphics.RectF, val inGroup: Boolean)

    private val appHits = LinkedHashMap<Any, AppHit>()

    fun registerAppHit(token: Any, hit: AppHit) {
        appHits[token] = hit
    }

    fun unregisterAppHit(token: Any) {
        appHits.remove(token)
    }

    /** App dưới điểm (x, y) của cửa sổ, chỉ khi Library mở hẳn và màn nhóm đứng yên (mở hẳn hoặc đóng hẳn); null = không có. */
    fun appAt(x: Float, y: Float): AppHit? {
        if (!enabled || !homeState().libraryOpen || swipeProgress.floatValue < 1f) return null
        val group = when {
            panelProgress <= 0f -> false
            panelProgress >= 1f -> true
            else -> return null
        }
        return appHits.values.lastOrNull { it.inGroup == group && it.hit.contains(x, y) }
    }

    fun exitEditing() {
        editing = false
    }

    /** LD Task 2: dấu "−" trên app ở chế độ sửa → hỏi gỡ cài đặt → trình gỡ của hệ thống. */
    fun onRemoveApp(app: AppItem) {
        val host = activity as? androidx.activity.ComponentActivity ?: return
        gambi.global.os26launcher.ui.home.compose.showUninstallAppDialog(host, app.label) { viewModel.uninstallApp(app.key) }
    }

    fun onCloseGroup() = viewModel.closeCategoryGroup()

    /** Gọi đầu `dispatchTouchEvent`. Trạng thái pager chỉ xét ở DOWN (AppLibraryController D2/H1). */
    fun onTouch(ev: MotionEvent): LibraryOverlayController.Result {
        // P2-11: chạm bắt đầu trên pill dạng chấm là cử chỉ của pill (chạm/giữ/kéo theo chấm): không làm ứng viên mở Library.
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) pagerQualifiedAtDown = isPagerSettledOnLastPage() && !pillClaims(ev.x, ev.y)
        return controller.onTouch(ev)
    }

    fun onContent(value: LibraryContent?) {
        content = value
        syncGroup(homeState())
    }

    /** Đồng bộ theo `libraryOpen`; edit mode / kéo / menu / thư mục / hỏi ảnh nền bật khi đang mở thì đóng (JH3c D3). */
    fun render(state: HomeUiState) {
        syncGroup(state)
        if (!state.libraryOpen) editing = false // LD Task 2: Library đóng thì thoát chế độ sửa
        if (state.libraryOpen && (state.editMode || state.draggingItemId != null || state.iconMenu != null ||
                state.openFolderId != null)
        ) {
            viewModel.setLibraryOpen(false)
            return
        }
        val first = !rendered
        rendered = true
        if (controller.isAnimatingOrDragging) return
        if (state.libraryOpen != controller.isOpen) controller.snapTo(state.libraryOpen, animate = !first)
    }

    fun closeIfOpen() {
        if (homeState().libraryOpen) viewModel.setLibraryOpen(false)
    }

    private fun syncGroup(state: HomeUiState) {
        val group = state.openCategoryGroup
        if (group == null) {
            openGroup = null
            return
        }
        val groups = (content as? LibraryContent.Folders)?.groups ?: return
        val entry = groups.firstOrNull { it.group == group }
        if (entry == null || entry.apps.isEmpty()) {
            viewModel.closeCategoryGroup()
            return
        }
        openGroup = entry
    }

    private fun canOpenLibrary(): Boolean {
        if (!enabled) return false
        val s = homeState()
        return s.gridStatus == GridStatus.Ready && s.pages.isNotEmpty() && !s.ghostPage && pagerQualifiedAtDown &&
            !s.editMode && s.draggingItemId == null && s.iconMenu == null && s.openFolderId == null &&
            !isSearchOpen() && !isDragging() && !s.todayOpen && !isTodayActive()
    }

    private companion object {
        const val FLING_DP_PER_SEC = 600f // G2a B2 (D17)
    }
}

/** Chuỗi tên nhóm App Library (ô nhóm và màn nhóm). JS4: chuyển từ `CategoryPageView.kt` sang đây. */
@StringRes
internal fun categoryGroupLabelRes(group: CategoryGroup): Int = when (group) {
    CategoryGroup.SOCIAL -> R.string.library_page_group_social
    CategoryGroup.ENTERTAINMENT -> R.string.library_page_group_entertainment
    CategoryGroup.GAME -> R.string.library_page_group_game
    CategoryGroup.PRODUCTIVITY -> R.string.library_page_group_productivity
    CategoryGroup.NEWS -> R.string.library_page_group_news
    CategoryGroup.MAPS -> R.string.library_page_group_maps
    CategoryGroup.UTILITIES -> R.string.library_page_group_utilities
    CategoryGroup.OTHER -> R.string.library_page_group_other
    CategoryGroup.SUGGESTED -> R.string.library_page_group_suggested // LO D6
    CategoryGroup.RECENTLY_ADDED -> R.string.library_page_group_recent
    CategoryGroup.ALL -> R.string.library_page_group_all // LO D5
}
