package gambi.global.os26launcher.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gambi.global.os26launcher.domain.config.GeneralConfig
import gambi.global.os26launcher.domain.config.GeneralConfigRepository
import gambi.global.os26launcher.domain.model.GridPreset
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import gambi.global.os26launcher.data.config.GeneralKeys as K

/** CF — nhóm Chung trên `launcher_settings`. */
@Singleton
class GeneralConfigRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : GeneralConfigRepository {

    override val config: Flow<GeneralConfig> = dataStore.groupFlow(TAG, ::read)

    override suspend fun update(transform: (GeneralConfig) -> GeneralConfig) =
        dataStore.updateGroup(::read, transform) { old, new ->
            putEnumIfChanged(K.GRID_PRESET, old.gridPreset, new.gridPreset)
            putIfChanged(K.NAV_BAR_HIDDEN, old.navBarHidden, new.navBarHidden)
            putIfChanged(K.AUTO_ARRANGE, old.autoArrange, new.autoArrange)
            putIfChanged(K.BLUR_EFFECT, old.blurEffect, new.blurEffect)
            putIfChanged(K.TEXT_FOLLOW_SYSTEM, old.textFollowSystem, new.textFollowSystem)
            putIfChanged(K.TEXT_SCALE, old.textScale, new.textScale)
            putIfChanged(K.HIDDEN_APPS_LOCK, old.hiddenAppsLock, new.hiddenAppsLock)
        }

    private fun read(p: Preferences): GeneralConfig {
        val d = GeneralConfig()
        return GeneralConfig(
            // M8a: tên lạ/null rơi về mặc định (fromName), không valueOf.
            gridPreset = GridPreset.fromName(p[K.GRID_PRESET]),
            navBarHidden = p[K.NAV_BAR_HIDDEN] ?: d.navBarHidden,
            autoArrange = p[K.AUTO_ARRANGE] ?: d.autoArrange,
            blurEffect = p[K.BLUR_EFFECT] ?: d.blurEffect,
            textFollowSystem = p[K.TEXT_FOLLOW_SYSTEM] ?: d.textFollowSystem,
            textScale = p.percent(K.TEXT_SCALE, d.textScale, GeneralConfig.TEXT_SCALE_MIN, GeneralConfig.TEXT_SCALE_MAX),
            hiddenAppsLock = p[K.HIDDEN_APPS_LOCK] ?: d.hiddenAppsLock
        )
    }

    private companion object {
        const val TAG = "GeneralConfigRepo"
    }
}
