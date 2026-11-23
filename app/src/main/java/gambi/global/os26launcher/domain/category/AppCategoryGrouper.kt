package gambi.global.os26launcher.domain.category

import gambi.global.os26launcher.domain.model.AppCategory
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.CategoryGroup
import gambi.global.os26launcher.domain.model.CategoryGroupApps

/** Phân app vào nhóm App Library (G2a B1, D12). Hàm thuần. */
object AppCategoryGrouper {

    /** Luật dự phòng: loại không rõ/khác → app hệ thống vào "Tiện ích", còn lại vào "Khác". */
    fun groupOf(category: AppCategory, isSystem: Boolean): CategoryGroup = when (category) {
        AppCategory.SOCIAL -> CategoryGroup.SOCIAL
        AppCategory.ENTERTAINMENT -> CategoryGroup.ENTERTAINMENT
        AppCategory.GAME -> CategoryGroup.GAME
        AppCategory.PRODUCTIVITY -> CategoryGroup.PRODUCTIVITY
        AppCategory.NEWS -> CategoryGroup.NEWS
        AppCategory.MAPS -> CategoryGroup.MAPS
        AppCategory.OTHER, AppCategory.UNDEFINED -> if (isSystem) CategoryGroup.UTILITIES else CategoryGroup.OTHER
    }

    /** Nhóm rỗng bỏ; nhóm theo thứ tự [CategoryGroup]; trong nhóm giữ thứ tự của [apps] (đã sắp theo nhãn). Mỗi app đúng một nhóm. */
    fun group(apps: List<AppItem>): List<CategoryGroupApps> {
        val byGroup = apps.groupBy { groupOf(it.category, it.isSystem) }
        return CategoryGroup.REGULAR // LO: chỉ 8 nhóm thường, nhóm đặc biệt do GetCategoryGroupsUseCase thêm
            .mapNotNull { g -> byGroup[g]?.let { CategoryGroupApps(g, it) } }
    }
}
