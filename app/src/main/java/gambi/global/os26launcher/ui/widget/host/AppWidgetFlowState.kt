package gambi.global.os26launcher.ui.widget.host

import gambi.global.os26launcher.domain.model.AppWidgetRef
import javax.inject.Inject
import javax.inject.Singleton

/**
 * G3c Task 6 — yêu cầu thêm một widget app: provider, cỡ (ô), cỡ dp của khung để gắn (`OPTION_APPWIDGET_*`) và trang đang xem lúc bấm chip.
 * Chỉ là dữ liệu số/chuỗi, không giữ view hay Context, nên sống được trong [AppWidgetFlowState] qua `recreate()`.
 */
data class AppWidgetAddRequest(
    val provider: AppWidgetRef,
    val spanX: Int,
    val spanY: Int,
    val widthDp: Int,
    val heightDp: Int,
    val preferredPageIndex: Int
)

/** Các trạng thái của luồng thêm (plan G3c, mục "Luồng thêm"). Hai trạng thái chạy đều mang id đã cấp và đánh dấu "đang trong luồng" ở `AppWidgetRepository`. */
sealed interface AppWidgetFlow {
    data object Idle : AppWidgetFlow

    /** Đã cấp id, đang chờ hộp thoại quyền gắn (`ACTION_APPWIDGET_BIND`). */
    data class Binding(val request: AppWidgetAddRequest, val appWidgetId: Int, val startedAtMs: Long) : AppWidgetFlow

    /** Đã gắn; provider cần cấu hình. [started] = đã mở activity cấu hình (chưa mở thì `onResume` kế tiếp mở). */
    data class Configuring(
        val request: AppWidgetAddRequest,
        val appWidgetId: Int,
        val startedAtMs: Long,
        val started: Boolean
    ) : AppWidgetFlow
}

/**
 * G3c Task 6 — trạng thái luồng thêm widget app, MỘT bản cho cả app (singleton Hilt) để sống qua `recreate()` của `HomeActivity` (activity chỉ giữ
 * `ActivityResultLauncher`). Chỉ truy cập trên luồng chính. Tiến trình bị giết thì trạng thái mất, id mồ côi do lần quét lúc khởi động dọn (plan L8).
 */
@Singleton
class AppWidgetFlowState @Inject constructor() {
    var current: AppWidgetFlow = AppWidgetFlow.Idle
}
