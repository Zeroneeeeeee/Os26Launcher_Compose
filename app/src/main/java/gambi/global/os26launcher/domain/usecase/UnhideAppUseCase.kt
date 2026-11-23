package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.repository.LayoutRepository
import javax.inject.Inject

/** M7 — bỏ ẩn 1 app: xóa khỏi danh sách đã ẩn và đặt lại vào ô trống đầu tiên trên màn hình chính. */
class UnhideAppUseCase @Inject constructor(
    private val layoutRepository: LayoutRepository
) {
    suspend operator fun invoke(key: ComponentKey, userSerial: Long) {
        layoutRepository.setHidden(key, userSerial, hidden = false)
    }
}
