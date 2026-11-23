package gambi.global.os26launcher.data.backdrop

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.core.locale.AppLocaleContext
import gambi.global.os26launcher.core.wallpaper.WallpaperPreset
import gambi.global.os26launcher.core.wallpaper.WallpaperPresets
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.domain.config.AppearanceRepository
import gambi.global.os26launcher.domain.config.WallpaperKind
import gambi.global.os26launcher.domain.model.WallpaperTarget
import gambi.global.os26launcher.domain.repository.BackdropRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bọc [PickedImageSource] và [WallpaperApplier] (code đã chạy được ở demo, chuyển nguyên vẹn) sau
 * [BackdropRepository]. [SystemWallpaperSource] cố ý KHÔNG được nối vào đây: đó là phương án B đang gác (B6).
 */
@Singleton
class BackdropRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val defaultSource: DefaultBackdropSource,
    private val appearance: AppearanceRepository, // CF: nguồn nền (ảnh/màu) và màu nền
    @ApplicationScope scope: CoroutineScope
) : BackdropRepository {

    private val source = PickedImageSource(context)

    /** CF: lựa chọn nguồn nền đọc từ `AppearanceConfig`; null tới lần đọc đầu (khi đó coi như AUTO, xem [awaitWallpaperConfig]). */
    private data class WallpaperChoice(val kind: WallpaperKind, val color: Int, val preset: String? = null)

    @Volatile
    private var choice: WallpaperChoice? = null

    /** CF: nền màu thay cho ảnh mặc định (đi đường "mặc định" nên Home không hỏi áp làm hình nền hệ thống). */
    private val colorChoice: WallpaperChoice? get() = choice?.takeIf { it.kind == WallpaperKind.COLOR }

    /** WI: ảnh cài sẵn, cùng đường "mặc định" như nền màu. Id lạ (ảnh đã bỏ) = null: rơi về ảnh tự chọn / ảnh mặc định như AUTO. */
    private val presetChoice: WallpaperPreset?
        get() = choice?.takeIf { it.kind == WallpaperKind.PRESET }?.let { WallpaperPresets.of(it.preset) }

    private val _versions = MutableStateFlow(backdropVersion())
    override val backdropVersions: StateFlow<Long> = _versions.asStateFlow()

    init {
        // CF: đổi nguồn nền hoặc màu (theme, Cài đặt) thì phát phiên bản mới để Home nạp lại tại chỗ. Lần đọc đầu chỉ điền bộ đệm.
        scope.launch {
            appearance.config.map { WallpaperChoice(it.wallpaperKind, it.wallpaperColor, it.wallpaperPreset) }.distinctUntilChanged().collect { next ->
                val first = choice == null
                choice = next
                if (!first) _versions.value = backdropVersion()
            }
        }
    }

    override suspend fun awaitWallpaperConfig() {
        if (choice != null) return
        val c = appearance.config.first()
        if (choice == null) choice = WallpaperChoice(c.wallpaperKind, c.wallpaperColor, c.wallpaperPreset)
    }

    override fun hasBackdrop(): Boolean = colorChoice == null && presetChoice == null && source.hasSaved()

    override fun backdropVersion(): Long = colorChoice?.let { ColorBackdropSource.versionOf(it.color) }
        ?: presetChoice?.let { presetVersion(it) }
        ?: when {
            source.hasSaved() -> source.version()
            // WP: không có ảnh chọn thì dấu phiên bản là khóa ảnh mặc định (-1 sáng, -2 tối) để Home nạp lại khi đổi sáng/tối
            defaultSource.isAvailable() -> if (defaultSource.isNight()) DEFAULT_NIGHT_VERSION else DEFAULT_LIGHT_VERSION
            else -> 0L
        }

    override fun hasDefaultBackdrop(): Boolean = colorChoice != null || presetChoice != null || defaultSource.isAvailable()

    /** WI: dấu phiên bản riêng mỗi ảnh cài sẵn, âm và nằm dưới khoảng của nền màu (`ColorBackdropSource.versionOf`). */
    private fun presetVersion(preset: WallpaperPreset): Long = -(PRESET_VERSION_BASE + WallpaperPresets.list.indexOf(preset) + 1)

    // FX1: OutOfMemoryError là Error nên không lọt vào `catch (Exception)`; thoát ra luồng IO là sập process, và với `load`
    // (chạy mỗi lần khởi động) là sập lặp lại. Bắt ở đây: Home vào trạng thái Error (nền đen) thay vì sập.
    override suspend fun loadDefaultBackdrop(reqW: Int, reqH: Int): Bitmap? = withContext(io) {
        try {
            colorChoice?.let { ColorBackdropSource.load(it.color, reqW, reqH) }
                ?: presetChoice?.let { ResourceBackdropLoader.load(context.resources, it.full, reqW, reqH) } // WI
                ?: defaultSource.load(reqW, reqH)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Hết bộ nhớ khi nạp ảnh nền mặc định", e)
            null
        }
    }

    override suspend fun loadBackdrop(reqW: Int, reqH: Int): Bitmap? = withContext(io) {
        try {
            source.load(reqW, reqH)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Hết bộ nhớ khi nạp ảnh nền", e)
            null
        }
    }

    // FX1 (C4): NonCancellable để thoát màn Cài đặt giữa chừng không bỏ dở việc; phát version NGAY TRONG khối vì kết quả withContext bị hủy
    // vẫn bị vứt ở người gọi, còn tác dụng phụ bên trong thì đã xảy ra.
    override suspend fun importBackdrop(uri: Uri, reqW: Int, reqH: Int): Bitmap? = withContext(io + NonCancellable) {
        importMutex.withLock {
            importLocked(uri, reqW, reqH)?.also {
                // CF: người dùng tự chọn ảnh thì nguồn nền là ảnh (thoát nền màu của theme). Đặt bộ đệm ngay rồi phát phiên bản, không chờ
                // collector, để Home nạp đúng ảnh vừa chọn.
                val name = displayNameOf(uri) // P3-2: tên ảnh cho hàng "Ảnh nền"
                try {
                    appearance.update { c -> c.copy(wallpaperKind = WallpaperKind.PHOTO, pickedImageName = name) }
                    choice = choice?.copy(kind = WallpaperKind.PHOTO) ?: WallpaperChoice(WallpaperKind.PHOTO, 0)
                } catch (e: IOException) {
                    Log.w(TAG, "Không ghi được nguồn nền PHOTO", e)
                }
                _versions.value = backdropVersion()
            }
        }
    }

    /** P3-2: tên hiển thị của [uri] (cột `DISPLAY_NAME`), bỏ phần đuôi; null nếu trình chọn ảnh không trả tên. */
    private fun displayNameOf(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: RuntimeException) { // SecurityException, IllegalArgumentException của provider lạ
        Log.w(TAG, "displayNameOf failed ${e.javaClass.simpleName}")
        null
    }

    // FX1: nhập tiếp tục sau khi màn Cài đặt đóng nên có thể có hai lần nhập chồng nhau (chọn, Back, mở lại, chọn tiếp). Cả hai dùng chung
    // `backdrop_image.tmp`; Mutex (công bằng, FIFO) tuần tự hóa và bảo đảm ảnh chọn SAU cùng thắng.
    private val importMutex = Mutex()

    private fun importLocked(uri: Uri, reqW: Int, reqH: Int): Bitmap? {
        return try {
            source.import(uri, reqW, reqH).also { bmp ->
                if (bmp != null) _versions.value = backdropVersion()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Không nhập được ảnh nền", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Không có quyền đọc ảnh nền", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Hết bộ nhớ khi nhập ảnh nền", e)
            null
        }
    }

    override fun wallpaperUnavailableReason(): String? = WallpaperApplier.unavailableReason(context)

    /** LG: thông điệp lỗi hiện thành Toast ở Home (`HomeEvent.WallpaperFailed`), nên theo ngôn ngữ app. */
    private fun localized(res: Int): String = AppLocaleContext.wrap(context).getString(res)

    override suspend fun applyAsWallpaper(
        target: WallpaperTarget,
        width: Int,
        height: Int
    ): Result<Unit> = withContext(io) {
        try {
            val src = source.load(width, height)
                ?: return@withContext Result.failure(IOException(localized(R.string.wallpaper_reason_read_failed)))
            WallpaperApplier.apply(context, src, width, height, target.toApplierTarget())
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Hết bộ nhớ khi áp wallpaper", e)
            Result.failure(IOException(localized(R.string.wallpaper_reason_out_of_memory)))
        }
    }

    override suspend fun applyPresetAsWallpaper(
        presetId: String,
        target: WallpaperTarget,
        width: Int,
        height: Int
    ): Result<Unit> = withContext(io) {
        val preset = WallpaperPresets.of(presetId)
            ?: return@withContext Result.failure(IOException(localized(R.string.wallpaper_reason_read_failed)))
        val m = context.resources.displayMetrics
        val w = if (width > 0) width else m.widthPixels
        val h = if (height > 0) height else m.heightPixels
        try {
            val src = ResourceBackdropLoader.load(context.resources, preset.full, w, h)
                ?: return@withContext Result.failure(IOException(localized(R.string.wallpaper_reason_read_failed)))
            WallpaperApplier.apply(context, src, w, h, target.toApplierTarget())
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Hết bộ nhớ khi áp wallpaper", e)
            Result.failure(IOException(localized(R.string.wallpaper_reason_out_of_memory)))
        }
    }

    private fun WallpaperTarget.toApplierTarget(): WallpaperApplier.Target = when (this) {
        WallpaperTarget.HOME -> WallpaperApplier.Target.HOME
        WallpaperTarget.LOCK -> WallpaperApplier.Target.LOCK
        WallpaperTarget.BOTH -> WallpaperApplier.Target.BOTH
    }

    private companion object {
        const val TAG = "BackdropRepo"
        const val DEFAULT_LIGHT_VERSION = -1L
        const val DEFAULT_NIGHT_VERSION = -2L
        const val PRESET_VERSION_BASE = 0x3_0000_0000L // WI: nền màu dùng -(2^32 .. 2^33)
    }
}
