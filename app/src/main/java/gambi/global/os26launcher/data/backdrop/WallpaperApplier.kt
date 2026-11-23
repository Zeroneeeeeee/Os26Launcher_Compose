package gambi.global.os26launcher.data.backdrop

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.locale.AppLocaleContext
import kotlin.math.max

/**
 * Đặt ảnh nền của app làm wallpaper hệ thống (quyết định 2 trong docs/LiquidGlass-Demo.md).
 *
 * Cần quyền `SET_WALLPAPER` (quyền thường, chỉ khai báo trong manifest, không xin lúc chạy).
 */
object WallpaperApplier {

    /** Nơi áp dụng. [title] là chữ hiển thị trên hộp thoại. */
    enum class Target(val title: String, val flags: Int) {
        HOME("Màn hình chính", WallpaperManager.FLAG_SYSTEM),
        LOCK("Màn khóa", WallpaperManager.FLAG_LOCK),
        BOTH("Cả hai", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
    }

    /** Lý do không đặt được wallpaper trên máy này (chữ hiện cho người dùng, theo ngôn ngữ app: LG), hoặc null nếu đặt được. */
    fun unavailableReason(context: Context): String? {
        val wm = WallpaperManager.getInstance(context)
        val res = when {
            !wm.isWallpaperSupported() -> R.string.wallpaper_reason_unsupported
            !wm.isSetWallpaperAllowed() -> R.string.wallpaper_reason_not_allowed
            else -> return null
        }
        return AppLocaleContext.wrap(context).getString(res)
    }

    /**
     * Đặt [src] làm wallpaper cho [target]. Chạy ở luồng nền (ghi xuống bộ nhớ nên có thể chậm).
     *
     * Ảnh được cắt kiểu centerCrop về đúng [width] x [height] (kích thước màn hình app đang vẽ), để wallpaper hệ
     * thống khớp với bản launcher vẽ bằng `centerCrop`. Vùng trong suốt của ảnh được tô đen, vì wallpaper hệ thống
     * không có "nền demo" phía sau để lộ ra như trong launcher.
     *
     * Có thể ném `IOException` hoặc `SecurityException`, người gọi cần bắt.
     */
    fun apply(context: Context, src: Bitmap, width: Int, height: Int, target: Target) {
        val flat = flattenCenterCrop(src, width, height)
        try {
            WallpaperManager.getInstance(context.applicationContext)
                .setBitmap(flat, null, true, target.flags)
        } finally {
            flat.recycle()
        }
    }

    private fun flattenCenterCrop(src: Bitmap, width: Int, height: Int): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val scale = max(width.toFloat() / src.width, height.toFloat() / src.height)
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate((width - src.width * scale) / 2f, (height - src.height * scale) / 2f)
        }
        canvas.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
