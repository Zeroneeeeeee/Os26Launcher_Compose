package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.repository.LayoutRepository
import javax.inject.Inject

/**
 * TF — "Xóa khỏi Màn hình chính": gỡ icon khỏi lưới / thư mục / dock nhưng KHÔNG ẩn app (khác [HideAppUseCase]): app vẫn ở Library, tìm kiếm và
 * lịch sử app gần đây.
 */
class RemoveFromHomeUseCase @Inject constructor(
    private val layoutRepository: LayoutRepository
) {
    suspend operator fun invoke(key: ComponentKey) = layoutRepository.removeFromHome(key, key.userSerial)
}

/** LD — thả app kéo từ App Library xuống lưới / dock: tạo hàng mới đúng chỗ (xem `LayoutRepository.addAppAt`). */
class AddAppAtUseCase @Inject constructor(
    private val layoutRepository: LayoutRepository
) {
    suspend operator fun invoke(key: ComponentKey, targetContainer: Long, targetPageId: Long?, cell: gambi.global.os26launcher.domain.model.GridPosition): Boolean =
        layoutRepository.addAppAt(key, targetContainer, targetPageId, cell)
}
