package gambi.global.os26launcher.ui.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gambi.global.os26launcher.core.ui.reassertEdgeToEdge
import gambi.global.os26launcher.domain.repository.DefaultLauncherRepository
import javax.inject.Inject

/**
 * RS Task 6 — màn 1.1 "Màn hình chính mặc định" (Figma 302:1738), mở từ trang chủ Cài đặt. Lưới 2 × 2 thẻ theme dùng lại
 * `ThemeCatalog` của onboarding 0.2. Nút "Áp dụng & đặt làm mặc định": ghi theme, rồi nếu app chưa là launcher mặc
 * định thì mở hộp thoại vai trò Home (`DefaultLauncherAction`, dùng chung với card Launcher hiện tại). Đã là mặc định thì nút chỉ
 * "Áp dụng" và ẩn dòng chú thích. Xong thì đóng màn, quay về trang chủ Cài đặt.
 *
 * JC: giao diện Compose ở [ThemeSettingsContent], kính Backdrop từ Android 12 (dưới đó là bề mặt DS tĩnh như trước).
 */
@AndroidEntryPoint
class ThemeSettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var defaultLauncherRepository: DefaultLauncherRepository

    private val viewModel: ThemeSettingsViewModel by viewModels()
    private lateinit var defaultLauncher: DefaultLauncherAction

    // Đã ghi theme và đang chờ người dùng chọn launcher: hộp thoại vai trò trả kết quả thì đóng; đi qua màn hệ thống dự phòng
    // (không có kết quả) thì kiểm lại ở onResume.
    private var waitingForRole = false

    // Đọc lại ở onResume (người dùng có thể đổi launcher mặc định ở ngoài rồi quay lại): đổi chữ nút và ẩn/hiện chú thích.
    private var isDefault by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        waitingForRole = savedInstanceState?.getBoolean(KEY_WAITING) ?: false

        // Đăng ký trong onCreate (trước STARTED): launcher kết quả của hộp thoại vai trò Home.
        defaultLauncher = DefaultLauncherAction(this, defaultLauncherRepository) {
            waitingForRole = false
            finish()
        }

        setContent {
            val chosen by viewModel.theme.collectAsStateWithLifecycle()
            val applying by viewModel.applying.collectAsStateWithLifecycle()
            ThemeSettingsContent(
                chosen = chosen,
                applying = applying,
                isDefault = isDefault,
                onChoose = viewModel::choose,
                onApply = ::onApply,
                onBack = ::finish
            )
        }
        reassertEdgeToEdge(window) // EF: Tecno Spark 40 (Android 15) mất tràn viền sau recreate()
    }

    override fun onResume() {
        super.onResume()
        val isDefaultNow = defaultLauncherRepository.isDefaultLauncher()
        if (waitingForRole && isDefaultNow) {
            finish()
            return
        }
        isDefault = isDefaultNow
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_WAITING, waitingForRole)
    }

    private fun onApply() {
        viewModel.apply {
            if (defaultLauncherRepository.isDefaultLauncher()) {
                finish()
            } else {
                waitingForRole = true
                defaultLauncher.request()
            }
        }
    }

    private companion object {
        const val KEY_WAITING = "waiting_for_role"
    }
}
