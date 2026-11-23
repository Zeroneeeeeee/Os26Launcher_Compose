package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.repository.ShortcutRepository
import javax.inject.Inject

/** M7 Task 10 — mở 1 shortcut từ menu nhấn giữ icon. */
class LaunchShortcutUseCase @Inject constructor(
    private val shortcutRepository: ShortcutRepository
) {
    operator fun invoke(key: ComponentKey, shortcutId: String): Boolean =
        shortcutRepository.startShortcut(key, shortcutId)
}
