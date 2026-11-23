package gambi.global.os26launcher.ui.controlcenter

import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import gambi.global.os26launcher.domain.model.controlcenter.ControlPermission
import gambi.global.os26launcher.domain.model.controlcenter.SystemPanel

/**
 * G3d: đổi [SystemPanel] và [ControlPermission] thành Intent (domain không biết Intent). Mỗi hàm trả danh sách theo thứ tự ưu
 * tiên: ROM nào thiếu màn thứ nhất thì thử màn kế (vd `Settings.Panel` chỉ có từ API 29).
 */
internal object SystemIntents {

    fun forPanel(context: Context, panel: SystemPanel): List<Intent> = when (panel) {
        SystemPanel.WIFI -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI) else null,
            Intent(Settings.ACTION_WIFI_SETTINGS)
        )

        SystemPanel.INTERNET -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else null,
            Intent(Settings.ACTION_DATA_USAGE_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )

        SystemPanel.AIRPLANE -> listOf(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
        SystemPanel.BLUETOOTH_ENABLE -> listOf(
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        )

        // Action không có trong SDK công khai nhưng app Cài đặt của AOSP nhận (RequestPermissionActivity), giống REQUEST_ENABLE:
        // hiện hộp "Một ứng dụng muốn tắt Bluetooth". App mẫu dùng đúng intent này (RE mục 2.6). ROM nào thiếu thì mở Cài đặt.
        SystemPanel.BLUETOOTH_DISABLE -> listOf(Intent(ACTION_REQUEST_DISABLE), Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        SystemPanel.BLUETOOTH_SETTINGS -> listOf(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        // RE mục 2.6: app mẫu mở TETHER_SETTINGS (action ẩn nhưng mọi ROM đều có), dự phòng WIRELESS_SETTINGS.
        SystemPanel.TETHER -> listOf(Intent(ACTION_TETHER_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
        SystemPanel.TIMER -> listOf(Intent(AlarmClock.ACTION_SHOW_TIMERS), Intent(AlarmClock.ACTION_SHOW_ALARMS))
        SystemPanel.CALCULATOR -> listOf(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
        SystemPanel.CAMERA -> listOf(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        SystemPanel.BATTERY_SAVER -> listOf(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        // RE mục 5.3: app mẫu mở DARK_THEME_SETTINGS (action ẩn), dự phòng DISPLAY_SETTINGS. App thường không tự đổi chế độ tối được.
        SystemPanel.DARK_MODE -> listOf(Intent(ACTION_DARK_THEME_SETTINGS), Intent(Settings.ACTION_DISPLAY_SETTINGS))
        SystemPanel.SETTINGS -> listOf(Intent(Settings.ACTION_SETTINGS))
        SystemPanel.VOICE_MEMO -> listOfNotNull(
            Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION),
            launcherByPackageHint(context, listOf("recorder", "soundrecorder", "voice"))
        )

        SystemPanel.NOTES -> listOfNotNull(launcherByPackageHint(context, listOf("notes", "note", "keep", "memo")))
        SystemPanel.DND_SETTINGS -> listOf(Intent(ACTION_ZEN_MODE_SETTINGS), Intent(Settings.ACTION_SOUND_SETTINGS))
        // TF: màn truyền màn hình của hệ thống (Samsung mở Smart View); ROM thiếu thì Kết nối không dây.
        SystemPanel.SCREEN_MIRROR -> listOf(Intent(Settings.ACTION_CAST_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
        SystemPanel.SOUND_OUTPUT -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_VOLUME) else null,
            Intent(Settings.ACTION_SOUND_SETTINGS)
        )
    }

    /**
     * RE mục 5.3: app mẫu tìm app có tên gói chứa "note"/"voice"… (Android không có intent chuẩn cho Ghi chú). Ta tìm trong các
     * activity MAIN/LAUNCHER (đã khai `<queries>` từ M2, không cần QUERY_ALL_PACKAGES). Ưu tiên gợi ý đứng trước.
     */
    private fun launcherByPackageHint(context: Context, hints: List<String>): Intent? {
        val pm = context.packageManager
        val all = try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        } catch (e: RuntimeException) {
            return null
        }
        for (hint in hints) {
            val match = all.firstOrNull { it.activityInfo.packageName.lowercase().contains(hint) } ?: continue
            return Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(match.activityInfo.packageName, match.activityInfo.name))
        }
        return null
    }

    fun forPermission(context: Context, permission: ControlPermission): List<Intent> {
        val pkg = Uri.fromParts("package", context.packageName, null)
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        return when (permission) {
            ControlPermission.OVERLAY -> listOf(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg), appDetails)
            ControlPermission.NOTIFICATION_LISTENER -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName(context, ControlCenterService::class.java).flattenToString()
                    )
                } else null,
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            )

            ControlPermission.BATTERY_OPTIMIZATION -> listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appDetails)
            ControlPermission.WRITE_SETTINGS -> listOf(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg), appDetails)
            ControlPermission.DND_ACCESS -> listOf(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            ControlPermission.BLUETOOTH_CONNECT -> listOf(appDetails)
        }
    }

    /** Thử lần lượt; trả false nếu không màn nào mở được. Gọi từ service thì cần [Intent.FLAG_ACTIVITY_NEW_TASK]. */
    fun startFirst(context: Context, intents: List<Intent>, newTask: Boolean): Boolean {
        for (intent in intents) {
            if (newTask) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (e: RuntimeException) {
                // ActivityNotFoundException hoặc SecurityException: thử màn kế
            }
        }
        return false
    }

    private const val ACTION_TETHER_SETTINGS = "android.settings.TETHER_SETTINGS"
    private const val ACTION_REQUEST_DISABLE = "android.bluetooth.adapter.action.REQUEST_DISABLE"
    private const val ACTION_DARK_THEME_SETTINGS = "android.settings.DARK_THEME_SETTINGS"
    private const val ACTION_ZEN_MODE_SETTINGS = "android.settings.ZEN_MODE_SETTINGS"
}
