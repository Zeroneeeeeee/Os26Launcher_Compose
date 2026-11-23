package gambi.global.os26launcher.ui.home

/** Sự kiện một lần cho UI (Toast...). Khác với state: không được phát lại khi Activity render lại. */
sealed interface HomeEvent {
    // P2-4: bỏ WallpaperApplied / WallpaperFailed cùng hộp thoại hỏi áp wallpaper (Cài đặt tự báo khi áp ảnh).
    data class AppLaunchFailed(val label: String) : HomeEvent

    /** M7 Task 10: mục menu nhấn giữ (shortcut, thông tin, gỡ cài đặt) bị hệ thống từ chối. */
    object MenuActionFailed : HomeEvent

    /** G2c: widget vừa được thêm vào trang [pageId]; pager cuộn tới trang đó. */
    data class WidgetAdded(val pageId: Long) : HomeEvent

    /** G3c Task 6: không ghi được hàng cho widget app vừa gắn (hết chỗ, cỡ vượt trần, lỗi DB); id đã được giải phóng. */
    object AppWidgetAddFailed : HomeEvent
}
