package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.category.AppCategoryGrouper
import gambi.global.os26launcher.domain.category.LibraryArranger
import gambi.global.os26launcher.domain.config.HomeScreenConfigRepository
import gambi.global.os26launcher.domain.config.LibraryStyle
import gambi.global.os26launcher.domain.model.CategoryGroup
import gambi.global.os26launcher.domain.model.CategoryGroupApps
import gambi.global.os26launcher.domain.model.LibraryContent
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.repository.LayoutRepository
import gambi.global.os26launcher.domain.repository.RecentAppsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * G2a B2 (D12) — nội dung App Library: mọi app đã cài TRỪ app ẩn. LO: theo cấu hình màn 3.2 — kiểu "Danh sách A–Z" ra
 * [LibraryContent.Alphabetical]; kiểu thư mục ra [LibraryContent.Folders] gồm (nếu bật "Gợi ý & Mới thêm") nhóm Gợi ý, Mới thêm, rồi 8 nhóm
 * của [AppCategoryGrouper] (hoặc một nhóm "Tất cả ứng dụng" khi tắt "Tự phân loại"). Nhóm rỗng đã được bỏ, danh sách rỗng = Empty.
 * Không tự phát `null`: giá trị đầu `null` (Loading) do `stateIn` của người gọi đảm nhiệm (C1).
 *
 * `.filter { isNotEmpty() }`: [AppRepository.apps] luôn phát danh sách rỗng trước giá trị thật (cùng lý do với
 * `AppLibraryViewModel`), nếu không sẽ nháy trạng thái Empty trong lúc chưa có dữ liệu.
 */
class GetCategoryGroupsUseCase @Inject constructor(
    private val appRepository: AppRepository,
    private val layoutRepository: LayoutRepository,
    private val homeScreenConfig: HomeScreenConfigRepository, // LO
    private val recentApps: RecentAppsRepository // LO D6
) {
    private data class LibraryPrefs(val style: LibraryStyle, val autoCategorize: Boolean, val suggestions: Boolean)

    operator fun invoke(): Flow<LibraryContent> =
        combine(
            appRepository.apps.filter { it.isNotEmpty() },
            layoutRepository.hiddenApps,
            homeScreenConfig.config
                .map {
                    // LO D9: hai công tắc đang ẩn ở màn 3.2 thì dùng giá trị cố định (8 nhóm, không Gợi ý/Mới thêm), bỏ qua giá trị đã lưu.
                    if (FOLDER_SWITCHES_ENABLED) LibraryPrefs(it.libraryStyle, it.libraryAutoCategorize, it.librarySuggestions)
                    else LibraryPrefs(it.libraryStyle, autoCategorize = true, suggestions = false)
                }
                .distinctUntilChanged(), // công tắc khác của Màn hình chính đổi thì không dựng lại Library
            recentApps.entries
        ) { all, hidden, prefs, recent ->
            val hiddenKeys = hidden.mapTo(HashSet()) { it.key }
            val visible = all.filter { it.key !in hiddenKeys }
            if (prefs.style == LibraryStyle.ALPHABETICAL) {
                LibraryContent.Alphabetical(LibraryArranger.sections(visible))
            } else {
                val groups = ArrayList<CategoryGroupApps>()
                if (prefs.suggestions && visible.isNotEmpty()) {
                    LibraryArranger.suggested(visible, recent).takeIf { it.isNotEmpty() }
                        ?.let { groups.add(CategoryGroupApps(CategoryGroup.SUGGESTED, it)) }
                    LibraryArranger.recentlyAdded(visible, System.currentTimeMillis()).takeIf { it.isNotEmpty() }
                        ?.let { groups.add(CategoryGroupApps(CategoryGroup.RECENTLY_ADDED, it)) }
                }
                if (prefs.autoCategorize) {
                    groups.addAll(AppCategoryGrouper.group(visible))
                } else if (visible.isNotEmpty()) {
                    groups.add(CategoryGroupApps(CategoryGroup.ALL, LibraryArranger.alphabetical(visible)))
                }
                LibraryContent.Folders(groups)
            }
        }.distinctUntilChanged() // mỗi lần mở app lịch sử phát lại: nội dung không đổi thì không dựng lại ô

    companion object {
        /** LO D9 (chủ dự án 2026-11-18): "Tự phân loại" và "Gợi ý & Mới thêm" tạm ẩn; cùng cờ với `LibrarySettingsScreen`. */
        const val FOLDER_SWITCHES_ENABLED = false
    }
}
