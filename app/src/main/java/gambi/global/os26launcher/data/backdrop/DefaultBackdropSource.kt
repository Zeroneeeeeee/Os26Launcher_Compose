package gambi.global.os26launcher.data.backdrop

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.core.wallpaper.WallpaperPresets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WP: ảnh nền MẶC ĐỊNH khi người dùng chưa chọn ảnh (không có `files/backdrop_image`).
 *
 * WI (2026-11-25): ảnh mặc định là ảnh cài sẵn [WallpaperPresets.DEFAULT_ID] (`wp_ios27_1`), dùng chung cho chế độ sáng lẫn tối.
 * Hai file thử `default_wallpaper_ios27.jpg` (ảnh iOS 27 của Apple, không giấy phép, bản sáng/tối) đã xóa.
 */
@Singleton
class DefaultBackdropSource @Inject constructor(
    @ApplicationContext private val context: Context
) : BackdropSource {

    override val name = "ảnh mặc định"

    /** Có ảnh mặc định để dùng không. */
    fun isAvailable(): Boolean = true

    /** Đang ở chế độ tối của hệ thống (quyết định ảnh nào được chọn). */
    fun isNight(): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /**
     * FX1: vùng giữa có tỉ lệ bằng màn ([BitmapScaler.cropRegion], đúng phần `centerCrop` hiển thị) rồi thu về không quá [reqW] x [reqH]
     * (ví dụ 1320x2868 thành 1080x2100 cho vùng Home 1080x2100, ≈ 9 MB thay vì ≈ 15 MB). Bitmap cùng tỉ lệ với `ImageView` nên kính tại chỗ
     * khớp. Cắt trong bộ nhớ vì ảnh chỉ ~1,3 MP (không cần `BitmapRegionDecoder`).
     */
    override fun load(reqW: Int, reqH: Int): Bitmap? {
        if (!isAvailable() || reqW <= 0 || reqH <= 0) return null
        // WI: phần giải mã chuyển sang [ResourceBackdropLoader] để ảnh cài sẵn dùng chung.
        val res = WallpaperPresets.of(WallpaperPresets.DEFAULT_ID)?.full ?: return null // WI
        return ResourceBackdropLoader.load(context.resources, res, reqW, reqH)
    }
}
