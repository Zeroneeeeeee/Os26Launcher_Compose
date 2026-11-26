package gambi.global.os26launcher.domain.category

import gambi.global.os26launcher.domain.model.AppCategory
import gambi.global.os26launcher.domain.model.AppGroupKind
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.HomePage
import gambi.global.os26launcher.domain.model.PlacedApp

/**
 * AG (2026-11-26) — nhận diện app theo [AppGroupKind] và chọn app được gom. Hàm THUẦN (không `android.*`). Luật: bảng "Nhận diện nhóm"
 * của `docs/plans/AG-app-groups.md`. `ApplicationInfo.category` hay vắng (trường tùy chọn của nhà phát triển) nên Google, mạng xã hội và
 * app của hãng dựa thêm vào tên gói.
 */
object AppGroupPlanner {

    /**
     * [app] có thuộc nhóm [kind] không. App launcher của mình ([selfPackage]) và app lõi của máy ([isCore]: Điện thoại, Danh bạ, Tin nhắn,
     * Máy ảnh, Cài đặt, CH Play) không thuộc nhóm nào, kể cả "Tiện ích" và "Ứng dụng của hãng".
     */
    fun matches(kind: AppGroupKind, app: AppItem, selfPackage: String): Boolean {
        val pkg = app.key.packageName
        if (pkg == selfPackage || isCore(pkg)) return false
        return when (kind) {
            AppGroupKind.GOOGLE -> (pkg.startsWith("com.google.") || pkg == "com.android.chrome") && pkg !in GOOGLE_CORE
            AppGroupKind.SOCIAL -> app.category == AppCategory.SOCIAL || pkg in SOCIAL_PACKAGES
            AppGroupKind.GAMES -> app.category == AppCategory.GAME
            AppGroupKind.ENTERTAINMENT -> app.category == AppCategory.ENTERTAINMENT && !app.isSystem
            // Cùng luật nhóm "Tiện ích" của Thư viện (AppCategoryGrouper): app hệ thống không khai loại / loại khác.
            AppGroupKind.UTILITIES -> app.isSystem && (app.category == AppCategory.OTHER || app.category == AppCategory.UNDEFINED)
            // Chỉ app cài sẵn: app của hãng khác tải từ CH Play (vd com.xiaomi.smarthome trên máy OPPO) không vào thư mục của hãng máy.
            AppGroupKind.MANUFACTURER -> app.isSystem && MANUFACTURER_PREFIXES.any { pkg.startsWith(it) }
        }
    }

    /**
     * App nằm phẳng trên lưới Home ([PlacedApp] của [pages]; không gồm dock, app trong thư mục, app ẩn) thuộc nhóm [kind], theo thứ tự
     * trang rồi thứ tự đọc (hàng, rồi cột). App đầu là chỗ đặt thư mục mới.
     */
    fun candidates(kind: AppGroupKind, pages: List<HomePage>, selfPackage: String): List<PlacedApp> =
        pages.sortedBy { it.index }.flatMap { page ->
            page.items.filterIsInstance<PlacedApp>()
                .filter { matches(kind, it.app, selfPackage) }
                .sortedWith(compareBy({ it.position.y }, { it.position.x }))
        }

    /**
     * App lõi của máy (thường ở dock): không gom vào nhóm nào. Khớp theo danh sách gói hoặc đoạn tên gói của Điện thoại / Danh bạ / Tin
     * nhắn / Máy ảnh / Cài đặt của AOSP, Google và các hãng (vd `com.samsung.android.dialer`, `com.oplus.camera`, `com.android.settings`).
     */
    private fun isCore(pkg: String): Boolean =
        pkg in GOOGLE_CORE || pkg in CORE_PACKAGES || CORE_SEGMENTS.any { seg -> pkg.split('.').any { it.equals(seg, ignoreCase = true) } }

    private val CORE_PACKAGES = setOf("com.android.settings", "com.android.vending", "com.android.mms", "com.android.camera2")

    /** Đoạn tên gói (giữa hai dấu chấm) của app lõi. */
    private val CORE_SEGMENTS = setOf("dialer", "contacts", "incallui", "mms", "messaging", "camera", "settings")

    /** App lõi của máy dù là của Google: không gom vào "Google" (FL Q5). */
    private val GOOGLE_CORE = setOf(
        "com.google.android.dialer",
        "com.google.android.contacts",
        "com.google.android.apps.messaging",
        "com.google.android.deskclock",
        "com.google.android.calculator",
        "com.google.android.calendar",
        "com.google.android.GoogleCamera",
        "com.google.android.apps.nbu.files",
        "com.google.android.documentsui",
        "com.google.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.vending"
    )

    /** App mạng xã hội / nhắn tin phổ biến hay thiếu `CATEGORY_SOCIAL`. */
    private val SOCIAL_PACKAGES = setOf(
        "com.facebook.katana", "com.facebook.orca", "com.facebook.lite", "com.facebook.mlite",
        "com.zing.zalo",
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.instagram.android", "com.instagram.barcelona",
        "org.telegram.messenger", "org.thunderdog.challegram",
        "com.whatsapp", "com.whatsapp.w4b",
        "com.twitter.android",
        "com.discord",
        "com.viber.voip",
        "com.snapchat.android",
        "com.linkedin.android",
        "com.pinterest",
        "com.reddit.frontpage",
        "jp.naver.line.android",
        "com.kakao.talk",
        "com.tencent.mm",
        "com.skype.raider"
    )

    /** Tiền tố gói app cài sẵn của các hãng máy. */
    private val MANUFACTURER_PREFIXES = listOf(
        "com.coloros.", "com.oppo.", "com.heytap.", "com.oplus.", "com.realme.", "com.oneplus.",
        "com.samsung.", "com.sec.",
        "com.miui.", "com.xiaomi.", "com.mi.",
        "com.vivo.", "com.bbk.", "com.iqoo.",
        "com.huawei.", "com.hihonor.",
        "com.vinsmart.", "com.vsmart.",
        "com.transsion.",
        "com.motorola.",
        "com.hmdglobal.",
        "com.asus.",
        "com.sonymobile.",
        "com.lge."
    )
}
