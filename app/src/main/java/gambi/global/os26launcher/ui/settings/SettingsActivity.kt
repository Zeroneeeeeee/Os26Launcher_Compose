package gambi.global.os26launcher.ui.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.compose.DsScreen
import gambi.global.os26launcher.core.designsystem.compose.DsSpinner
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs
import gambi.global.os26launcher.core.ui.reassertEdgeToEdge
import gambi.global.os26launcher.domain.config.AppStateRepository
import gambi.global.os26launcher.domain.repository.DefaultLauncherRepository
import gambi.global.os26launcher.ui.home.HomeWarmup
import gambi.global.os26launcher.ui.onboarding.OnboardingActivity
import gambi.global.os26launcher.ui.settings.compose.SettingsList
import gambi.global.os26launcher.ui.settings.compose.listActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * M8a Task 3 — khung màn Cài đặt (mở từ nút "Cài đặt" trong edit mode ở Home). G2d: Activity chỉ là khung; màn cụ thể là một
 * [SettingsScreen] chọn bằng extra [EXTRA_SCREEN] (thiếu/lạ thì về màn mặc định của [SettingsScreenId.fromExtra]).
 *
 * JS1: khung vẽ bằng Compose (`DsScreen` + `SettingsList`), thay `activity_settings.xml` + `SettingsAdapter`. Các màn giữ nguyên hợp đồng
 * `SettingsScreen` (hàng + lệnh); hộp thoại của màn vẽ qua `dsDialogs` của Activity này.
 */
@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var defaultLauncherRepository: DefaultLauncherRepository

    @Inject
    lateinit var appState: AppStateRepository // HC: cờ Onboarding khi vào app từ icon // OB: DefaultLauncherAction nhận thẳng repository

    @Inject
    lateinit var homeWarmup: HomeWarmup // HW

    private val viewModel: SettingsViewModel by viewModels()
    private var lastState: SettingsUiState? = null
    private lateinit var screen: SettingsScreen

    // Trạng thái Compose của khung.
    private var rows by mutableStateOf<List<SettingsRow>>(emptyList())
    private var loading by mutableStateOf(true)
    private var busy by mutableStateOf(false)
    private var resetKey by mutableIntStateOf(0)
    private var covered by mutableStateOf(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false) // RS: nền aurora tràn dưới thanh hệ thống

        // Launcher đăng ký trong lúc dựng (trước STARTED) và theo thứ tự cố định: khung trước, màn sau.
        val context = SettingsScreenContext(
            activity = this,
            viewModel = viewModel,
            defaultLauncher = DefaultLauncherAction(this, defaultLauncherRepository),
            onRerender = { force -> lastState?.let { render(it, force) } },
            onOpen = { id ->
                startActivity(
                    Intent(this, SettingsActivity::class.java).putExtra(EXTRA_SCREEN, id.name)
                )
            }
        )
        screen = SettingsScreens.create(
            SettingsScreenId.fromExtra(intent.getStringExtra(EXTRA_SCREEN)),
            context
        ) ?: SettingsHubScreen(context) // mã chưa đăng ký (extra lạ) thì về trang chủ
        val actions = screen.listActions()
        val showBack = screen !is SettingsHubScreen // RS: trang chủ không có nút quay lại (Figma 302:1519)

        setContent {
            // HC: che nội dung (nền vẫn tối) trong lúc đọc cờ Onboarding, để không thấy Cài đặt lóe lên.
            DsScreen(title = stringResource(screen.titleRes), onBack = if (showBack) ::finish else null, modifier = Modifier.alpha(if (covered) 0f else 1f)) {
                Box(Modifier.weight(1f).fillMaxSize()) {
                    if (!loading) SettingsList(rows, actions, resetKey = resetKey)
                    if (loading || busy) DsSpinner(stringResource(R.string.settings_loading), Modifier.align(Alignment.Center))
                }
            }
            dsDialogs.Render()
        }
        reassertEdgeToEdge(window) // EF: Tecno Spark 40 (Android 15) mất tràn viền sau recreate()

        if (savedInstanceState == null && isLauncherEntry()) startOnboardingIfNeeded()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it, forceRebind = false) } }
                launch { viewModel.events.collect(::handleEvent) }
                launch { screen.observe() }
            }
        }
    }

    override fun onDestroy() {
        screen.onDestroy()
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        screen.onStop() // HL-2
        uncover() // HC: Onboarding đã che màn này
    }

    /** HC Task 2: mở từ icon app (qua [LauncherEntryActivity]), không phải từ edit mode của Home. */
    private fun isLauncherEntry(): Boolean = intent?.getBooleanExtra(EXTRA_FROM_LAUNCHER, false) == true

    /**
     * HC Task 2: lần đầu (Onboarding chưa xong) mở Onboarding ngay khi vào app từ icon. Che nội dung trong lúc đọc cờ; hiện lại khi bị che
     * (onStop), đọc xong mà không cần Onboarding, hoặc quá [ONBOARDING_HOLD_MAX_MS].
     */
    private fun startOnboardingIfNeeded() {
        covered = true
        window.decorView.postDelayed({ uncover() }, ONBOARDING_HOLD_MAX_MS)
        lifecycleScope.launch {
            val done = try {
                withTimeoutOrNull(ONBOARDING_HOLD_MAX_MS) { appState.state.first().onboardingDone } ?: true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "read onboarding flag failed ${e.javaClass.name}")
                true
            }
            if (done) uncover() else startActivity(OnboardingActivity.intent(this@SettingsActivity, startAtDone = false))
        }
    }

    private fun uncover() {
        covered = false
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDefaultLauncher()
        screen.onResume()
        // RS: kính của card đọc GlassEnvironment trực tiếp (rememberGlassLook) nên đổi ở màn Tinh chỉnh kính tự áp, không cần dựng lại.
        lastState?.let { render(it, forceRebind = false) }
        // HW: trang chủ có Hero Card ("Trải nghiệm" / "Khám phá ›" mở Home): giải mã sẵn ảnh nền + bản mờ để Home mở không khựng, đen màn.
        if (screen is SettingsHubScreen) homeWarmup.warmAfterNextFrame(this)
    }

    private fun render(state: SettingsUiState, forceRebind: Boolean) {
        loading = state.loading
        busy = state.applyingGrid
        lastState = state
        if (state.loading) return
        rows = screen.rows(state)
        if (forceRebind) resetKey++
    }

    private fun handleEvent(event: SettingsEvent) {
        if (event is SettingsEvent.Message) {
            Toast.makeText(
                this,
                getString(event.res, event.arg ?: getString(R.string.common_unknown)),
                Toast.LENGTH_LONG
            ).show()
            // Ghi lỗi thì flow không đổi nên công tắc đã bật/tắt trên màn vẫn lệch: dựng lại từ trạng thái thật.
            render(viewModel.uiState.value, forceRebind = true)
        } else {
            screen.onEvent(event)
        }
    }

    companion object {
        /** Tên một [SettingsScreenId]; thiếu/lạ thì về màn mặc định. */
        const val EXTRA_SCREEN = "settings_screen"

        /** HC Task 2: [LauncherEntryActivity] đặt true (vào app từ icon): lần đầu mở Onboarding. */
        const val EXTRA_FROM_LAUNCHER = "from_launcher"
        private const val TAG = "SettingsActivity"
        private const val ONBOARDING_HOLD_MAX_MS = 1500L // HC: cùng trần với HomeActivity
    }
}
