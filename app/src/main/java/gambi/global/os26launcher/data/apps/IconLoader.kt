package gambi.global.os26launcher.data.apps

import android.graphics.Bitmap
import android.util.Log
import gambi.global.os26launcher.core.bitmap.IconShape
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.GlassLook
import gambi.global.os26launcher.domain.model.IconStyleKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IconLoader @Inject constructor(
    private val dataSource: LauncherAppsDataSource,
    private val cache: IconCache,
    private val styleSource: IconStyleSource,
    private val renderer: IconRenderer,
    private val diskCache: IconDiskCache,
    private val iconPacks: IconPackManager, // IP
    private val customIcons: CustomIconStore // P3-4
) {

    /**
     * Icon vuông [sizePx], null nếu không lấy được. Chạy chậm, gọi từ luồng nền.
     * Một app có icon lỗi hoặc vừa bị gỡ không được làm sập màn hình chính → mọi lỗi trả null.
     */
    suspend fun load(key: ComponentKey, sizePx: Int): Bitmap? {
        // createBitmap ném IllegalArgumentException với kích thước <= 0
        if (sizePx <= 0) return null
        // G2b: chờ kiểu thật (không bao giờ mặc định giả) rồi tra cache theo kiểu đó
        val style = styleSource.current()
        // P3-4: icon riêng thắng mọi thứ (kiểu, gói); khóa cache = tên file (mỗi lần đổi là tên mới). Không vào cache đĩa (file đã ở đĩa).
        customIcons.fileFor(key)?.let { file ->
            val customStamp = CUSTOM_STAMP_PREFIX + file.name
            cache.get(key, customStamp, sizePx, style)?.let { return it }
            loadCustom(file, key, sizePx, style)?.let { bmp ->
                cache.put(key, customStamp, sizePx, style, bmp)
                return bmp
            }
        }
        return loadStyled(key, sizePx, style)
    }

    /** P3-4: icon hệ thống theo kiểu đang chọn, BỎ QUA icon riêng (ô "icon cũ" trong sheet đổi icon). */
    suspend fun loadSystem(key: ComponentKey, sizePx: Int): Bitmap? {
        if (sizePx <= 0) return null
        return loadStyled(key, sizePx, styleSource.current())
    }

    /** Đường cũ của [load] (trước P3-4): cache RAM -> đĩa -> gói -> vẽ. */
    private suspend fun loadStyled(key: ComponentKey, sizePx: Int, style: IconStyleKey): Bitmap? {
        // A4/B7: dấu APK đọc MỘT lần cho cả lượt nạp (registry được làm mới trước khi danh sách mới phát), dùng cho RAM và đĩa.
        val stamp = dataSource.sourceDirOf(key).orEmpty()
        cache.get(key, stamp, sizePx, style)?.let { return it }

        return try {
            // IC: RAM miss -> đĩa. Không có sourceDir (khóa chưa qua loadApps) thì bỏ qua đĩa, đi đường cũ.
            val fileName = stamp.takeIf { it.isNotEmpty() }?.let { diskName(key, sizePx, style, it) }
            if (fileName != null) {
                diskCache.get(fileName)?.let { hit ->
                    cache.put(key, stamp, sizePx, style, hit)
                    return hit
                }
            }
            // IP: có gói thì thử icon của gói trước (mục -> dự phòng); null = rơi về đường cũ
            val rendered = renderFromPack(key, sizePx, style) ?: run {
                // null: app đã bị gỡ ngay trước lời gọi này
                val drawable = dataSource.loadIconDrawable(key) ?: return null
                // Vẽ + cắt hình bo góc; bitmap trả về có góc trong suốt, view chỉ đọc
                renderer.render(drawable, sizePx, style)
            }
            // C10: huy hiệu hồ sơ công việc vẽ cuối cùng, trước khi vào RAM và đĩa (tên file đĩa của hồ sơ có hậu tố riêng)
            val bitmap = dataSource.badgeForUser(rendered, key.userSerial)
            cache.put(key, stamp, sizePx, style, bitmap)
            if (fileName != null) diskCache.put(fileName, bitmap)
            bitmap
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "icon OOM ${key.packageName}", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "icon failed ${key.packageName}", e)
            null
        }
    }

    /** P3-4: giải mã file icon riêng (thu nhỏ gần [sizePx]), cắt hình, huy hiệu hồ sơ. null nếu file hỏng (rơi về icon hệ thống). */
    private fun loadCustom(file: java.io.File, key: ComponentKey, sizePx: Int, style: IconStyleKey): Bitmap? = try {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
        val src = android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        src?.let {
            try {
                dataSource.badgeForUser(renderer.renderCustom(it, sizePx, IconShape.forCorner(style.corner)), key.userSerial)
            } finally {
                it.recycle()
            }
        }
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "custom icon OOM ${key.packageName}", e)
        null
    } catch (e: Exception) {
        Log.w(TAG, "custom icon failed ${key.packageName}", e)
        null
    }

    /** P3-4: icon mẫu "hình của app trên nền [color]" (ô vuông chưa cắt, xem [IconRenderer.renderOnColor]). null nếu app không còn. */
    fun renderOnColor(key: ComponentKey, sizePx: Int, color: Int): Bitmap? {
        if (sizePx <= 0) return null
        return try {
            val drawable = dataSource.loadIconDrawable(key) ?: return null
            renderer.renderOnColor(drawable, sizePx, color)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "on-color icon OOM ${key.packageName}", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "on-color icon failed ${key.packageName}", e)
            null
        }
    }

    /** P3-4: cắt hình TL2 cho một ô vuông (icon mẫu hiển thị trong sheet). */
    fun shape(square: Bitmap, sizePx: Int): Bitmap = renderer.renderCustom(square, sizePx)

    /**
     * P3-3: vẽ icon theo kiểu [style] CHỈ ĐỊNH (không theo kiểu đang lưu), không đọc/ghi cache. Dùng cho khung xem trước Bộ biểu tượng:
     * kiểu vừa đổi trong Cài đặt có thể chưa tới [IconStyleSource] (hai flow đọc DataStore độc lập), nên không dùng [load].
     */
    fun render(key: ComponentKey, sizePx: Int, style: IconStyleKey): Bitmap? {
        if (sizePx <= 0) return null
        return try {
            val rendered = renderFromPack(key, sizePx, style) ?: run {
                val drawable = dataSource.loadIconDrawable(key) ?: return null
                renderer.render(drawable, sizePx, style)
            }
            dataSource.badgeForUser(rendered, key.userSerial)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "preview icon OOM ${key.packageName}", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "preview icon failed ${key.packageName}", e)
            null
        }
    }

    /**
     * IP: icon từ gói đang chọn. Thứ tự: mục `<item>` khớp component -> icon dự phòng ghép từ iconback/iconmask/iconupon
     * (chỉ khi gói khai) -> null (caller dùng icon gốc + TL2 + G2b). Mọi lỗi của gói trả null.
     */
    private fun renderFromPack(key: ComponentKey, sizePx: Int, style: IconStyleKey): Bitmap? {
        if (style.pack.isEmpty()) return null
        return try {
            val pack = iconPacks.packFor(style.pack) ?: return null
            pack.mapped(key.packageName, key.className)
                ?.let { return renderer.renderPackIcon(it, sizePx, IconShape.forCorner(style.corner)) }
            if (!pack.hasFallback) return null
            val original = dataSource.loadIconDrawable(key) ?: return null
            renderer.renderPackFallback(original, sizePx, pack, key.packageName.hashCode(), IconShape.forCorner(style.corner))
        } catch (e: OutOfMemoryError) {
            Log.w(PACK_TAG, "pack icon OOM", e)
            null
        } catch (e: Exception) {
            Log.w(PACK_TAG, "pack icon failed", e)
            null
        }
    }

    /** `<pkg>__<hash>.png`; hash gồm class, user, kích thước, kiểu, màu tô và sourceDir. Tiền tố pkg để evict. */
    private fun diskName(
        key: ComponentKey,
        sizePx: Int,
        style: IconStyleKey,
        sourceDir: String
    ): String {
        // IP: gói nối vào cuối CHỈ khi có; không gói thì chuỗi thô y hệt trước IP nên cache đĩa cũ còn dùng được
        val packPart = if (style.pack.isEmpty()) "" else "|pack=${style.pack}"
        // C10: chỉ user khác user chính có hậu tố, nên file cũ không huy hiệu của hồ sơ không bị dùng lại mà cache user chính không mất
        val badgePart = if (dataSource.isWorkUser(key.userSerial)) "|badge1" else ""
        // 2026-11-23: bo góc icon chỉ nối khi khác hình gốc (NEUTRAL_CORNER, không phải DEFAULT: mặc định đổi thì file cũ phải tách), nên file
        // cache đĩa cũ vẫn dùng được
        val cornerPart = if (style.corner == GlassLook.NEUTRAL_CORNER) "" else "|corner=${style.corner}"
        val raw =
            "${key.className}|${key.userSerial}|$sizePx|${style.style.name}|${style.tint.name}|$sourceDir$packPart$badgePart$cornerPart"
        return "${key.packageName}__${IconDiskCache.shortHash(raw)}.png"
    }

    private companion object {
        const val TAG = "IconLoader"
        const val PACK_TAG = "IP"

        /** P3-4: tiền tố khóa cache của icon riêng (không trùng sourceDir thật). */
        const val CUSTOM_STAMP_PREFIX = "custom:"
    }
}
