package gambi.global.os26launcher.domain.config

/** CF — cờ nội bộ (không phải lựa chọn người dùng). `docs/config-schema.md` mục 3.7. */
data class AppState(
    /** Đã xong hoặc bỏ qua onboarding. Khóa `first_run_done`. */
    val onboardingDone: Boolean = false,
    /** TV: đã tạo widget mặc định cho màn Today (một lần; gỡ hết widget sau đó thì không tạo lại). Khóa `today_seeded`. Bản Compose 2026-11-21: chỉ còn là đầu vào một lần của `LayoutReconciler.migrateLegacyToday` (không ai ghi nữa). */
    val todaySeeded: Boolean = false
)
