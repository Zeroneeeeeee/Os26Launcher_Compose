package gambi.global.os26launcher.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gambi.global.os26launcher.domain.config.MotionConfig
import gambi.global.os26launcher.domain.config.MotionConfigRepository
import gambi.global.os26launcher.domain.config.enumFromName
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import gambi.global.os26launcher.data.config.MotionKeys as K

/** CF — nhóm Chuyển cảnh trên `launcher_settings`. TR: màn 4.1 ghi, `TransitionMotion` đọc; màn 4.2 chưa có. */
@Singleton
class MotionConfigRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : MotionConfigRepository {

    override val config: Flow<MotionConfig> = dataStore.groupFlow(TAG, ::read)

    override suspend fun update(transform: (MotionConfig) -> MotionConfig) =
        dataStore.updateGroup(::read, transform) { old, new ->
            putEnumIfChanged(K.TRANSITION_STYLE, old.transitionStyle, new.transitionStyle)
            putEnumIfChanged(K.TRANSITION_SPEED, old.transitionSpeed, new.transitionSpeed)
            putIfChanged(K.REDUCE, old.reduceMotion, new.reduceMotion)
            putEnumIfChanged(K.APP_OPEN_EFFECT, old.appOpenEffect, new.appOpenEffect)
            putIfChanged(K.APP_OPEN_SPEED, old.appOpenSpeed, new.appOpenSpeed)
            putIfChanged(K.PRESS_SHRINK, old.pressShrink, new.pressShrink)
            putIfChanged(K.HAPTIC_ON_OPEN, old.hapticOnOpen, new.hapticOnOpen)
        }

    private fun read(p: Preferences): MotionConfig {
        val d = MotionConfig()
        return MotionConfig(
            transitionStyle = enumFromName(p[K.TRANSITION_STYLE], d.transitionStyle),
            transitionSpeed = enumFromName(p[K.TRANSITION_SPEED], d.transitionSpeed),
            reduceMotion = p[K.REDUCE] ?: d.reduceMotion,
            appOpenEffect = enumFromName(p[K.APP_OPEN_EFFECT], d.appOpenEffect),
            appOpenSpeed = p.percent(K.APP_OPEN_SPEED, d.appOpenSpeed),
            pressShrink = p[K.PRESS_SHRINK] ?: d.pressShrink,
            hapticOnOpen = p[K.HAPTIC_ON_OPEN] ?: d.hapticOnOpen
        )
    }

    private companion object {
        const val TAG = "MotionConfigRepo"
    }
}
