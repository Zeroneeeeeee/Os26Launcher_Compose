package gambi.global.os26launcher.ui.home.controller

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.repository.DefaultLauncherRepository
import gambi.global.os26launcher.ui.settings.DefaultLauncherAction
import gambi.global.os26launcher.ui.settings.SettingsDialogs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs

/**
 * PB Task 3 — hỏi "Đặt làm màn hình chính" mỗi lần Home vào RESUMED khi app chưa là launcher mặc định (plan PB, D1). Chưa mặc định thì
 * nút Home của máy mở launcher khác, nên Home chỉ resume khi người dùng chủ động mở app (icon, Gần đây, quay về từ Cài đặt).
 *
 * Bỏ qua khi: Onboarding chưa xong; lần resume đầu sau khi Onboarding đóng ([skipNextResume], bước 0.4 vừa hỏi); lần resume ngay sau khi chính
 * hộp này mở bộ chọn launcher (người dùng vừa Hủy thì không hỏi lại liền); hộp đang mở.
 *
 * Dựng ở `onCreate` (DefaultLauncherAction đăng ký ActivityResult trước STARTED).
 */
internal class DefaultLauncherPromptController(
    private val activity: AppCompatActivity,
    private val repository: DefaultLauncherRepository,
    private val isOnboardingDone: suspend () -> Boolean,
    onDefaultChanged: () -> Unit
) {
    private val action = DefaultLauncherAction(activity, repository) { onDefaultChanged() }
    private var dialog: DsDialogHandle? = null
    private var skipNext = false
    private var checkJob: Job? = null

    /** Lần resume kế tiếp không hỏi (vd Home vừa mở Onboarding). */
    fun skipNextResume() {
        skipNext = true
    }

    fun onResume() {
        if (skipNext) {
            skipNext = false
            return
        }
        if (dialog != null || repository.isDefaultLauncher()) return
        checkJob?.cancel()
        checkJob = activity.lifecycleScope.launch {
            val done = try {
                isOnboardingDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "read onboarding flag failed ${e.javaClass.name}")
                false
            }
            if (!done) return@launch
            // HW: chờ Home dựng xong rồi mới mở hộp. Mở ngay thì cửa sổ hộp thoại (kính) vẽ khung đầu chồng lên lúc Home dựng mới (vừa bấm
            // "Trải nghiệm" ở Cài đặt): đo Samsung A17 bản `perf` 2026-11-26 thêm một khung ~260 ms. onPause hủy lần chờ này.
            delay(PROMPT_DELAY_MS)
            // Đọc cờ và chờ là suspend: trong lúc chờ người dùng có thể đã rời Home hoặc vừa đặt mặc định.
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            if (dialog != null || repository.isDefaultLauncher()) return@launch
            if (activity.dsDialogs.hasOpen) return@launch // B21: không chồng lên hộp khác (hộp "Cấp quyền", bảng thời tiết…)
            show()
        }
    }

    fun onPause() {
        checkJob?.cancel()
        checkJob = null
    }

    fun onDestroy() {
        onPause()
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
    }

    private fun show() {
        val appName = activity.getString(R.string.app_name)
        dialog = SettingsDialogs.confirm(
            activity,
            activity.getString(R.string.onboarding_default_title, appName),
            activity.getString(R.string.onboarding_default_subtitle),
            activity.getString(R.string.default_prompt_set), // WI: chữ ngắn, hai nút cạnh nhau không bị cắt (0.4 giữ chữ đầy đủ)
            onPositive = {
                skipNext = true // bộ chọn launcher đóng -> Home resume: không hỏi lại ngay
                action.request()
            },
            negative = activity.getString(R.string.onboarding_later)
        ).also { d -> d.setOnDismissListener { dialog = null } }
    }

    private companion object {
        const val TAG = "DefaultLauncherPrompt"

        /** HW: hoãn mở hộp sau khi Home vào RESUMED, đủ để Home dựng mới qua các khung nặng đầu tiên (~1 giây trên A17 bản `perf`). */
        const val PROMPT_DELAY_MS = 800L
    }
}
