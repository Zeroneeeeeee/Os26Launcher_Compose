package gambi.global.os26launcher.data.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IC: cache bitmap icon ĐÃ RENDER xuống đĩa, mỗi icon một file PNG trong `cacheDir/icons-v1-<hash fingerprint>/`.
 *
 * Tên file do [IconLoader] đặt (`<pkg>__<hash>.png`, hash gồm cả `sourceDir`), nên cache này không tự quyết định
 * hợp lệ: cùng tên = cùng nội dung. Đọc chạy trên luồng gọi (IconLoader đã ở `@IoDispatcher`); ghi và dọn chạy trên
 * một luồng đơn riêng để không tranh CPU với việc nạp icon. Mọi lỗi chỉ ghi log và rơi về đường render, không ném ra.
 */
@Singleton
class IconDiskCache @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher io: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val writer = io.limitedParallelism(1)

    private val dirName = DIR_PREFIX + shortHash(Build.FINGERPRINT)
    private val dir = File(context.cacheDir, dirName)

    // C2: tên file đã được chạm `lastModified` (hoặc vừa ghi) trong process này; mỗi file tối đa một lần mỗi process.
    private val touched = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Tổng byte ước lượng trong dir; chỉ truy cập trên [writer] (đơn luồng). -1 = chưa biết, quét lại khi cần.
    private var totalBytes = -1L

    init {
        try {
            dir.mkdirs()
        } catch (e: Exception) {
            Log.w(TAG, "icon cache dir create failed", e)
        }
        // Thư mục của fingerprint cũ (đã cập nhật hệ điều hành) bỏ đi; làm trên luồng ghi để không chặn luồng khởi tạo.
        scope.launch(writer) {
            try {
                context.cacheDir.listFiles()?.forEach { f ->
                    if (f.name.startsWith(DIR_PREFIX_ANY) && f.name != dirName) f.deleteRecursively()
                }
            } catch (e: Exception) {
                Log.w(TAG, "stale icon dirs cleanup failed", e)
            }
        }
    }

    /** Chặn, gọi từ luồng nền. null = thiếu hoặc hỏng (file hỏng bị xóa). */
    fun get(name: String): Bitmap? {
        val file = File(dir, name)
        return try {
            if (!file.isFile) return null
            // decodeFile chấp nhận PNG bị cắt cụt (trả bitmap trống), nên kiểm chữ ký đầu và chunk IEND cuối trước.
            if (!isCompletePng(file)) {
                Log.w(TAG, "icon cache file incomplete, deleting $name")
                file.delete()
                return null
            }
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bmp = BitmapFactory.decodeFile(file.path, opts)
            if (bmp == null) {
                file.delete()
            } else if (touched.add(name)) {
                // C2: xóa theo LRU thật: đọc trúng thì đẩy mtime lên bây giờ. Làm trên luồng ghi, không nằm trên đường trả bitmap.
                scope.launch(writer) {
                    try {
                        file.setLastModified(System.currentTimeMillis())
                    } catch (e: Exception) {
                        Log.w(TAG, "icon cache touch failed $name", e)
                    }
                }
            }
            bmp
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "icon cache read OOM $name", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "icon cache read failed $name, deleting", e)
            safeDelete(file)
            null
        }
    }

    /** Xếp hàng ghi nền, trả ngay. Bitmap không được sửa sau khi gọi (icon trong RAM cache chỉ đọc). */
    fun put(name: String, bitmap: Bitmap) {
        scope.launch(writer) { write(name, bitmap) }
    }

    /** Xóa mọi file của một package (mọi class, user, kích thước, kiểu). Chạy trên luồng ghi. */
    fun evict(packageName: String) {
        scope.launch(writer) {
            try {
                val prefix = "${packageName}__"
                dir.listFiles()?.forEach { if (it.name.startsWith(prefix)) it.delete() }
                totalBytes = -1L
            } catch (e: Exception) {
                Log.w(TAG, "icon cache evict failed $packageName", e)
            }
        }
    }

    /** Xóa file cũ nhất (theo lastModified) cho tới khi tổng <= trần. Chạy trên luồng ghi. */
    fun trim() {
        scope.launch(writer) { trimNow() }
    }

    private fun write(name: String, bitmap: Bitmap) {
        val target = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        try {
            if (target.exists()) return
            if (!dir.isDirectory) dir.mkdirs()
            FileOutputStream(tmp).use { out ->
                if (!bitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        out
                    )
                ) error("compress returned false")
            }
            if (!tmp.renameTo(target)) error("rename failed")
            touched.add(name) // C2: file vừa ghi đã mới
            if (totalBytes < 0) totalBytes = scanTotal() else totalBytes += target.length()
            if (totalBytes > MAX_BYTES) trimNow()
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "icon cache write OOM $name", e)
            safeDelete(tmp)
        } catch (e: Exception) {
            Log.w(TAG, "icon cache write failed $name", e)
            safeDelete(tmp)
        }
    }

    private fun trimNow() {
        try {
            val files = dir.listFiles()?.filter { it.isFile } ?: return
            var total = files.sumOf { it.length() }
            if (total > MAX_BYTES) {
                for (f in files.sortedBy { it.lastModified() }) {
                    if (total <= MAX_BYTES) break
                    val len = f.length()
                    if (f.delete()) total -= len
                }
            }
            totalBytes = total
        } catch (e: Exception) {
            Log.w(TAG, "icon cache trim failed", e)
            totalBytes = -1L
        }
    }

    /** Đọc 8 byte đầu (chữ ký PNG) và 12 byte cuối (chunk IEND); chỉ 2 lần đọc nhỏ. */
    private fun isCompletePng(file: File): Boolean {
        if (file.length() < PNG_SIGNATURE.size + IEND_CHUNK.size) return false
        RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(PNG_SIGNATURE.size)
            raf.readFully(head)
            if (!head.contentEquals(PNG_SIGNATURE)) return false
            val tail = ByteArray(IEND_CHUNK.size)
            raf.seek(raf.length() - IEND_CHUNK.size)
            raf.readFully(tail)
            return tail.contentEquals(IEND_CHUNK)
        }
    }

    private fun scanTotal(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun safeDelete(file: File) {
        try {
            file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "icon cache delete failed ${file.name}", e)
        }
    }

    companion object {
        private const val TAG = "IconDiskCache"
        private val PNG_SIGNATURE =
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val IEND_CHUNK = byteArrayOf(
            0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
        )
        private const val DIR_PREFIX_ANY = "icons-"
        private const val DIR_PREFIX = "icons-v1-"

        /** Trần tổng dung lượng thư mục icon (chủ dự án duyệt 2026-11-06). */
        private const val MAX_BYTES = 8L * 1024 * 1024

        /** 16 ký tự hex đầu của SHA-1; đủ để va chạm không đáng kể ở quy mô vài trăm icon. */
        fun shortHash(input: String): String {
            val digest =
                MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
