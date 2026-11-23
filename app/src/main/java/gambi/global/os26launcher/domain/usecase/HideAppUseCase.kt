package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.repository.LayoutRepository
import gambi.global.os26launcher.domain.repository.RecentAppsRepository
import javax.inject.Inject

/** M7 — ẩn 1 app: gỡ khỏi bố cục (lưới hoặc dock) và ghi vào danh sách đã ẩn. */
class HideAppUseCase @Inject constructor(
    private val layoutRepository: LayoutRepository,
    private val recentApps: RecentAppsRepository // P3-7
) {
    suspend operator fun invoke(key: ComponentKey, userSerial: Long) {
        layoutRepository.setHidden(key, userSerial, hidden = true)
        // P3-7 (Q12): xóa hẳn khỏi lịch sử app gần đây, bỏ ẩn thì app bắt đầu lại từ đầu.
        recentApps.remove(key)
    }
}
