package gambi.global.os26launcher.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.domain.model.DockRole
import gambi.global.os26launcher.domain.repository.DefaultAppResolverPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Danh sách package ứng viên (có thứ tự) cho mỗi vai trò dock, theo chuỗi dự phòng của DD (D2):
 * (1) API công khai cho mặc định thật, (2) `resolveActivity` khi KHÔNG phải hộp chọn,
 * (3) trình xử lý hệ thống, (4) package đã biết. Bậc cuối (bảng chữ cái) nằm ở use case.
 * Mỗi bậc bọc try/catch riêng: một bậc lỗi không làm mất các bậc sau.
 */
@Singleton
class DefaultAppResolver @Inject constructor(
    @ApplicationContext private val context: Context
) : DefaultAppResolverPort {

    private val packageManager: PackageManager = context.packageManager

    override fun candidates(role: DockRole): List<String> {
        val intent = intentFor(role)
        val result = LinkedHashMap<String, String>() // package -> bậc đầu tiên đưa nó vào
        fun add(tier: String, pkg: String?) {
            if (!pkg.isNullOrEmpty()) result.putIfAbsent(pkg, tier)
        }

        // (1) API công khai
        guarded("api", role) {
            when (role) {
                DockRole.PHONE -> {
                    val telecom = context.getSystemService(TelecomManager::class.java)
                    add(TIER_API, telecom?.defaultDialerPackage)
                    if (Build.VERSION.SDK_INT >= 29) add(TIER_API, telecom?.systemDialerPackage)
                }

                DockRole.MESSAGES -> add(TIER_API, Telephony.Sms.getDefaultSmsPackage(context))
                else -> Unit
            }
        }

        // Danh sách trình xử lý lấy một lần, dùng cho bậc (2) và (3).
        val handlers: List<ResolveInfo> = try {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (e: RuntimeException) {
            Log.w(TAG, "queryIntentActivities failed for $role", e)
            emptyList()
        }

        // (2) mặc định thật từ resolveActivity
        guarded("resolve", role) {
            @Suppress("DEPRECATION")
            val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            add(TIER_RESOLVE, realDefault(resolved, handlers))
        }

        // (3) trình xử lý hệ thống, loại hộp chọn; package đã biết xếp trước
        guarded("system", role) {
            val known = KNOWN[role].orEmpty()
            val systemPkgs = handlers
                .mapNotNull { it.activityInfo }
                .filter { !isChooser(it.packageName, it.name) && isSystem(it.applicationInfo) }
                .map { it.packageName }
                .distinct()
            systemPkgs.filter { it in known }.forEach { add(TIER_SYSTEM, it) }
            systemPkgs.filter { it !in known }.forEach { add(TIER_SYSTEM, it) }
        }

        // (4) package đã biết
        KNOWN[role].orEmpty().forEach { add(TIER_KNOWN, it) }

        val first = result.entries.firstOrNull()
        Log.i(TAG, "role=$role src=${first?.value ?: "NONE"} cands=${result.keys.toList()}")
        return result.keys.toList()
    }

    /** "Mặc định thật" = kết quả resolveActivity không phải hộp chọn (D1: A1 + A2). */
    private fun realDefault(info: ResolveInfo?, handlers: List<ResolveInfo>): String? {
        val ai = info?.activityInfo ?: return null
        if (isChooser(ai.packageName, ai.name)) return null
        val inHandlers = handlers.any {
            it.activityInfo?.packageName == ai.packageName && it.activityInfo?.name == ai.name
        }
        return if (inHandlers) ai.packageName else null
    }

    /** A1: tên hộp chọn đã biết. */
    private fun isChooser(pkg: String, cls: String): Boolean =
        pkg == "android" || pkg == "com.android.intentresolver" ||
                pkg.startsWith(PERMISSION_RESOLVER_PREFIX) ||
                cls.endsWith("ResolverActivity") || cls.endsWith("ChooserActivity")

    private fun isSystem(info: ApplicationInfo?): Boolean =
        info != null &&
                (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0

    private inline fun guarded(step: String, role: DockRole, block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            Log.w(TAG, "step=$step failed for $role", e)
        }
    }

    private fun intentFor(role: DockRole): Intent = when (role) {
        DockRole.PHONE -> Intent(Intent.ACTION_DIAL)
        DockRole.MESSAGES -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
        DockRole.BROWSER -> Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)

        DockRole.CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    }

    private companion object {
        const val TAG = "DefaultAppResolver"
        const val PERMISSION_RESOLVER_PREFIX = "android.permission"
        const val TIER_API = "API"
        const val TIER_RESOLVE = "RESOLVE"
        const val TIER_SYSTEM = "SYSTEM"
        const val TIER_KNOWN = "KNOWN"

        /** Package đã biết theo vai trò. Nguồn: "đo" = đã đo, "suy luận" = chưa đo. */
        val KNOWN: Map<DockRole, List<String>> = mapOf(
            DockRole.PHONE to listOf(
                "com.google.android.dialer", // đo Oppo shell 2026-11-09
                "com.vinsmart.android.dialer", // đo Live 4 M3
                "com.android.dialer", // suy luận
                "com.samsung.android.dialer" // suy luận
            ),
            DockRole.MESSAGES to listOf(
                "com.google.android.apps.messaging", // đo Oppo shell 2026-11-09
                "com.android.mms", // đo Oppo shell 2026-11-09
                "com.vinsmart.messaging", // đo Live 4 M3
                "com.samsung.android.messaging", // suy luận
                "com.android.messaging" // suy luận
            ),
            DockRole.BROWSER to listOf(
                "com.android.chrome", // đo Oppo shell 2026-11-09
                "com.sec.android.app.sbrowser", // suy luận
                "com.heytap.browser", // suy luận
                "com.android.browser" // suy luận
            ),
            DockRole.CAMERA to listOf(
                "com.oplus.camera", // đo Oppo shell 2026-11-09
                "com.vinsmart.camera", // đo Live 4 M3
                "com.google.android.GoogleCamera", // suy luận
                "com.sec.android.app.camera", // suy luận
                "com.android.camera2", // suy luận
                "com.android.camera" // suy luận
            )
        )
    }
}
