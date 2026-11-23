package gambi.global.os26launcher.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import gambi.global.os26launcher.domain.config.AppState
import gambi.global.os26launcher.domain.config.AppStateRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import gambi.global.os26launcher.data.config.AppStateKeys as K

/** CF — cờ nội bộ trên `launcher_settings`. */
@Singleton
class AppStateRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : AppStateRepository {

    override val state: Flow<AppState> = dataStore.groupFlow(TAG) { p ->
        AppState(
            onboardingDone = p[K.ONBOARDING_DONE] ?: false,
            todaySeeded = p[K.TODAY_SEEDED] ?: false
        )
    }

    override suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[K.ONBOARDING_DONE] = done }
    }

    private companion object {
        const val TAG = "AppStateRepo"
    }
}
