package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.GridPosition
import gambi.global.os26launcher.domain.repository.LayoutRepository
import javax.inject.Inject

/**
 * M7 — di chuyển 1 item đã có trong bố cục tới một ô đích. Chỉ chuyển tiếp xuống [LayoutRepository];
 * mọi quy tắc chèn/đẩy-dồn/tạo trang mới nằm ở [gambi.global.os26launcher.data.layout.LayoutReconciler],
 * không lặp lại ở đây (domain không biết chi tiết thuật toán lưới).
 */
class MoveItemUseCase @Inject constructor(
    private val layoutRepository: LayoutRepository
) {
    suspend operator fun invoke(
        itemId: Long,
        targetContainer: Long,
        targetPageId: Long?,
        targetCell: GridPosition
    ): Boolean = layoutRepository.moveItem(itemId, targetContainer, targetPageId, targetCell)
}
