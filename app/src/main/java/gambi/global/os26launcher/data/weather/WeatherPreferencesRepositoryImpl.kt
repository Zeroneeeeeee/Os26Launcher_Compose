package gambi.global.os26launcher.data.weather

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import gambi.global.os26launcher.domain.model.weather.TemperatureUnit
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationKind
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationSettings
import gambi.global.os26launcher.domain.weather.WeatherPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : WeatherPreferencesRepository {

    private val safeData: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val unit: Flow<TemperatureUnit> = safeData
        .map { TemperatureUnit.fromName(it[WeatherPrefsKeys.TEMPERATURE_UNIT]) }
        .distinctUntilChanged() // ghi khóa không liên quan không đánh thức widget

    override suspend fun setUnit(unit: TemperatureUnit) {
        dataStore.edit { it[WeatherPrefsKeys.TEMPERATURE_UNIT] = unit.name }
    }

    override val notifications: Flow<WeatherNotificationSettings> = safeData
        .map { prefs -> settingsOf(prefs) }
        .distinctUntilChanged()

    override suspend fun setNotificationEnabled(kind: WeatherNotificationKind, enabled: Boolean) {
        dataStore.edit { it[WeatherPrefsKeys.notifyKey(kind)] = enabled }
    }

    override suspend fun notificationSettings(): WeatherNotificationSettings = settingsOf(safeData.first())

    override suspend fun lastShownDay(kind: WeatherNotificationKind): String? =
        safeData.first()[WeatherPrefsKeys.shownKey(kind)]

    override suspend fun markShown(kind: WeatherNotificationKind, day: String) {
        dataStore.edit { it[WeatherPrefsKeys.shownKey(kind)] = day }
    }

    private fun settingsOf(prefs: Preferences) = WeatherNotificationSettings(
        daily = prefs[WeatherPrefsKeys.NOTIFY_DAILY] ?: false,
        tomorrow = prefs[WeatherPrefsKeys.NOTIFY_TOMORROW] ?: false
    )
}
