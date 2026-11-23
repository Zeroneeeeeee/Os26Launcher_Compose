package gambi.global.os26launcher.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.os.UserManager
import android.util.Log
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.domain.model.AppShortcutItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.repository.ShortcutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M7 Task 10 Step 1. Chỉ launcher MẶC ĐỊNH mới có quyền đọc shortcut của app khác
 * ([LauncherApps.hasShortcutHostPermission]); khi chưa là mặc định mọi lời gọi ném SecurityException -> trả rỗng.
 *
 * Cờ truy vấn: động + manifest (shortcut tĩnh như "Chụp ảnh tự sướng" của Camera nằm ở manifest, plan gốc quên).
 * KHÔNG lấy shortcut chỉ-ghim: đó là lối tắt người dùng ghim ra màn hình, không phải menu của app.
 */
@Singleton
class ShortcutRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : ShortcutRepository {

    private val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager = context.getSystemService(UserManager::class.java)

    override suspend fun getShortcuts(key: ComponentKey, iconSizePx: Int): List<AppShortcutItem> =
        withContext(io) {
            try {
                if (!launcherApps.hasShortcutHostPermission()) return@withContext emptyList()
                val user = userManager.getUserForSerialNumber(key.userSerial)
                    ?: return@withContext emptyList()
                val query = LauncherApps.ShortcutQuery()
                    .setPackage(key.packageName)
                    .setActivity(ComponentName(key.packageName, key.className))
                    .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
                val infos = launcherApps.getShortcuts(query, user).orEmpty()
                infos
                    .filter { it.isEnabled && !it.shortLabel.isNullOrBlank() }
                    .sortedBy { it.rank }
                    .take(ShortcutRepository.MAX_SHORTCUTS)
                    .map { info ->
                        AppShortcutItem(
                            info.id,
                            info.shortLabel.toString(),
                            iconOf(info, iconSizePx)
                        )
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "getShortcuts failed for ${key.packageName}", e)
                emptyList()
            }
        }

    override suspend fun shortcutIcon(key: ComponentKey, shortcutId: String, iconSizePx: Int): Bitmap? =
        withContext(io) {
            val cacheKey = "${key.packageName}/$shortcutId/${key.userSerial}/$iconSizePx"
            val now = SystemClock.elapsedRealtime()
            iconCache.get(cacheKey)?.let { if (now - it.atMs < ICON_CACHE_TTL_MS) return@withContext it.bitmap }
            try {
                if (!launcherApps.hasShortcutHostPermission()) return@withContext null
                val user = userManager.getUserForSerialNumber(key.userSerial) ?: return@withContext null
                // setShortcutIds đi cùng setPackage; id duy nhất theo package nên không cần setActivity.
                val query = LauncherApps.ShortcutQuery()
                    .setPackage(key.packageName)
                    .setShortcutIds(listOf(shortcutId))
                    .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
                val info = launcherApps.getShortcuts(query, user).orEmpty().firstOrNull { it.isEnabled && it.id == shortcutId }
                    ?: return@withContext null
                val bitmap = iconOf(info, iconSizePx) ?: return@withContext null
                iconCache.put(cacheKey, CachedIcon(bitmap, now))
                bitmap
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "shortcutIcon failed for ${key.packageName}", e)
                null
            }
        }

    // C8: icon shortcut của tìm kiếm, 32 mục, hết hạn sau 60 giây (cùng nhịp cache chỉ mục shortcut): icon động có thể đổi.
    private class CachedIcon(val bitmap: Bitmap, val atMs: Long)

    private val iconCache = LruCache<String, CachedIcon>(32)

    override fun canReadShortcuts(): Boolean = try {
        launcherApps.hasShortcutHostPermission()
    } catch (e: Exception) {
        false
    }

    override fun startShortcut(key: ComponentKey, shortcutId: String): Boolean = try {
        val user = userManager.getUserForSerialNumber(key.userSerial)
        if (user == null) {
            false
        } else {
            launcherApps.startShortcut(key.packageName, shortcutId, null, null, user)
            true
        }
    } catch (e: Exception) {
        Log.w(TAG, "startShortcut failed ${key.packageName}/$shortcutId", e)
        false
    }

    private fun iconOf(info: ShortcutInfo, sizePx: Int): Bitmap? {
        if (sizePx <= 0) return null
        return try {
            val drawable = launcherApps.getShortcutIconDrawable(info, 0) ?: return null
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(Canvas(bitmap))
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "shortcut icon failed ${info.id}", e)
            null
        }
    }

    private companion object {
        const val TAG = "ShortcutRepo"
        const val ICON_CACHE_TTL_MS = 60_000L
    }
}
