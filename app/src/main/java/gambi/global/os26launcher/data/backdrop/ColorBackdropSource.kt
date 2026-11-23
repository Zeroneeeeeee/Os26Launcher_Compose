package gambi.global.os26launcher.data.backdrop

import android.graphics.Bitmap
import kotlin.math.max

/**
 * CF — nền màu đặc (theme, onboarding 0.3) khi `appearance_wallpaper_kind = COLOR`. Bitmap cùng TỈ LỆ với vùng Home ([reqW] x [reqH]) để
 * `centerCrop` và ánh xạ kính tại chỗ (`imageMatrix`) giữ đúng, nhưng nhỏ hơn [DOWNSCALE] lần mỗi chiều: một màu phóng to vẫn y hệt, và
 * 1080x2400 chỉ tốn ~650 KB thay vì ~10 MB. ARGB_8888 vì bộ làm mờ của kính đọc bitmap nền.
 */
internal object ColorBackdropSource {

    private const val DOWNSCALE = 4

    fun load(color: Int, reqW: Int, reqH: Int): Bitmap? {
        if (reqW <= 0 || reqH <= 0) return null
        return Bitmap.createBitmap(max(1, reqW / DOWNSCALE), max(1, reqH / DOWNSCALE), Bitmap.Config.ARGB_8888)
            .apply { eraseColor(color) }
    }

    /**
     * Dấu phiên bản của nền màu: số âm riêng cho từng màu, không trùng `lastModified` của ảnh (dương) hay khóa ảnh mặc định (-1, -2), để
     * Home biết phải nạp lại khi đổi màu.
     */
    fun versionOf(color: Int): Long = -(COLOR_VERSION_BASE + (color.toLong() and 0xFFFFFFFFL))

    private const val COLOR_VERSION_BASE = 0x1_0000_0000L
}
