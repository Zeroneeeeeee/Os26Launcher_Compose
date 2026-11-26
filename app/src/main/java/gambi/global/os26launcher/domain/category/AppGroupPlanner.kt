package gambi.global.os26launcher.domain.category

import gambi.global.os26launcher.domain.layout.FolderPlanner
import gambi.global.os26launcher.domain.model.AppCategory
import gambi.global.os26launcher.domain.model.AppGroupKind
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.HomePage
import gambi.global.os26launcher.domain.model.PlacedApp

/**
 * AG (2026-11-26) — nhận diện app theo [AppGroupKind] và chọn app được gom. Hàm THUẦN (không `android.*`). Luật: bảng "Nhận diện nhóm"
 * của `docs/plans/AG-app-groups.md`. `ApplicationInfo.category` hay vắng (trường tùy chọn của nhà phát triển) nên Google, mạng xã hội và
 * app của hãng dựa thêm vào tên gói.
 */
object AppGroupPlanner {

    /**
     * [app] có thuộc nhóm [kind] không. App launcher của mình ([selfPackage]) không thuộc nhóm nào. Mở lại 2026-11-26 (QĐ 13): app lõi của
     * máy ([isCore]: Danh bạ, Ghi chú, Tin nhắn, Cài đặt, Thư viện ảnh, Máy ảnh, Điện thoại…) CHỈ thuộc "Tiện ích" (trước đó không thuộc nhóm
     * nào); app đang ở dock không bao giờ là ứng viên vì nơi gọi chỉ đưa app trên lưới / ngoài dock.
     */
    fun matches(kind: AppGroupKind, app: AppItem, selfPackage: String): Boolean {
        val pkg = app.key.packageName
        if (pkg == selfPackage) return false
        val core = isCore(pkg)
        return when (kind) {
            // Lõi, hoặc app hệ thống tiện ích thật (Đồng hồ, Máy tính, Tệp, Ghi âm, Lịch, Thời tiết, La bàn…) / app AOSP `com.android.*`.
            // Không lấy mọi app hệ thống "không khai loại": phần lớn app cài sẵn của hãng như vậy, sẽ biến Tiện ích thành bãi chứa và
            // làm thư mục "Ứng dụng của hãng" không bao giờ đủ app (review 2026-11-26).
            AppGroupKind.UTILITIES -> core || (
                app.isSystem && !isGoogleService(pkg) && pkg !in SOCIAL_PACKAGES &&
                    (pkg.startsWith("com.android.") || hasSegment(pkg, UTILITY_SEGMENTS))
                )
            AppGroupKind.GOOGLE -> !core && isGoogleService(pkg)
            AppGroupKind.SOCIAL -> !core && (app.category == AppCategory.SOCIAL || pkg in SOCIAL_PACKAGES)
            AppGroupKind.GAMES -> !core && app.category == AppCategory.GAME
            AppGroupKind.ENTERTAINMENT -> !core && app.category == AppCategory.ENTERTAINMENT && !app.isSystem
            // Chỉ app cài sẵn: app của hãng khác tải từ CH Play (vd com.xiaomi.smarthome trên máy OPPO) không vào thư mục của hãng máy.
            AppGroupKind.MANUFACTURER -> !core && app.isSystem && MANUFACTURER_PREFIXES.any { pkg.startsWith(it) }
        }
    }

    /**
     * Mở lại 2026-11-26 (QĐ 12, 15): nhóm cho bố cục lần đầu. Duyệt [AppGroupKind] theo thứ tự khai báo, mỗi app vào nhóm ĐẦU TIÊN khớp;
     * nhóm < 2 app bị bỏ (app đó nằm phẳng); mỗi nhóm tối đa [FolderPlanner.MAX_ITEMS]. Trong nhóm giữ thứ tự của [apps] (đã sắp theo nhãn).
     * [apps] phải là app được phép vào thư mục (nơi gọi đã bỏ app ở dock).
     */
    fun seedGroups(apps: List<AppItem>, selfPackage: String): List<Pair<AppGroupKind, List<AppItem>>> {
        val taken = HashSet<ComponentKey>()
        return AppGroupKind.entries.mapNotNull { kind ->
            val members = apps.filter { it.key !in taken && matches(kind, it, selfPackage) }.take(FolderPlanner.MAX_ITEMS)
            if (members.size < 2) {
                null
            } else {
                members.mapTo(taken) { it.key }
                kind to members
            }
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
     * App tiện ích chính của máy (thường một phần ở dock): chỉ vào "Tiện ích" (QĐ 13). Khớp theo danh sách gói hoặc đoạn tên gói (giữa hai
     * dấu chấm) của Điện thoại / Danh bạ / Tin nhắn / Máy ảnh / Cài đặt / Ghi chú / Thư viện ảnh của AOSP, Google và các hãng (vd
     * `com.samsung.android.dialer`, `com.oplus.camera`, `com.coloros.note`, `com.coloros.gallery3d`, `com.android.settings`).
     */
    private fun isCore(pkg: String): Boolean =
        pkg in GOOGLE_CORE || pkg in CORE_PACKAGES || hasSegment(pkg, CORE_SEGMENTS) ||
            pkg.split('.').any { it.startsWith("gallery", ignoreCase = true) }

    /** Gói có một đoạn (giữa hai dấu chấm) trùng một chữ trong [segments], không phân biệt hoa thường. */
    private fun hasSegment(pkg: String, segments: Set<String>): Boolean =
        pkg.split('.').any { seg -> segments.any { seg.equals(it, ignoreCase = true) } }

    /** Dịch vụ Google (vào thư mục "Google"): gói `com.google.*`, Chrome, CH Play (QĐ 17); trừ app lõi [GOOGLE_CORE]. */
    private fun isGoogleService(pkg: String): Boolean =
        (pkg.startsWith("com.google.") || pkg == "com.android.chrome" || pkg == "com.android.vending") && pkg !in GOOGLE_CORE

    /** Đoạn tên gói của app tiện ích cài sẵn (vd `com.coloros.alarmclock`, `com.sec.android.app.myfiles`, `com.miui.weather2`). */
    private val UTILITY_SEGMENTS = setOf(
        "clock", "alarmclock", "deskclock", "calculator", "calculator2", "filemanager", "myfiles", "files", "fileexplorer",
        "soundrecorder", "recorder", "calendar", "weather", "weather2", "compass"
    )

    private val CORE_PACKAGES = setOf("com.android.settings", "com.android.mms", "com.android.camera2")

    /** Đoạn tên gói của app lõi (khớp nguyên đoạn, không phân biệt hoa thường); thêm: đoạn bắt đầu bằng "gallery" (vd `gallery3d`). */
    private val CORE_SEGMENTS = setOf("dialer", "contacts", "incallui", "mms", "messaging", "camera", "settings", "notes", "note")

    /** App lõi của máy dù là của Google: không vào "Google" mà vào "Tiện ích" (QĐ 13; FL Q5). */
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
        "com.google.android.gsf"
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
