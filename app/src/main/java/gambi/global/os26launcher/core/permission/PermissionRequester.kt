package gambi.global.os26launcher.core.permission

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.compose.DsAlertDialog
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs

/**
 * PM (2026-11-26) — hai quyền runtime app xin: Vị trí gần đúng (thời tiết) và Danh bạ (tìm kiếm). [title] / [message] là nội dung hộp "bị chặn"
 * ([showPermissionBlockedDialog]): lời nhắn phải nói rõ quyền dùng để làm gì (yêu cầu chủ dự án).
 */
enum class PermissionKind(
    val permission: String,
    internal val key: String,
    @StringRes val title: Int,
    @StringRes val message: Int
) {
    LOCATION(Manifest.permission.ACCESS_COARSE_LOCATION, "location", R.string.perm_blocked_location_title, R.string.perm_blocked_location_message),
    CONTACTS(Manifest.permission.READ_CONTACTS, "contacts", R.string.perm_blocked_contacts_title, R.string.perm_blocked_contacts_message)
}

/**
 * PM R1, R5 — nhớ quyền nào ĐÃ bị người dùng từ chối rõ ràng (SharedPreferences `permission_denials`, khóa = [PermissionKind.key]; đọc đồng bộ
 * lúc bấm). Không lưu gì khác. Xem `docs/config-schema.md`.
 */
object PermissionDenials {
    private const val FILE = "permission_denials"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun wasDenied(context: Context, kind: PermissionKind): Boolean = prefs(context).getBoolean(kind.key, false)

    fun set(context: Context, kind: PermissionKind, denied: Boolean) {
        if (wasDenied(context, kind) != denied) prefs(context).edit().putBoolean(kind.key, denied).apply()
    }
}

/**
 * PM — xin một quyền theo luật chung của app (chủ dự án 2026-11-26):
 * - đã có quyền → [request] trả `true` ngay;
 * - bị chặn ([isBlocked]: đã từ chối rõ ràng và Android không hỏi nữa) → hộp riêng [showPermissionBlockedDialog], [request] trả `false`;
 * - còn lại → hộp của hệ thống; chạm ra ngoài hay Từ chối đều chỉ trả `false` (không Toast, không mở gì).
 *
 * "Từ chối rõ ràng" (R1): kết quả từ chối mà `shouldShowRequestPermissionRationale` trước HOẶC sau khi hỏi là true. Chạm ra ngoài hộp hệ thống
 * không đổi rationale nên không bị tính. Từ chối lần hai (Android 11+ chặn luôn) không hiện hộp riêng ngay; lần bấm sau mới hiện.
 *
 * Dựng lúc dựng Activity / màn (trước STARTED: `registerForActivityResult`). Lời gọi lại chờ kết quả không sống qua process death.
 *
 * [onSystemUi] (B21): gọi ngay khi sắp rời Activity sang màn của hệ thống (hộp xin quyền, trang thông tin ứng dụng), để Home bỏ qua lần
 * `onResume` kế tiếp (hộp "Đặt làm màn hình chính" hỏi mỗi lần Home resume).
 */
class PermissionRequester(
    private val activity: ComponentActivity,
    val kind: PermissionKind,
    private val onSystemUi: () -> Unit = {}
) {

    private var pending: ((Boolean) -> Unit)? = null
    private var rationaleBefore = false
    private var blockedDialog: DsDialogHandle? = null

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            PermissionDenials.set(activity, kind, false)
        } else if (rationaleBefore || rationale()) {
            PermissionDenials.set(activity, kind, true)
        }
        val callback = pending
        pending = null
        callback?.invoke(granted)
    }

    fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(activity, kind.permission) == PackageManager.PERMISSION_GRANTED

    /** Đã từ chối rõ ràng và Android không hiện hộp hỏi nữa (chỉ còn cấp ở trang thông tin ứng dụng). */
    fun isBlocked(): Boolean = !isGranted() && PermissionDenials.wasDenied(activity, kind) && !rationale()

    /**
     * Xin quyền; [onResult] nhận true khi đã có quyền. Bị chặn thì hiện hộp riêng và [onResult] nhận false ngay; [onOpenSettings] gọi khi người
     * dùng bấm "Cấp quyền" (trang thông tin ứng dụng đã mở), để màn gọi biết mà đọc lại quyền ở `onResume`.
     */
    fun request(onOpenSettings: () -> Unit = {}, onResult: (Boolean) -> Unit = {}) {
        if (isGranted()) {
            PermissionDenials.set(activity, kind, false)
            onResult(true)
            return
        }
        if (isBlocked()) {
            if (blockedDialog == null) {
                blockedDialog = showPermissionBlockedDialog(activity, kind) {
                    onSystemUi()
                    onOpenSettings()
                }.also { d ->
                    d.setOnDismissListener { blockedDialog = null }
                }
            }
            onResult(false)
            return
        }
        rationaleBefore = rationale()
        pending = onResult
        onSystemUi()
        launcher.launch(kind.permission)
    }

    /** Đóng hộp riêng nếu đang mở (gọi ở `onDestroy` của màn). */
    fun dismissDialog() {
        blockedDialog?.dismiss()
        blockedDialog = null
    }

    private fun rationale(): Boolean = ActivityCompat.shouldShowRequestPermissionRationale(activity, kind.permission)
}

/**
 * PM (Y3, Y5, Y6) — hộp xác nhận khi quyền [kind] bị chặn: tiêu đề + lý do cần quyền, nút "Đóng" và "Cấp quyền" (mở trang thông tin ứng dụng).
 * Chạm ra ngoài không làm gì, không đóng; Back = "Đóng" (R3).
 */
fun showPermissionBlockedDialog(activity: ComponentActivity, kind: PermissionKind, onOpenSettings: () -> Unit = {}): DsDialogHandle =
    activity.dsDialogs.show { handle ->
        DsAlertDialog(
            onDismiss = handle::dismiss,
            title = stringResource(kind.title),
            message = stringResource(kind.message),
            positive = stringResource(R.string.perm_blocked_grant) to {
                if (openAppDetailsSettings(activity)) onOpenSettings()
            },
            negative = stringResource(R.string.ds_close) to {},
            cancelable = true,
            dismissOnClickOutside = false
        )
    }

/** Mở trang thông tin ứng dụng của launcher (nơi cấp quyền). false = máy không có màn này. */
fun openAppDetailsSettings(activity: ComponentActivity): Boolean = try {
    activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
    true
} catch (e: ActivityNotFoundException) {
    false
}
