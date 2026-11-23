package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.config.AppearanceRepository
import gambi.global.os26launcher.domain.config.ThemeId
import gambi.global.os26launcher.domain.config.ThemePresets.applyPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** CF — theme đang chọn và đã bị chỉnh tay chưa (config hiện tại khác preset của theme đó). [id] null = chưa từng chọn theme. */
data class ThemeState(val id: ThemeId?, val modified: Boolean)

class ObserveThemeStateUseCase @Inject constructor(
    private val appearance: AppearanceRepository
) {
    operator fun invoke(): Flow<ThemeState> = appearance.config
        .map { config ->
            val id = config.themeId
            ThemeState(id, modified = id != null && config.applyPreset(id) != config)
        }
        .distinctUntilChanged()
}
