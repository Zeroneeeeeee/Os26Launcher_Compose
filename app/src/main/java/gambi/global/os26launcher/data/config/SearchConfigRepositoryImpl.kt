package gambi.global.os26launcher.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gambi.global.os26launcher.domain.config.SearchConfig
import gambi.global.os26launcher.domain.config.SearchConfigRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import gambi.global.os26launcher.data.config.SearchKeys as K

/** CF — nhóm Tìm kiếm trên `launcher_settings` (thay `SearchPreferencesRepositoryImpl` của M8d). */
@Singleton
class SearchConfigRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SearchConfigRepository {

    // distinctUntilChanged trong groupFlow: ghi khóa không liên quan không làm chỉ mục tìm kiếm dựng lại.
    override val config: Flow<SearchConfig> = dataStore.groupFlow(TAG, ::read)

    override suspend fun update(transform: (SearchConfig) -> SearchConfig) =
        dataStore.updateGroup(::read, transform) { old, new ->
            putIfChanged(K.SYSTEM_SETTINGS, old.systemSettings, new.systemSettings)
            putIfChanged(K.SHORTCUTS, old.shortcuts, new.shortcuts)
            putIfChanged(K.SUGGESTIONS, old.suggestions, new.suggestions)
            putIfChanged(K.RECENT_APPS, old.recentApps, new.recentApps)
            putIfChanged(K.CONTACTS, old.contacts, new.contacts)
            putIfChanged(K.CONTACTS_ASKED, old.contactsAsked, new.contactsAsked)
            putIfChanged(K.FILES, old.files, new.files)
            putIfChanged(K.WEB_SUGGESTIONS, old.webSuggestions, new.webSuggestions)
            putNullableIfChanged(K.WEB_ENGINE, old.webEngine, new.webEngine)
        }

    private fun read(p: Preferences): SearchConfig {
        val d = SearchConfig()
        return SearchConfig(
            systemSettings = p[K.SYSTEM_SETTINGS] ?: d.systemSettings,
            shortcuts = p[K.SHORTCUTS] ?: d.shortcuts,
            suggestions = p[K.SUGGESTIONS] ?: d.suggestions,
            recentApps = p[K.RECENT_APPS] ?: p[K.SUGGESTIONS] ?: d.recentApps, // P3-5: chuyển giá trị cũ khi chưa ghi khóa mới
            contacts = p[K.CONTACTS] ?: d.contacts,
            contactsAsked = p[K.CONTACTS_ASKED] ?: d.contactsAsked,
            files = p[K.FILES] ?: d.files,
            webSuggestions = p[K.WEB_SUGGESTIONS] ?: d.webSuggestions,
            webEngine = p[K.WEB_ENGINE]?.takeIf { it.isNotBlank() }
        )
    }

    private companion object {
        const val TAG = "SearchConfigRepo"
    }
}
