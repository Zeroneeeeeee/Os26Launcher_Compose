package gambi.global.os26launcher.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gambi.global.os26launcher.domain.config.HomeScreenConfig
import gambi.global.os26launcher.domain.config.HomeScreenConfigRepository
import gambi.global.os26launcher.domain.config.enumFromName
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import gambi.global.os26launcher.data.config.HomeScreenKeys as K

/** CF — nhóm Màn hình nền trên `launcher_settings` (thay `HomeTogglesRepositoryImpl` của HT). */
@Singleton
class HomeScreenConfigRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : HomeScreenConfigRepository {

    // distinctUntilChanged trong groupFlow: ghi khóa không liên quan không làm Home phản ứng.
    override val config: Flow<HomeScreenConfig> = dataStore.groupFlow(TAG, ::read)

    override suspend fun update(transform: (HomeScreenConfig) -> HomeScreenConfig) =
        dataStore.updateGroup(::read, transform) { old, new ->
            putIfChanged(K.PAGE_DOTS, old.pageDots, new.pageDots)
            putIfChanged(K.SWIPE_DOWN_SEARCH, old.swipeDownSearch, new.swipeDownSearch)
            putIfChanged(K.SEARCH_PILL, old.searchPill, new.searchPill)
            putEnumIfChanged(K.PAGE_TRANSITION, old.pageTransition, new.pageTransition)
            putIfChanged(K.LIBRARY_ENABLED, old.libraryEnabled, new.libraryEnabled)
            putEnumIfChanged(K.LIBRARY_STYLE, old.libraryStyle, new.libraryStyle)
            putIfChanged(K.LIBRARY_AUTO_CATEGORIZE, old.libraryAutoCategorize, new.libraryAutoCategorize)
            putIfChanged(K.LIBRARY_SUGGESTIONS, old.librarySuggestions, new.librarySuggestions)
            putEnumIfChanged(K.NEW_APP_PLACEMENT, old.newAppPlacement, new.newAppPlacement)
            putIfChanged(K.BADGES_ENABLED, old.badgesEnabled, new.badgesEnabled)
            putEnumIfChanged(K.BADGE_STYLE, old.badgeStyle, new.badgeStyle)
            putIfChanged(K.BADGE_DISABLED_APPS, old.badgeDisabledApps, new.badgeDisabledApps)
            putIfChanged(K.TODAY_ENABLED, old.todayEnabled, new.todayEnabled)
        }

    private fun read(p: Preferences): HomeScreenConfig {
        val d = HomeScreenConfig()
        return HomeScreenConfig(
            pageDots = p[K.PAGE_DOTS] ?: d.pageDots,
            todayEnabled = p[K.TODAY_ENABLED] ?: d.todayEnabled,
            swipeDownSearch = p[K.SWIPE_DOWN_SEARCH] ?: d.swipeDownSearch,
            searchPill = p[K.SEARCH_PILL] ?: d.searchPill,
            pageTransition = enumFromName(p[K.PAGE_TRANSITION], d.pageTransition),
            libraryEnabled = p[K.LIBRARY_ENABLED] ?: d.libraryEnabled,
            libraryStyle = enumFromName(p[K.LIBRARY_STYLE], d.libraryStyle),
            libraryAutoCategorize = p[K.LIBRARY_AUTO_CATEGORIZE] ?: d.libraryAutoCategorize,
            librarySuggestions = p[K.LIBRARY_SUGGESTIONS] ?: d.librarySuggestions,
            newAppPlacement = enumFromName(p[K.NEW_APP_PLACEMENT], d.newAppPlacement),
            badgesEnabled = p[K.BADGES_ENABLED] ?: d.badgesEnabled,
            badgeStyle = enumFromName(p[K.BADGE_STYLE], d.badgeStyle),
            badgeDisabledApps = p[K.BADGE_DISABLED_APPS] ?: d.badgeDisabledApps
        )
    }

    private companion object {
        const val TAG = "HomeScreenConfigRepo"
    }
}
