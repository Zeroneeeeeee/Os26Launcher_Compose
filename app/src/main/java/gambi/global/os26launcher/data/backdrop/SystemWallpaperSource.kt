package gambi.global.os26launcher.data.backdrop

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap

/**
 * Phương án B (TẠM GÁC, chưa nối vào giao diện): đọc wallpaper thật của hệ thống.
 *
 * Trên Android 14 chỉ đọc được khi app giữ quyền `MANAGE_EXTERNAL_STORAGE` ("Tất cả tệp"), nếu không sẽ ném
 * `SecurityException` (hàm này bắt lại và trả null). Quyền đó đã được gỡ khỏi manifest chính; muốn thử lại B thì
 * phải khai báo lại quyền và mở màn hình cấp quyền qua `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`.
 *
 * Nhược điểm của B (Google Play hạn chế quyền này, chưa kiểm chứng với live wallpaper) chưa được giải quyết.
 * Xem mục "Quyết định đã chốt" trong docs/LiquidGlass-Demo.md.
 */
class SystemWallpaperSource(context: Context) : BackdropSource {

    private val appContext = context.applicationContext

    override val name = "wallpaper hệ thống"

    /** Trả ảnh gốc của wallpaper, không thu nhỏ: [reqW] và [reqH] không được dùng. */
    // Thiếu quyền đọc wallpaper thì ném SecurityException, đã bắt bên dưới và trả null (không đổi hành vi).
    @SuppressLint("MissingPermission")
    override fun load(reqW: Int, reqH: Int): Bitmap? {
        return try {
            val d = WallpaperManager.getInstance(appContext).drawable ?: return null
            (d as? BitmapDrawable)?.bitmap ?: d.toBitmap()
        } catch (e: SecurityException) {
            null
        }
    }
}
