package gambi.global.os26launcher.data.apps

import android.content.pm.ApplicationInfo
import gambi.global.os26launcher.domain.model.AppCategory

/** Đổi `ApplicationInfo.category` (hằng số hệ thống) sang [AppCategory] (G2a B1, D12). */
internal fun mapPlatformCategory(raw: Int): AppCategory = when (raw) {
    ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
    ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_IMAGE -> AppCategory.ENTERTAINMENT
    ApplicationInfo.CATEGORY_GAME -> AppCategory.GAME
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
    ApplicationInfo.CATEGORY_NEWS -> AppCategory.NEWS
    ApplicationInfo.CATEGORY_MAPS -> AppCategory.MAPS
    ApplicationInfo.CATEGORY_UNDEFINED -> AppCategory.UNDEFINED
    else -> AppCategory.OTHER
}
