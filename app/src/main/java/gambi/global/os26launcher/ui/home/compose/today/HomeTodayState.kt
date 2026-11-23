package gambi.global.os26launcher.ui.home.compose.today

import android.app.Activity
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.runtime.mutableFloatStateOf
import gambi.global.os26launcher.ui.home.GridStatus
import gambi.global.os26launcher.ui.home.HomeUiState
import gambi.global.os26launcher.ui.home.HomeViewModel
import gambi.global.os26launcher.ui.home.LibraryOverlayController

/**
 * TV-1 — trạng thái và cử chỉ của Today View (vuốt PHẢI từ trang đầu của Home), bản Compose của `TodayController` (REF 3ab9c20). Cùng mẫu
 * [gambi.global.os26launcher.ui.home.compose.library.HomeLibraryState]: `LibraryOverlayController` chạy ngược chiều, gọi từ
 * `HomeActivity.dispatchTouchEvent`; tiến độ ghi vào [swipeProgress] (0 = Home, 1 = Today mở hẳn) để `HomeScreen` đẩy lớp Home sang phải và
 * [HomeToday] trượt vào từ trái.
 *
 * Mở chỉ khi pager đứng yên ở trang đầu (xét lúc DOWN, chạm không bắt đầu trên pill dạng chấm) và không vướng edit mode / kéo / menu / panel
 * thư mục / tìm kiếm / Library; các trạng thái đó bật lên lúc Today đang mở thì Today đóng.
 */
internal class HomeTodayState(
    private val activity: Activity,
    private val viewModel: HomeViewModel,
    private val homeState: () -> HomeUiState,
    private val isSearchOpen: () -> Boolean,
    private val isPagerSettledOnFirstPage: () -> Boolean,
    /** App Library đang mở, đang kéo hoặc đang chạy spring: không mở Today chồng lên. */
    private val isLibraryActive: () -> Boolean,
    /** P2-11 (`SearchPillView.claimsTouchAt`): chạm bắt đầu trên pill dạng chấm là cử chỉ của pill, không mở Today. */
    private val pillClaims: (Float, Float) -> Boolean = { _, _ -> false }
) {
    val swipeProgress = mutableFloatStateOf(0f)

    /** Q6 (công tắc riêng của bản Compose). false thì không mở được và đang mở thì đóng. */
    var enabled = true

    /**
     * TV-3: đang nhấn giữ / kéo một widget trên Today ([HomeToday] ghi). Cử chỉ đóng Today thấy chạm ở Activity, TRƯỚC cây view, nên phải hỏi cờ
     * này để vuốt ngang trong lúc kéo widget không đóng Today.
     */
    var isHolding = false

    private var pagerQualifiedAtDown = false
    private var rendered = false

    private val controller = LibraryOverlayController(
        host = object : LibraryOverlayController.Host {
            override val width: Int get() = activity.window.decorView.width
            override fun canOpen(): Boolean = canOpenToday()
            override fun canClose(): Boolean = !isHolding
            override fun onProgress(progress: Float) {
                swipeProgress.floatValue = progress
            }
            override fun onSettled(open: Boolean) = viewModel.setTodayOpen(open)
        },
        slopPx = ViewConfiguration.get(activity).scaledTouchSlop.toFloat(),
        flingVelocityPxPerSec = FLING_DP_PER_SEC * activity.resources.displayMetrics.density,
        opensLeftward = false
    )

    /** Màn Today đang hiện một phần hoặc mở hẳn, hay đang kéo/chạy spring (Library dùng để không mở chồng). */
    val isActive: Boolean get() = homeState().todayOpen || controller.isAnimatingOrDragging || swipeProgress.floatValue > 0f

    /** Gọi trong `dispatchTouchEvent`. Trạng thái pager chỉ xét ở DOWN, như Library (D2/H1). */
    fun onTouch(ev: MotionEvent): LibraryOverlayController.Result {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            pagerQualifiedAtDown = isPagerSettledOnFirstPage() && !pillClaims(ev.x, ev.y)
        }
        return controller.onTouch(ev)
    }

    /** Đồng bộ theo `todayOpen`; trạng thái chặn (edit mode, kéo, menu, panel thư mục, Library) bật khi đang mở thì đóng. */
    fun render(state: HomeUiState) {
        if (state.todayOpen && (!enabled || state.editMode || state.draggingItemId != null || state.iconMenu != null ||
                state.openFolderId != null || state.libraryOpen)
        ) {
            viewModel.setTodayOpen(false)
            return
        }
        val first = !rendered
        rendered = true
        if (controller.isAnimatingOrDragging) return
        if (state.todayOpen != controller.isOpen) controller.snapTo(state.todayOpen, animate = !first)
    }

    /** Nhấn Home khi đang ở Home / Back: đóng Today nếu đang mở. */
    fun closeIfOpen() {
        if (homeState().todayOpen) viewModel.setTodayOpen(false)
    }

    private fun canOpenToday(): Boolean {
        if (!enabled) return false
        val s = homeState()
        return s.gridStatus == GridStatus.Ready && s.pages.isNotEmpty() && !s.ghostPage && pagerQualifiedAtDown &&
            !s.editMode && s.draggingItemId == null && s.iconMenu == null && s.openFolderId == null &&
            !s.libraryOpen && !isLibraryActive() && !isSearchOpen()
    }

    private companion object {
        /** Vận tốc vuốt ngang tối thiểu (dp/s) để thả tay là mở/đóng dù chưa quá nửa quãng (cùng Library). */
        const val FLING_DP_PER_SEC = 600f
    }
}
