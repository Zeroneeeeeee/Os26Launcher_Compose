package gambi.global.os26launcher.ui.onboarding

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gambi.global.os26launcher.core.ui.reassertEdgeToEdge
import gambi.global.os26launcher.domain.repository.DefaultLauncherRepository
import gambi.global.os26launcher.ui.language.AppLanguages
import gambi.global.os26launcher.ui.onboarding.compose.OnboardingActions
import gambi.global.os26launcher.ui.onboarding.compose.OnboardingScreen
import gambi.global.os26launcher.ui.onboarding.compose.OnboardingUi
import gambi.global.os26launcher.ui.settings.DefaultLauncherAction
import gambi.global.os26launcher.ui.settings.SettingsActivity
import gambi.global.os26launcher.ui.settings.SettingsScreenId
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * OB — luồng Onboarding 0.1–0.5 (plan `docs/plans/OB-onboarding.md`). Mở từ `HomeActivity.onCreate` khi chưa xem xong.
 *
 * JS5: giao diện Compose ([OnboardingScreen]). Phần CỐ ĐỊNH (không trượt): nền aurora, thanh trên, chỉ báo bước, hàng nút; chỉ phần giữa trượt
 * ngang. Bấm nút lúc đang trượt bị bỏ qua (khóa trong [SLIDE_MS] kể từ lần đổi bước).
 */
@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    private val viewModel: OnboardingViewModel by viewModels()

    @Inject
    lateinit var defaultLauncherRepository: DefaultLauncherRepository

    private lateinit var defaultLauncher: DefaultLauncherAction
    private var stepChangedAt = 0L

    // Đọc lại ở onResume (về từ màn hệ thống): 0.5 hiện "Đã đặt" / "Chưa đặt".
    private var resumeTick by mutableIntStateOf(0)

    // Tắt hoạt ảnh hệ thống (thang hoạt ảnh = 0) thì trang đổi tức thì: không khóa nút (như bản View chỉ khóa khi slide đang chạy).
    private val transitioning: Boolean
        get() = ValueAnimator.areAnimatorsEnabled() && SystemClock.uptimeMillis() - stepChangedAt < SLIDE_MS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Đăng ký trong onCreate (trước STARTED): launcher kết quả của hộp thoại vai trò Home.
        defaultLauncher = DefaultLauncherAction(this, defaultLauncherRepository) { isDefault ->
            viewModel.onDefaultLauncherResult(isDefault)
        }
        val actions = OnboardingActions(
            onBack = { if (!transitioning) onBackStep() },
            onSkip = { if (!transitioning) viewModel.skip() },
            onPrimary = { if (!transitioning) viewModel.onPrimary() },
            onSecondary = { if (!transitioning) viewModel.onSecondary() },
            // LG: chip ngôn ngữ 0.1 mở Cài đặt → Ngôn ngữ (màn 1.3); chọn xong mọi Activity tạo lại, ViewModel giữ bước 0.1.
            onLanguage = {
                if (!transitioning) {
                    startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_SCREEN, SettingsScreenId.LANGUAGE.name))
                }
            },
            onChooseTheme = viewModel::chooseTheme,
            onChooseSwatch = viewModel::chooseSwatch,
            onJump = { if (!transitioning) viewModel.jumpTo(it) }
        )
        val languageName = AppLanguages.effective().nativeName
        setContent {
            val step by viewModel.step.collectAsStateWithLifecycle()
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val swatch by viewModel.swatch.collectAsStateWithLifecycle()
            @Suppress("UNUSED_VARIABLE") val tick = resumeTick // đọc để 0.5 dựng lại sau onResume
            OnboardingScreen(OnboardingUi(step, theme, swatch, viewModel.isDefaultLauncher(), languageName), actions)
        }
        reassertEdgeToEdge(window) // EF: Tecno Spark 40 (Android 15) mất tràn viền sau recreate()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!transitioning) onBackStep()
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    var first = true
                    viewModel.step.collect {
                        if (!first) stepChangedAt = SystemClock.uptimeMillis()
                        first = false
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            OnboardingEvent.Close -> {
                                // WI (chủ dự án 2026-11-18, thay HC Q3): xong Onboarding thì vào Cài đặt. CLEAR_TOP + SINGLE_TOP: mở từ Cài đặt
                                // (icon app) thì đưa đúng màn Cài đặt bên dưới lên, không tạo bản thứ hai; mở từ Home thì Cài đặt mở trên Home.
                                startActivity(
                                    Intent(this@OnboardingActivity, SettingsActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                )
                                finish()
                            }
                            OnboardingEvent.RequestDefaultLauncher -> {
                                OnboardingRuntime.markPendingDefaultLauncher() // Home mới (nếu có) mở thẳng 0.5
                                defaultLauncher.request()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Quay lại Onboarding (hủy hộp thoại, về từ màn hệ thống): không còn chờ đặt mặc định nữa.
        OnboardingRuntime.clearPendingDefaultLauncher()
        // Về từ màn hệ thống dự phòng (không có kết quả trả về) hoặc từ Cài đặt hệ thống: đã là mặc định thì đi tiếp.
        if (viewModel.step.value == OnboardingStep.DEFAULT_LAUNCHER && viewModel.isDefaultLauncher()) {
            viewModel.onDefaultLauncherResult(true)
        }
        resumeTick++
    }

    private fun onBackStep() {
        if (!viewModel.back()) finish() // 0.1: đóng, chưa đánh dấu xong (plan OB Q6)
    }

    companion object {
        private const val SLIDE_MS = 280L

        /** Extra: mở thẳng ở 0.5 (Home mới sau khi đặt mặc định ở 0.4). ViewModel đọc qua `SavedStateHandle`. */
        const val EXTRA_START_AT_DONE = "start_at_done"

        fun intent(context: Context, startAtDone: Boolean = false): Intent =
            Intent(context, OnboardingActivity::class.java).putExtra(EXTRA_START_AT_DONE, startAtDone)
    }
}