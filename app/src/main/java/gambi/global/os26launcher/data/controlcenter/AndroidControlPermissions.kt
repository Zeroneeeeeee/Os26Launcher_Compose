package gambi.global.os26launcher.data.controlcenter

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.domain.controlcenter.ControlPermissionsPort
import gambi.global.os26launcher.domain.model.controlcenter.ControlPermission
import javax.inject.Inject
import javax.inject.Singleton

/** G3d: kiểm tra quyền đặc biệt (RE mục 2.1: app mẫu kiểm đúng các quyền này trước khi hiện dải mép). */
@Singleton
class AndroidControlPermissions @Inject constructor(
    @ApplicationContext private val context: Context
) : ControlPermissionsPort {

    override fun isGranted(permission: ControlPermission): Boolean = try {
        when (permission) {
            ControlPermission.OVERLAY -> Settings.canDrawOverlays(context)
            ControlPermission.NOTIFICATION_LISTENER ->
                NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

            ControlPermission.BATTERY_OPTIMIZATION ->
                context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

            ControlPermission.WRITE_SETTINGS -> Settings.System.canWrite(context)
            ControlPermission.DND_ACCESS ->
                context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

            ControlPermission.BLUETOOTH_CONNECT -> Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        }
    } catch (e: RuntimeException) {
        false // ROM ném SecurityException/IllegalState cho vài truy vấn: coi như chưa cấp
    }

    override fun applicable(): List<ControlPermission> = ControlPermission.entries.filter {
        // BLUETOOTH_CONNECT chỉ có từ API 31: 31-32 để bật/tắt trực tiếp, 33+ để mở hộp thoại ACTION_REQUEST_ENABLE (D6).
        it != ControlPermission.BLUETOOTH_CONNECT || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}
