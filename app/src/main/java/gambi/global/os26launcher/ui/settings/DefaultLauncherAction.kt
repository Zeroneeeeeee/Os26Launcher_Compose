package gambi.global.os26launcher.ui.settings

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.repository.DefaultLauncherRepository

/**
 * G2d — luồng "đặt launcher mặc định" của Cài đặt (chuyển nguyên từ SettingsActivity). Dựng một lần trong `onCreate` của khung
 * (launcher phải đăng ký trước STARTED); các màn gọi [request].
 *
 * OB: nhận thẳng [DefaultLauncherRepository] (không còn cần `SettingsViewModel`) để Onboarding 0.4 dùng chung. [onResult] gọi sau mỗi
 * lần hộp thoại vai trò Home đóng, với trạng thái mặc định lúc đó (đi qua màn hệ thống dự phòng thì không có kết quả: màn gọi tự
 * đọc lại ở `onResume`).
 */
class DefaultLauncherAction(
    private val activity: AppCompatActivity,
    private val repository: DefaultLauncherRepository,
    private val onResult: (isDefault: Boolean) -> Unit = {}
) {
    // B12: elapsedRealtime lúc mở hộp thoại xin vai trò Home; 0 = không biết (Activity dựng lại giữa chừng).
    private var roleRequestAt = 0L

    // RESULT_CANCELED quay về nhanh hơn ROLE_DIALOG_NEVER_SHOWN_MS = hộp thoại không hiện (người dùng không kịp nhìn rồi chạm):
    // mở màn hệ thống dự phòng. Người dùng tự bấm Hủy thì ở lại.
    private val requestDefault =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val startedAt = roleRequestAt
            roleRequestAt = 0L
            if (startedAt == 0L) return@registerForActivityResult
            val isDefault = repository.isDefaultLauncher()
            if (result.resultCode != Activity.RESULT_OK) {
                val tooFast = SystemClock.elapsedRealtime() - startedAt < ROLE_DIALOG_NEVER_SHOWN_MS
                if (tooFast && !isDefault) openFallback()
            }
            onResult(isDefault)
        }

    fun request() {
        val role = repository.createRoleRequestIntent()
        if (role == null) { // ROM không có role: thẳng màn hệ thống, không đo thời gian (tránh vòng lặp)
            openFallback()
            return
        }
        try {
            roleRequestAt = SystemClock.elapsedRealtime()
            requestDefault.launch(role)
        } catch (e: RuntimeException) {
            // ActivityNotFoundException hoặc SecurityException: ROM không cho mở dù resolve được
            roleRequestAt = 0L
            openFallback()
        }
    }

    /** Màn Ứng dụng màn hình chính của hệ thống, rồi Cài đặt chung (B12). Mở bằng startActivity thường: không chờ kết quả. */
    private fun openFallback() {
        for (intent in repository.createFallbackIntents()) {
            try {
                activity.startActivity(intent)
                return
            } catch (e: RuntimeException) {
                // thử màn kế tiếp
            }
        }
        Toast.makeText(activity, R.string.home_set_default_failed, Toast.LENGTH_LONG).show()
    }

    private companion object {
        /** CHỈNH TAY (B12, đo Live 4 2026-11-11, bản debug): max(300, 2 x 176 ms lần "không hiện" lớn nhất); Hủy thật nhanh nhất ~1309 ms. */
        const val ROLE_DIALOG_NEVER_SHOWN_MS = 352L
    }
}
