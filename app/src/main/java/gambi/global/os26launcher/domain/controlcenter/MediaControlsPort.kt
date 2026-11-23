package gambi.global.os26launcher.domain.controlcenter

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

/**
 * G3d Task 7: phiên phát nhạc đang hoạt động (RE mục 5.1, khối nhạc nhỏ và trình phát lớn). [art] là Bitmap: ngoại lệ có chủ ý
 * của luật domain như `BackdropRepository`. [positionMs] đúng tại [positionUpdatedAtElapsed] (`SystemClock.elapsedRealtime`);
 * khi đang phát, vị trí hiện tại = positionMs + (now − positionUpdatedAtElapsed)·[speed].
 */
data class MediaState(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val positionUpdatedAtElapsed: Long,
    val speed: Float,
    val durationMs: Long,
    /** Tên thiết bị phát (tai nghe Bluetooth...), null = loa của máy. */
    val outputName: String?
)

/** G3d Task 7: đọc và điều khiển phiên media qua `MediaSessionManager` (cần quyền đọc thông báo). */
interface MediaControlsPort {
    /** null = không có phiên nào hoặc thiếu quyền. Chỉ đăng ký listener khi có collector (panel đang mở). */
    fun observe(): Flow<MediaState?>

    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)

    /**
     * P3-7: bỏ qua phiên phát nhạc của các gói này (gói chỉ còn app đã ẩn): có phiên khác thì hiện phiên đó, không thì như không phát.
     * Gọi trên main thread; đổi khi đang theo dõi thì chọn lại phiên ngay.
     */
    fun setExcludedPackages(packages: Set<String>)
}
