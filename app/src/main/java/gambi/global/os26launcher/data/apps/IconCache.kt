package gambi.global.os26launcher.data.apps

import android.graphics.Bitmap
import android.util.LruCache
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.GlassLook
import gambi.global.os26launcher.domain.model.IconStyleKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache icon trong RAM, tính dung lượng theo byte (không theo số lượng). M2 chỉ có RAM:
 * cache đĩa hoãn tới M3, sau khi đo thời gian hiện lưới lần đầu trên Vsmart Live 4.
 */
@Singleton
class IconCache @Inject constructor() {

    private val maxSizeBytes = (Runtime.getRuntime().maxMemory() / 8).toInt()

    private val cache = object : LruCache<String, Bitmap>(maxSizeBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    // Có sizePx trong khóa: lưới và danh sách có thể hỏi hai kích thước khác nhau cho cùng một app.
    // G2b: kiểu icon cũng nằm trong khóa, nên một lần nạp đang bay lúc đổi kiểu không thể ghi đè icon kiểu mới.
    // A4/B7: [stamp] (dấu APK) nằm ngay sau `#user`, trước `@size`: APK mới là khóa mới, và `evict(packageName)` theo tiền tố "pkg/" không đổi.
    private fun cacheKey(key: ComponentKey, stamp: String, sizePx: Int, style: IconStyleKey) =
        "${key.packageName}/${key.className}#${key.userSerial}!$stamp@$sizePx~${style.style.name}.${style.tint.name}" +
                // IP: gói biểu tượng cũng nằm trong khóa; rỗng thì khóa giữ y hệt trước IP
                (if (style.pack.isEmpty()) "" else "^${style.pack}") +
                // CF Step 7: cờ nổi chỉ nối khi bật; tắt thì khóa y hệt trước
                (if (style.bevel) "+bevel" else "") +
                // 2026-11-23: bo góc icon chỉ nối khi khác mặc định; mặc định thì khóa y hệt trước
                (if (style.corner == GlassLook.DEFAULT.corner) "" else "%${style.corner}")

    fun get(key: ComponentKey, stamp: String, sizePx: Int, style: IconStyleKey): Bitmap? =
        cache.get(cacheKey(key, stamp, sizePx, style))

    fun put(key: ComponentKey, stamp: String, sizePx: Int, style: IconStyleKey, bitmap: Bitmap) {
        cache.put(cacheKey(key, stamp, sizePx, style), bitmap)
    }

    /**
     * Bỏ mọi icon của một package, mọi user profile và mọi kích thước. LruCache không có xóa theo tiền tố
     * nên quét snapshot khóa. Gọi khi app cập nhật hoặc bị gỡ, không thì icon cũ còn đến khi process chết.
     */
    fun evict(packageName: String) {
        val prefix = "$packageName/"
        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }

    /** G2b: [IconStyleSource] gọi khi đổi kiểu icon (TT#12). */
    fun clear() = cache.evictAll()
}
