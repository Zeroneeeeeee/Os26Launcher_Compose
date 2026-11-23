package gambi.global.os26launcher.ui.onboarding

/**
 * OB — trạng thái trong process (không lưu đĩa) nối Onboarding với Home mới khi đặt mặc định ở 0.4.
 *
 * Chọn OS26 Launcher trong hộp thoại vai trò Home: PermissionController gửi intent HOME, hệ thống dựng `HomeActivity` MỚI trong task
 * Home; kết quả không về Onboarding cũ (đo Live 4 2026-11-17, process giữ nguyên). Onboarding bật cờ ngay trước khi mở hộp thoại;
 * Home mới đọc-và-xóa cờ để mở Onboarding thẳng ở 0.5 thay vì 0.1. Hủy hộp thoại thì Onboarding quay lại và tự xóa cờ.
 * Process bị đóng giữa chừng thì cờ mất: Onboarding chạy lại từ 0.1 (chấp nhận, hiếm).
 */
object OnboardingRuntime {
    @Volatile
    private var pendingDefaultLauncher = false

    fun markPendingDefaultLauncher() {
        pendingDefaultLauncher = true
    }

    fun clearPendingDefaultLauncher() {
        pendingDefaultLauncher = false
    }

    /** true nếu Onboarding vừa mở hộp thoại đặt mặc định và chưa ai nhận; đọc xong thì xóa. */
    fun consumePendingDefaultLauncher(): Boolean {
        val pending = pendingDefaultLauncher
        pendingDefaultLauncher = false
        return pending
    }
}
