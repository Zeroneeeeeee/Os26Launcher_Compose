package gambi.global.os26launcher.ui.onboarding

import androidx.annotation.StringRes
import gambi.global.os26launcher.R

/**
 * OB — năm màn của luồng Onboarding (Figma 0.1–0.5; 0.0 là splash hệ thống, xem theme `Theme.Os26Launcher.Starting`).
 * Mỗi bước khai cấu hình phần CỐ ĐỊNH của [OnboardingActivity] (thanh trên, hàng nút); chỉ phần giữa đổi theo bước.
 * Thứ tự enum = thứ tự màn = chỉ số chấm của `DsStepIndicatorView`.
 */
enum class OnboardingStep(
    @StringRes val primaryLabel: Int,
    @StringRes val secondaryLabel: Int?,
    val showBack: Boolean,
    val showSkip: Boolean
) {
    WELCOME(R.string.onboarding_start, null, showBack = false, showSkip = true),
    ICON_STYLE(R.string.onboarding_continue, null, showBack = true, showSkip = true),
    WALLPAPER(R.string.onboarding_continue, null, showBack = true, showSkip = true),
    DEFAULT_LAUNCHER(R.string.onboarding_set_default, R.string.onboarding_later, showBack = true, showSkip = true),

    /** Bước cuối: không có "Bỏ qua" (plan OB Q2). */
    DONE(R.string.onboarding_go_settings, null, showBack = true, showSkip = false);

    /** 0..4, cho `DsStepIndicatorView.currentStep`. */
    val index: Int get() = ordinal
}

/** Việc một lần ViewModel giao cho Activity. */
sealed interface OnboardingEvent {
    /** Đóng Onboarding (xong hoặc Bỏ qua); Home bên dưới hiện lại. */
    data object Close : OnboardingEvent

    /** Mở hộp thoại chọn launcher mặc định (cần Activity: `registerForActivityResult`). */
    data object RequestDefaultLauncher : OnboardingEvent
}
