package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.repository.AppLabelRepository
import javax.inject.Inject

/** RN — bỏ tên riêng của một app, về tên gốc. */
class RestoreAppLabelUseCase @Inject constructor(
    private val labels: AppLabelRepository
) {
    suspend operator fun invoke(key: ComponentKey) = labels.setCustomLabel(key, null)
}
