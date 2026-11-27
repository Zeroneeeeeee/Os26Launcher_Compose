package gambi.global.os26launcher.ui.controlcenter

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.domain.model.controlcenter.ControlPermission
import gambi.global.os26launcher.ui.settings.SettingsDialogs

/**
 * RST: "Cài đặt bị hạn chế" của Android 13+. App cài từ file APK (không qua kho ứng dụng) bị hệ thống chặn bật Truy cập thông báo:
 * Settings hiện hộp "Ứng dụng đã bị từ chối cấp quyền". App không tự gỡ được chặn, chỉ hướng dẫn người dùng mở khóa ở menu ⋮ của
 * trang Thông tin ứng dụng.
 *
 * Trạng thái chặn là appop `android:access_restricted_settings` của chính app: ERRORED = bị chặn, IGNORED = bị chặn và đã thấy hộp
 * từ chối (menu ⋮ đã có mục mở khóa), ALLOWED = đã mở khóa. Giá trị khác hoặc không đọc được thì đoán theo nguồn cài.
 */
object RestrictedSettings {

    private const val OP_ACCESS_RESTRICTED_SETTINGS = "android:access_restricted_settings"

    fun isRestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val mode = try {
            context.getSystemService(AppOpsManager::class.java)
                ?.unsafeCheckOpNoThrow(OP_ACCESS_RESTRICTED_SETTINGS, Process.myUid(), context.packageName)
        } catch (e: RuntimeException) {
            null // ROM không có op này (IllegalArgumentException) hoặc chặn truy vấn
        }
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> false
            AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> true
            else -> installedFromFile(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun installedFromFile(context: Context): Boolean = try {
        val source = context.packageManager.getInstallSourceInfo(context.packageName).packageSource
        source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE || source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
    } catch (e: Exception) {
        false
    }

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
}

/**
 * RST: mở màn Truy cập thông báo, và nếu quay lại mà vẫn chưa cấp vì máy chặn "Cài đặt bị hạn chế" thì hiện hộp hướng dẫn mở khóa.
 * Máy không bị chặn thì không bao giờ hiện hộp.
 *
 * Luồng: [open] mở màn quyền -> hệ thống từ chối (lần này menu ⋮ mới có mục mở khóa) -> [onResume] hiện hộp hướng dẫn -> "Mở thông tin
 * ứng dụng" -> người dùng bật "Cho phép cài đặt bị hạn chế" -> [onResume] thấy đã mở khóa thì tự mở lại màn Truy cập thông báo.
 *
 * Chủ sở hữu gọi [onResume] ở `onResume` và [onDestroy] ở `onDestroy`. [onGuideDeclined]: người dùng bấm "Để sau" / Back ở hộp hướng dẫn.
 */
class NotificationListenerFlow(
    private val activity: ComponentActivity,
    private val isGranted: () -> Boolean,
    private val onGuideDeclined: () -> Unit = {}
) {
    private enum class Pending { NONE, LISTENER, APP_DETAILS }

    private var pending = Pending.NONE
    private var dialog: DsDialogHandle? = null

    val isShowingGuide: Boolean get() = dialog?.isShowing == true

    /** Mở màn Truy cập thông báo; false nếu máy không mở được màn nào. */
    fun open(): Boolean {
        val ok = SystemIntents.startFirst(
            activity, SystemIntents.forPermission(activity, ControlPermission.NOTIFICATION_LISTENER), newTask = false
        )
        pending = if (ok) Pending.LISTENER else Pending.NONE
        return ok
    }

    /** Trả true nếu đã hiện hộp hướng dẫn hoặc tự mở màn hệ thống (chủ sở hữu khỏi hiện hộp khác). */
    fun onResume(): Boolean {
        val was = pending
        pending = Pending.NONE
        if (was == Pending.NONE || isGranted()) return false
        val restricted = RestrictedSettings.isRestricted(activity)
        return when (was) {
            Pending.LISTENER -> if (restricted) showGuide() else false
            Pending.APP_DETAILS -> if (!restricted) open() else false // vừa mở khóa: đưa thẳng tới màn quyền
            Pending.NONE -> false
        }
    }

    fun onDestroy() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
    }

    private fun showGuide(): Boolean {
        if (isShowingGuide) return true
        dialog = SettingsDialogs.confirm(
            activity = activity,
            title = activity.getString(R.string.restricted_settings_title),
            message = activity.getString(R.string.restricted_settings_message),
            positive = activity.getString(R.string.restricted_settings_open),
            onPositive = ::openAppDetails,
            negative = activity.getString(R.string.cc_perm_later),
            onNegative = onGuideDeclined,
            onCancel = onGuideDeclined
        ).also { handle -> handle.setOnDismissListener { if (dialog === handle) dialog = null } }
        return true
    }

    private fun openAppDetails() {
        if (SystemIntents.startFirst(activity, listOf(RestrictedSettings.appDetailsIntent(activity)), newTask = false)) {
            pending = Pending.APP_DETAILS
        } else {
            Toast.makeText(activity, R.string.cc_perm_open_failed, Toast.LENGTH_LONG).show()
        }
    }
}
