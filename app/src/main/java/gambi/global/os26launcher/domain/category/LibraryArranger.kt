package gambi.global.os26launcher.domain.category

import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.LibraryContent
import gambi.global.os26launcher.domain.model.RecentApp
import gambi.global.os26launcher.domain.search.AppSearchMatcher

/** LO — sắp app cho App Library: A–Z theo chữ cái, nhóm Gợi ý, nhóm Mới thêm (plan LO, D4–D6). Hàm thuần. */
object LibraryArranger {

    /** Tối đa số app trong nhóm Gợi ý / Mới thêm (D6). */
    const val SPECIAL_GROUP_MAX = 8

    /** Cửa sổ "Mới thêm" (D6): 30 ngày. */
    const val RECENTLY_ADDED_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

    /** Đoạn "#": chữ số, ký tự, chữ ngoài A–Z sau khi bỏ dấu. Luôn đứng cuối. */
    const val OTHER_LETTER = "#"

    /** Sắp theo tên đã bỏ dấu ("Đ" về "D"), hòa thì theo tên gốc; tên đầu không phải A–Z vào "#" ở cuối. */
    fun alphabetical(apps: List<AppItem>): List<AppItem> =
        apps.map { it to AppSearchMatcher.normalize(it.label) }
            .sortedWith(compareBy<Pair<AppItem, String>>({ letterOf(it.second) == OTHER_LETTER }, { it.second }, { it.first.label }))
            .map { it.first }

    fun sections(apps: List<AppItem>): List<LibraryContent.LetterSection> {
        val out = ArrayList<LibraryContent.LetterSection>()
        var letter: String? = null
        var bucket = ArrayList<AppItem>()
        for (app in alphabetical(apps)) {
            val l = letterOf(AppSearchMatcher.normalize(app.label))
            if (l != letter) {
                if (letter != null && bucket.isNotEmpty()) out.add(LibraryContent.LetterSection(letter, bucket))
                letter = l
                bucket = ArrayList()
            }
            bucket.add(app)
        }
        if (letter != null && bucket.isNotEmpty()) out.add(LibraryContent.LetterSection(letter, bucket))
        return out
    }

    /** Gợi ý: app có trong lịch sử, nhiều lần mở trước, hòa thì mở gần nhất trước; [visible] đã bỏ app ẩn. */
    fun suggested(visible: List<AppItem>, recent: List<RecentApp>): List<AppItem> {
        if (recent.isEmpty()) return emptyList()
        val byKey = visible.associateBy { it.key }
        return recent.sortedWith(compareByDescending<RecentApp> { it.launchCount }.thenByDescending { it.lastLaunchedMillis })
            .mapNotNull { byKey[it.key] }
            .distinctBy { it.key }
            .take(SPECIAL_GROUP_MAX)
    }

    /** Mới thêm: cài trong [RECENTLY_ADDED_WINDOW_MS] tính tới [nowMillis], mới nhất trước, bỏ app hệ thống. */
    fun recentlyAdded(visible: List<AppItem>, nowMillis: Long): List<AppItem> =
        visible.filter { !it.isSystem && it.firstInstallMillis > 0L && nowMillis - it.firstInstallMillis in 0..RECENTLY_ADDED_WINDOW_MS }
            .sortedByDescending { it.firstInstallMillis }
            .take(SPECIAL_GROUP_MAX)

    private fun letterOf(normalized: String): String {
        val c = normalized.firstOrNull() ?: return OTHER_LETTER
        return if (c in 'a'..'z') c.uppercaseChar().toString() else OTHER_LETTER
    }
}
