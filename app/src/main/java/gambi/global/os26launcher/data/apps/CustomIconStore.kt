package gambi.global.os26launcher.data.apps

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.data.layout.db.AppOverrideDao
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.repository.CustomIconRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P3-4 (CI) — icon riêng của từng app (màn "Thay đổi biểu tượng", 5.2).
 *
 * - File PNG vuông trong `files/custom_icons/`, tên `<package>__u<user>_<hash class>_<thời điểm>.png`: tiền tố package/user để dọn khi
 *   gỡ app, thời điểm để mỗi lần đổi là một tên mới (khóa cache icon đổi theo tên file, Home tự nạp lại).
 * - Đường dẫn (tên file) ở cột `app_overrides.customIconPath` (Room v2 đã có cột, không cần migration).
 *
 * [IconLoader] hỏi [fileFor] cho mỗi icon (chờ lần đọc CSDL đầu, không đoán "không có"). Lỗi đọc CSDL thành bản đồ rỗng: lỗi ở đây không
 * được làm danh sách app ngừng phát.
 */
@Singleton
class CustomIconStore @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: AppOverrideDao,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope scope: CoroutineScope
) : CustomIconRepository {

    private val dir = File(context.filesDir, DIR_NAME)

    /** Khóa -> tên file. null = chưa đọc CSDL lần nào. */
    private val names: StateFlow<Map<ComponentKey, String>?> = dao.observeIcons()
        .map { rows ->
            rows.filter { it.customIconPath.isNotBlank() }
                .associate { ComponentKey(it.packageName, it.className, it.userSerial) to it.customIconPath }
        }
        .catch { e ->
            Log.w(TAG, "custom icons unreadable, using none", e)
            emit(emptyMap())
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Khóa -> dấu phiên bản (tên file). `AppRepositoryImpl` nối vào `versionStamp` để mọi danh sách tự nạp lại icon khi đổi. */
    val stamps: Flow<Map<ComponentKey, String>> = names.filterNotNull()

    override val customized: Flow<Set<ComponentKey>> = stamps.map { it.keys }.distinctUntilChanged()

    /** File icon riêng của [key], null nếu không có (hoặc file đã mất). Chờ lần đọc CSDL đầu. */
    suspend fun fileFor(key: ComponentKey): File? {
        val name = names.filterNotNull().first()[key] ?: return null
        return File(dir, name).takeIf { it.isFile }
    }

    override suspend fun setIcon(key: ComponentKey, bitmap: Bitmap): Boolean = withContext(io + NonCancellable) {
        try {
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("mkdirs failed")
            val name = fileName(key)
            val tmp = File(dir, "$name.tmp")
            FileOutputStream(tmp).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("compress failed")
            }
            val target = File(dir, name)
            if (!tmp.renameTo(target)) throw IOException("rename failed")
            val old = names.value?.get(key)
            dao.setIconPath(key.packageName, key.className, key.userSerial, name)
            if (old != null && old != name) File(dir, old).delete()
            true
        } catch (e: IOException) {
            Log.w(TAG, "setIcon failed ${key.packageName}", e)
            false
        } catch (e: RuntimeException) { // SQLite lỗi đĩa đầy...
            Log.w(TAG, "setIcon failed ${key.packageName}", e)
            false
        }
    }

    override suspend fun clearIcon(key: ComponentKey): Unit = withContext(io + NonCancellable) {
        val old = names.value?.get(key)
        val ok = try {
            dao.setIconPath(key.packageName, key.className, key.userSerial, null)
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "clearIcon failed ${key.packageName}", e)
            false
        }
        if (ok && old != null) File(dir, old).delete()
    }

    /** Gỡ app thật (hàng CSDL đã do `LayoutReconciler` xóa): xóa mọi file icon riêng của gói đó trên user đó. */
    fun deletePackageFiles(packageName: String, userSerial: Long) {
        val prefix = "${packageName}__u${userSerial}_"
        dir.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    /** Hồ sơ công việc bị bỏ: xóa mọi file của user đó. */
    fun deleteUserFiles(userSerial: Long) {
        val marker = "__u${userSerial}_"
        dir.listFiles()?.filter { it.name.contains(marker) }?.forEach { it.delete() }
    }

    private fun fileName(key: ComponentKey): String =
        "${key.packageName}__u${key.userSerial}_${Integer.toHexString(key.className.hashCode())}_${System.currentTimeMillis()}.png"

    private companion object {
        const val TAG = "CustomIconStore"
        const val DIR_NAME = "custom_icons"
    }
}
