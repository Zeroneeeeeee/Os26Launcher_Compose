package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.AppShortcutItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.repository.ShortcutRepository
import javax.inject.Inject

/** M7 Task 10 — shortcut hiện trong menu nhấn giữ icon. Không bao giờ ném lỗi: thất bại = danh sách rỗng. */
class GetAppShortcutsUseCase @Inject constructor(
    private val shortcutRepository: ShortcutRepository
) {
    suspend operator fun invoke(key: ComponentKey, iconSizePx: Int): List<AppShortcutItem> =
        shortcutRepository.getShortcuts(key, iconSizePx)
}
