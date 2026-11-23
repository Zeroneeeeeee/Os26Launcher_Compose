package gambi.global.os26launcher.ui.settings

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import gambi.global.os26launcher.R

/**
 * HL-2 — xác thực để xem "Ứng dụng ẩn" bằng khóa của máy (phương án A, chủ dự án 2026-11-20):
 * - sinh trắc **loại mạnh** (`BIOMETRIC_STRONG`: vân tay, khuôn mặt loại 3; không nhận khuôn mặt loại yếu, Q2);
 * - hoặc mã PIN / hình vẽ / mật khẩu khóa màn hình của máy.
 *
 * Theo API (tài liệu Android: `DEVICE_CREDENTIAL` và `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` không hỗ trợ trên API 29 trở xuống):
 * - API 30+: một hộp `BiometricPrompt` với `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`.
 * - API 26–29: có sinh trắc loại mạnh thì hộp `BIOMETRIC_STRONG` kèm nút "Dùng mã khóa máy"; bấm nút đó (hoặc máy không có sinh trắc
 *   loại mạnh) thì mở màn xác thực của hệ thống `KeyguardManager.createConfirmDeviceCredentialIntent`.
 *
 * Phải tạo trong lúc dựng màn (trước STARTED) vì đăng ký ActivityResult. [inProgress] = đang có hộp/màn xác thực mở: màn gọi không được khóa
 * lại ở onStop trong lúc này (màn xác thực của hệ thống ở API 26–29 là Activity khác, làm màn gọi onStop).
 */
class HiddenAppsAuthenticator(private val activity: FragmentActivity) {

    private val keyguard = activity.getSystemService(KeyguardManager::class.java)
    private var onSuccess: () -> Unit = {}
    private var onCancel: () -> Unit = {}

    /** Đang có hộp hoặc màn xác thực mở. */
    var inProgress = false
        private set

    private val credentialLauncher: ActivityResultLauncher<android.content.Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            finish(result.resultCode == Activity.RESULT_OK)
        }

    /** Máy có khóa màn hình (PIN / hình vẽ / mật khẩu). Không có thì không khóa được (Q5: vào thẳng, ẩn công tắc). */
    fun isDeviceSecure(): Boolean = keyguard?.isDeviceSecure == true

    /** Mở xác thực. Chỉ một phiên một lúc: đang mở thì bỏ qua. */
    fun authenticate(onSuccess: () -> Unit, onCancel: () -> Unit) {
        if (inProgress) return
        this.onSuccess = onSuccess
        this.onCancel = onCancel
        inProgress = true
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> prompt(allowDeviceCredential = true)
                BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS ->
                    prompt(allowDeviceCredential = false)
                else -> confirmDeviceCredential()
            }
        } catch (e: RuntimeException) { // hộp không mở được (máy lạ): coi như hủy, không bao giờ mở toang
            Log.w(TAG, "authenticate failed", e)
            finish(false)
        }
    }

    private fun prompt(allowDeviceCredential: Boolean) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.hl_auth_title))
            .setSubtitle(activity.getString(R.string.hl_auth_subtitle))
            .apply {
                if (allowDeviceCredential) {
                    setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) // không được kèm nút phủ định
                } else {
                    setAllowedAuthenticators(BIOMETRIC_STRONG)
                    setNegativeButtonText(activity.getString(R.string.hl_auth_use_device))
                }
            }
            .build()
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = finish(true)

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!allowDeviceCredential && errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    confirmDeviceCredential() // API 26–29: "Dùng mã khóa máy"
                    return
                }
                Log.d(TAG, "auth error $errorCode")
                finish(false)
            }
            // onAuthenticationFailed: vân tay không khớp, hộp vẫn mở cho thử lại -> không làm gì.
        }
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }

    @Suppress("DEPRECATION") // createConfirmDeviceCredentialIntent: đường chính thức cho API 26–29
    private fun confirmDeviceCredential() {
        val intent = keyguard?.createConfirmDeviceCredentialIntent(
            activity.getString(R.string.hl_auth_title),
            activity.getString(R.string.hl_auth_subtitle)
        )
        if (intent == null) { // máy không có khóa màn hình: màn gọi đã kiểm isDeviceSecure, tới đây là bất thường -> hủy
            finish(false)
            return
        }
        credentialLauncher.launch(intent)
    }

    private fun finish(ok: Boolean) {
        inProgress = false
        if (ok) onSuccess() else onCancel()
    }

    private companion object {
        const val TAG = "HiddenAppsAuth"
    }
}
