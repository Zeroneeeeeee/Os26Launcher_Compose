package gambi.global.os26launcher.data.controlcenter

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.domain.controlcenter.ControlPermissionsPort
import gambi.global.os26launcher.domain.controlcenter.SystemControlsPort
import gambi.global.os26launcher.domain.model.controlcenter.ControlCenterState
import gambi.global.os26launcher.domain.model.controlcenter.ControlPermission
import gambi.global.os26launcher.domain.model.controlcenter.ControlResult
import gambi.global.os26launcher.domain.model.controlcenter.ControlTile
import gambi.global.os26launcher.domain.model.controlcenter.SystemPanel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * G3d: hiện thực [SystemControlsPort] bằng API công khai của Android. Cách bật/tắt từng ô theo D5-D8 của plan, học từ app mẫu
 * (RE mục 2.6 và 5): API nào không cho app thường tự làm thì trả [ControlResult.OpenSystemPanel].
 *
 * Mọi lời gọi hệ thống bọc `try/catch`: ROM khác nhau ném SecurityException/IllegalArgument ở những chỗ tài liệu không nói.
 */
@Singleton
class AndroidSystemControls @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: ControlPermissionsPort
) : SystemControlsPort {

    private val wifi: WifiManager? = context.applicationContext.getSystemService(WifiManager::class.java)
    private val bluetooth: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val notifications: NotificationManager? = context.getSystemService(NotificationManager::class.java)
    private val camera: CameraManager? = context.getSystemService(CameraManager::class.java)
    private val power: PowerManager? = context.getSystemService(PowerManager::class.java)
    private val telephony: TelephonyManager? = context.getSystemService(TelephonyManager::class.java)
    private val resolver: ContentResolver get() = context.contentResolver

    /** Camera có đèn flash đầu tiên; null = máy không có đèn pin. Tra một lần. */
    private val torchCameraId: String? by lazy {
        val cm = camera ?: return@lazy null
        try {
            cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Hệ thống không có API đọc trạng thái đèn pin, chỉ có callback: giữ giá trị cuối callback báo. */
    @Volatile
    private var torchOn = false

    override fun snapshot(): ControlCenterState {
        // Broadcast "dính" của pin: đọc không cần đăng ký receiver thật (receiver null).
        val battery = safe(null) { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
        val level = battery?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0 && s > 0) (l * 100 / s) else -1
        } ?: -1
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        return ControlCenterState(
            wifiOn = safe(false) { wifi?.isWifiEnabled == true },
            bluetoothOn = safe(false) { bluetoothEnabled() },
            bluetoothAvailable = bluetooth != null,
            airplaneOn = safe(false) { Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 },
            syncOn = safe(false) { ContentResolver.getMasterSyncAutomatically() },
            torchOn = torchOn,
            torchAvailable = torchCameraId != null,
            rotationLocked = safe(false) { Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0 },
            dndOn = safe(false) {
                val filter = notifications?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
                filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            },
            brightness = safe(0.5f) {
                Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128) / BRIGHTNESS_MAX
            }.coerceIn(0f, 1f),
            autoBrightness = safe(false) {
                Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
                        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            },
            volume = safe(0.5f) {
                val am = audio ?: return@safe 0.5f
                am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                        am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            }.coerceIn(0f, 1f),
            screenTimeoutMs = safe(30_000) { Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000) },
            batteryLevel = level,
            charging = plugged,
            powerSave = safe(false) { power?.isPowerSaveMode == true },
            darkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES ||
                    safe(false) { context.getSystemService(UiModeManager::class.java)?.nightMode == UiModeManager.MODE_NIGHT_YES },
            carrier = safe("") { telephony?.networkOperatorName.orEmpty() }
        )
    }

    override fun observe(): Flow<ControlCenterState> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        val emit = { trySend(snapshot()) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                emit()
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(VOLUME_CHANGED_ACTION)
        }
        // Broadcast hệ thống vẫn tới receiver NOT_EXPORTED (chỉ chặn broadcast từ app khác).
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                emit()
            }
        }
        safe(Unit) {
            for (name in listOf(
                Settings.System.SCREEN_BRIGHTNESS,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.ACCELEROMETER_ROTATION,
                Settings.System.SCREEN_OFF_TIMEOUT
            )) resolver.registerContentObserver(Settings.System.getUriFor(name), false, observer)
        }
        val syncHandle = safe(null) {
            ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS) { handler.post { emit() } }
        }
        val torchCallback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchCameraId) {
                    torchOn = enabled
                    emit()
                }
            }
        }
        safe(Unit) { camera?.registerTorchCallback(torchCallback, handler) }
        emit()
        awaitClose {
            safe(Unit) { context.unregisterReceiver(receiver) }
            safe(Unit) { resolver.unregisterContentObserver(observer) }
            safe(Unit) { syncHandle?.let(ContentResolver::removeStatusChangeListener) }
            safe(Unit) { camera?.unregisterTorchCallback(torchCallback) }
        }
    }.conflate().distinctUntilChanged()

    override fun toggle(tile: ControlTile): ControlResult = when (tile) {
        ControlTile.WIFI -> toggleWifi()
        ControlTile.BLUETOOTH -> toggleBluetooth()
        ControlTile.CELLULAR -> ControlResult.OpenSystemPanel(SystemPanel.INTERNET)
        ControlTile.AIRPLANE -> ControlResult.OpenSystemPanel(SystemPanel.AIRPLANE)
        ControlTile.HOTSPOT -> ControlResult.OpenSystemPanel(SystemPanel.TETHER)
        ControlTile.SYNC -> toggleSync()
        ControlTile.ROTATION_LOCK -> toggleRotationLock()
        ControlTile.DND -> toggleDnd()
        ControlTile.FLASHLIGHT -> toggleTorch()
    }

    override fun setBrightness(value: Float): ControlResult = writeSettings {
        // Kéo thanh = chỉnh tay: tắt độ sáng tự động, nếu không hệ thống ghi đè ngay.
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS,
            (value.coerceIn(MIN_BRIGHTNESS_FRACTION, 1f) * BRIGHTNESS_MAX).roundToInt()
        )
    }

    override fun toggleAutoBrightness(): ControlResult = writeSettings {
        val auto = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL else Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        )
    }

    override fun setScreenTimeout(millis: Int): ControlResult = writeSettings {
        Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, millis)
    }

    override fun setVolume(value: Float): ControlResult = safe(ControlResult.Unavailable) {
        val am = audio ?: return@safe ControlResult.Unavailable
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        // SecurityException khi máy đang Không làm phiền và app không có quyền chính sách thông báo.
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (value.coerceIn(0f, 1f) * max).roundToInt(), 0)
        ControlResult.Done
    }

    private inline fun writeSettings(block: () -> Unit): ControlResult {
        if (!permissions.isGranted(ControlPermission.WRITE_SETTINGS)) {
            return ControlResult.NeedsPermission(ControlPermission.WRITE_SETTINGS)
        }
        return safe(ControlResult.Unavailable) {
            block()
            ControlResult.Done
        }
    }

    /** D5: từ API 29 `setWifiEnabled` không còn tác dụng với app thường. */
    @Suppress("DEPRECATION")
    private fun toggleWifi(): ControlResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return ControlResult.OpenSystemPanel(SystemPanel.WIFI)
        val wm = wifi ?: return ControlResult.Unavailable
        return safe(ControlResult.Unavailable) {
            wm.isWifiEnabled = !wm.isWifiEnabled
            ControlResult.Done
        }
    }

    /** D6: API 33+ `enable()/disable()` không còn tác dụng (target 33+), nên đi qua intent hệ thống. */
    @SuppressLint("MissingPermission") // đã kiểm BLUETOOTH_CONNECT ở API 31-32; dưới 31 dùng BLUETOOTH_ADMIN (maxSdk 30)
    @Suppress("DEPRECATION")
    private fun toggleBluetooth(): ControlResult {
        val adapter = bluetooth ?: return ControlResult.Unavailable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Hộp thoại bật/tắt của hệ thống (REQUEST_ENABLE / REQUEST_DISABLE) cần BLUETOOTH_CONNECT; chưa có thì xin quyền một
            // lần ở màn cài đặt Trung tâm điều khiển, sau đó mỗi lần bấm chỉ còn một hộp xác nhận.
            if (!permissions.isGranted(ControlPermission.BLUETOOTH_CONNECT)) {
                return ControlResult.NeedsPermission(ControlPermission.BLUETOOTH_CONNECT)
            }
            return ControlResult.OpenSystemPanel(
                if (bluetoothEnabled()) SystemPanel.BLUETOOTH_DISABLE else SystemPanel.BLUETOOTH_ENABLE
            )
        }
        if (!permissions.isGranted(ControlPermission.BLUETOOTH_CONNECT)) {
            return ControlResult.NeedsPermission(ControlPermission.BLUETOOTH_CONNECT)
        }
        return safe(ControlResult.Unavailable) {
            if (adapter.isEnabled) adapter.disable() else adapter.enable()
            ControlResult.Done
        }
    }

    /** Đồng bộ tự động: quyền WRITE_SYNC_SETTINGS là quyền thường, nên bật/tắt trực tiếp được (app mẫu chỉ mở Cài đặt). */
    private fun toggleSync(): ControlResult = safe(ControlResult.OpenSystemPanel(SystemPanel.SETTINGS)) {
        ContentResolver.setMasterSyncAutomatically(!ContentResolver.getMasterSyncAutomatically())
        ControlResult.Done
    }

    private fun toggleRotationLock(): ControlResult = writeSettings {
        val autoRotate = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (autoRotate) 0 else 1)
    }

    /** RE mục 5.2: app mẫu đổi giữa ALL và ALARMS (chỉ báo thức). Ta dùng PRIORITY như Không làm phiền của Android. */
    private fun toggleDnd(): ControlResult {
        val nm = notifications ?: return ControlResult.Unavailable
        if (!permissions.isGranted(ControlPermission.DND_ACCESS)) {
            return ControlResult.NeedsPermission(ControlPermission.DND_ACCESS)
        }
        return safe(ControlResult.Unavailable) {
            val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
            // Android 15+: setInterruptionFilter chỉ đổi luật ngầm của chính app. Không làm phiền do người dùng hoặc app khác bật
            // thì không tắt được từ đây: mở màn Không làm phiền của hệ thống.
            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
                nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            ) ControlResult.OpenSystemPanel(SystemPanel.DND_SETTINGS) else ControlResult.Done
        }
    }

    private fun toggleTorch(): ControlResult {
        val cm = camera ?: return ControlResult.Unavailable
        val id = torchCameraId ?: return ControlResult.Unavailable
        return try {
            cm.setTorchMode(id, !torchOn)
            ControlResult.Done
        } catch (e: CameraAccessException) {
            ControlResult.Unavailable // camera đang bị app khác dùng
        } catch (e: RuntimeException) {
            ControlResult.Unavailable
        }
    }

    @SuppressLint("MissingPermission") // isEnabled: API ≤ 30 cần BLUETOOTH (đã khai), 31+ không cần quyền
    private fun bluetoothEnabled(): Boolean = bluetooth?.isEnabled == true

    private inline fun <T> safe(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: RuntimeException) {
        fallback
    }

    private companion object {
        /** AOSP: SCREEN_BRIGHTNESS là 0-255. Vài ROM dùng thang khác; chấp nhận lệch ở mức hiển thị. */
        const val BRIGHTNESS_MAX = 255f

        /** Không cho kéo về 0 hẳn: màn tối đen thì người dùng không thấy để kéo lại. */
        const val MIN_BRIGHTNESS_FRACTION = 0.02f

        /** Broadcast ẩn nhưng mọi ROM đều phát khi âm lượng đổi. Không có thì thanh chỉ cập nhật khi mở lại panel. */
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }
}
