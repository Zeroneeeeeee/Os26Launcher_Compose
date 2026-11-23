package gambi.global.os26launcher.data.controlcenter

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.domain.controlcenter.MediaControlsPort
import gambi.global.os26launcher.domain.controlcenter.MediaState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * G3d Task 7: đọc phiên media như app mẫu (RE mục 2: `sr2.d()` gọi `getActiveSessions(ComponentName của NotificationListener)` và
 * `addOnActiveSessionsChangedListener`). Chọn phiên đang phát; không có thì lấy phiên đầu.
 *
 * ComponentName ghi bằng tên lớp (chuỗi) vì data không được import ui; tên phải khớp service khai trong manifest.
 */
@Singleton
class MediaSessionSource @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaControlsPort {

    private val manager: MediaSessionManager? = context.getSystemService(MediaSessionManager::class.java)
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val listener = ComponentName(context.packageName, LISTENER_CLASS)

    /** Phiên đang chọn (chỉ dùng trên main thread). */
    private var current: MediaController? = null

    /** P3-7: gói bị bỏ qua (app đã ẩn). Main thread. */
    private var excluded: Set<String> = emptySet()

    /** P3-7: chọn lại phiên khi [excluded] đổi lúc đang theo dõi; null khi không có collector. Main thread. */
    private var reselect: (() -> Unit)? = null

    override fun setExcludedPackages(packages: Set<String>) {
        if (packages == excluded) return
        excluded = packages
        reselect?.invoke()
    }

    override fun observe(): Flow<MediaState?> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        var controller: MediaController? = null
        var onDestroyed: () -> Unit = {} // gán sau khi có select (hàm cục bộ không gọi trước khai báo được)
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                trySend(controller?.let(::toState))
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                trySend(controller?.let(::toState))
            }

            override fun onSessionDestroyed() {
                onDestroyed()
            }
        }

        fun select(next: MediaController?) {
            if (next?.sessionToken == controller?.sessionToken && next != null) {
                trySend(toState(next))
                return
            }
            controller?.unregisterCallback(callback)
            controller = next
            current = next
            next?.registerCallback(callback, handler)
            trySend(next?.let(::toState))
        }

        onDestroyed = {
            select(
                try {
                    pick(manager?.getActiveSessions(listener))
                } catch (e: SecurityException) {
                    null // vừa bị thu quyền
                }
            )
        }
        reselect = onDestroyed // cùng việc: đọc lại danh sách phiên và chọn (đã lọc gói bị bỏ qua)
        val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> select(pick(list)) }
        try {
            manager?.addOnActiveSessionsChangedListener(sessionsListener, listener, handler)
            select(pick(manager?.getActiveSessions(listener)))
        } catch (e: SecurityException) {
            trySend(null) // chưa cấp "Truy cập thông báo"
        }
        awaitClose {
            try {
                manager?.removeOnActiveSessionsChangedListener(sessionsListener)
            } catch (e: RuntimeException) {
                // đã gỡ
            }
            controller?.unregisterCallback(callback)
            current = null
            reselect = null
        }
    }.conflate()

    override fun playPause() {
        val c = current ?: return
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        safe { if (playing) c.transportControls.pause() else c.transportControls.play() }
    }

    override fun next() = safe { current?.transportControls?.skipToNext() }

    override fun previous() = safe { current?.transportControls?.skipToPrevious() }

    override fun seekTo(positionMs: Long) = safe { current?.transportControls?.seekTo(positionMs) }

    private fun pick(list: List<MediaController>?): MediaController? {
        // P3-7: phiên của app đã ẩn coi như không có.
        val usable = if (excluded.isEmpty()) list else list?.filter { it.packageName !in excluded }
        if (usable.isNullOrEmpty()) return null
        return usable.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: usable.first()
    }

    private fun toState(c: MediaController): MediaState {
        val md = c.metadata
        val pb = c.playbackState
        val art: Bitmap? = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        return MediaState(
            packageName = c.packageName,
            appLabel = appLabel(c.packageName),
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))
                .orEmpty(),
            art = art,
            isPlaying = pb?.state == PlaybackState.STATE_PLAYING,
            positionMs = pb?.position ?: 0L,
            positionUpdatedAtElapsed = pb?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = pb?.playbackSpeed ?: 1f,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0L,
            outputName = outputName()
        )
    }

    private fun appLabel(pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    /** Thiết bị Bluetooth đang nối để phát (A2DP/BLE), hoặc tai nghe có dây. */
    private fun outputName(): String? = try {
        audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }?.productName?.toString()?.takeIf { it.isNotBlank() }
    } catch (e: RuntimeException) {
        null
    }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            // phiên vừa bị huỷ
        }
    }

    private companion object {
        const val LISTENER_CLASS = "gambi.global.os26launcher.ui.controlcenter.ControlCenterService"
    }
}
