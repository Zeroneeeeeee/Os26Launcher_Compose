package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.GridPosition
import gambi.global.os26launcher.domain.model.WidgetKind
import gambi.global.os26launcher.domain.repository.LayoutRepository
import javax.inject.Inject

/** G2c — thêm widget tự vẽ vào hình chữ nhật trống đầu tiên (không đẩy icon). Trả id trang đã đặt, null nếu không hợp lệ. */
class AddWidgetUseCase @Inject constructor(
    private val layoutRepository: LayoutRepository
) {
    suspend operator fun invoke(
        kind: WidgetKind,
        spanX: Int,
        spanY: Int,
        preferredPageId: Long?
    ): Long? =
        layoutRepository.addWidget(kind, spanX, spanY, preferredPageId)

    /** G2c Task 13 — thả widget kéo từ bảng chọn đúng chỗ người dùng chọn (có thể đẩy icon). false = bị từ chối, không ghi gì. */
    suspend operator fun invoke(
        kind: WidgetKind,
        spanX: Int,
        spanY: Int,
        targetPageId: Long,
        cell: GridPosition
    ): Boolean =
        layoutRepository.addWidgetAt(kind, spanX, spanY, targetPageId, cell)
}
