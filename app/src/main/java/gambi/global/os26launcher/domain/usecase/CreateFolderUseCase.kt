package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.repository.LayoutRepository
import javax.inject.Inject

/**
 * G2a A1b — gộp app [draggedItemId] đè lên app [targetItemId] thành một thư mục mới. Quy tắc từ chối và ghi DB nằm ở
 * `FolderPlanner` / `LayoutReconciler.createFolder`; ở đây chỉ chuyển tiếp. `false` = bị từ chối, không ghi gì.
 */
class CreateFolderUseCase @Inject constructor(
    private val layoutRepository: LayoutRepository
) {
    suspend operator fun invoke(draggedItemId: Long, targetItemId: Long): Boolean =
        layoutRepository.createFolder(draggedItemId, targetItemId)
}
