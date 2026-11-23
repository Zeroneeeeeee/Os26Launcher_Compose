package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.config.AppearanceRepository
import gambi.global.os26launcher.domain.config.ThemeId
import gambi.global.os26launcher.domain.config.ThemePresets.applyPreset
import javax.inject.Inject

/** CF — áp theme một lần: MỘT `update` ghi mọi trường theme của preset (không bao giờ thấy trạng thái áp dở). */
class ApplyThemeUseCase @Inject constructor(
    private val appearance: AppearanceRepository
) {
    suspend operator fun invoke(id: ThemeId) = appearance.update { it.applyPreset(id) }
}
