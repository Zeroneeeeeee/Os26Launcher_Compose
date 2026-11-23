package gambi.global.os26launcher.domain.config

/**
 * CF — nhóm "Màn hình nền" (3.1–3.4). Ba công tắc đầu là `HomeToggles` cũ (HT): mặc định bật hết = hành vi trước HT.
 * [swipeDownSearch]: vuốt xuống ở giữa màn hình mở tìm kiếm. [searchPill]: nút "Tìm kiếm" phía trên Dock. [pageDots]: chấm chỉ vị trí trang.
 * `docs/config-schema.md` mục 3.3.
 */
data class HomeScreenConfig(
    val pageDots: Boolean = true,
    val swipeDownSearch: Boolean = true,
    val searchPill: Boolean = true,
    val pageTransition: PageTransition = PageTransition.DEFAULT,
    val libraryEnabled: Boolean = true,
    val libraryStyle: LibraryStyle = LibraryStyle.FOLDERS,
    val libraryAutoCategorize: Boolean = true,
    val librarySuggestions: Boolean = false,
    val newAppPlacement: NewAppPlacement = NewAppPlacement.HOME,
    val badgesEnabled: Boolean = false,
    val badgeStyle: BadgeStyle = BadgeStyle.NUMBER,
    /** BG: app tắt huy hiệu, mỗi phần tử là `BadgeTarget.storageKey` ("gói#serialUser"). Rỗng = mọi app hiện huy hiệu. */
    val badgeDisabledApps: Set<String> = emptySet(),
    /** TV (Q6): vuốt phải ở trang 1 mở màn widget (Today). */
    val todayEnabled: Boolean = true
)
