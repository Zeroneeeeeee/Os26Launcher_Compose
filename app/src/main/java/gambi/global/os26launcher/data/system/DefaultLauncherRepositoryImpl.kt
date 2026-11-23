package gambi.global.os26launcher.data.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.domain.repository.DefaultLauncherRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultLauncherRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DefaultLauncherRepository {

    private val packageManager: PackageManager get() = context.packageManager

    override fun isDefaultLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val held = try {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    roleManager.isRoleHeld(RoleManager.ROLE_HOME)
                } else {
                    null
                }
            } catch (e: SecurityException) {
                null
            }
            if (held != null) return held
        }
        // API 26–28, hoặc máy không cho hỏi role: xem hệ thống đang chọn ai cho HOME intent.
        // Khi chưa có launcher mặc định, kết quả là ResolverActivity (package "android") nên so sánh ra false.
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    override fun createSetDefaultIntent(): Intent? =
        createRoleRequestIntent() ?: createFallbackIntents().firstOrNull()

    override fun createRoleRequestIntent(): Intent? {
        // Đường 1: RoleManager (API 29+). Một số ROM khai báo role nhưng từ chối tạo intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    if (isResolvable(intent)) return intent
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "RoleManager từ chối tạo intent", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "RoleManager từ chối tạo intent", e)
            }
        }
        return null
    }

    // Đường 2 và 3: màn cài đặt launcher mặc định, rồi màn Cài đặt chung
    override fun createFallbackIntents(): List<Intent> =
        listOf(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            .filter(::isResolvable)

    private fun isResolvable(intent: Intent): Boolean =
        packageManager.resolveActivity(intent, 0) != null

    private companion object {
        const val TAG = "M1"
    }
}
