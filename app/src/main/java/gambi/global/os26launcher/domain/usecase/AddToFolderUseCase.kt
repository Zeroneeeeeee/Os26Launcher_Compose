package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.repository.LayoutRepository
import javax.inject.Inject

/** G2a A3a — thả app [itemId] vào thư mục [folderId]. `false` = bị từ chối (đầy / không hợp lệ), không ghi gì. */
class AddToFolderUseCase @Inject constructor(
    private val layoutRepository: LayoutRepository
) {
    suspend operator fun invoke(itemId: Long, folderId: Long): Boolean =
        layoutRepository.addToFolder(itemId, folderId)
}
