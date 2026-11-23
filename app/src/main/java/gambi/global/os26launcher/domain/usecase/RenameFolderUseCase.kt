package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.layout.FolderPlanner
import gambi.global.os26launcher.domain.repository.LayoutRepository
import javax.inject.Inject

/** G2a A2 — đổi tên thư mục. Chuẩn hóa tên ở đây (bỏ khoảng trắng hai đầu, tối đa 30 ký tự); rỗng = bỏ tên. */
class RenameFolderUseCase @Inject constructor(
    private val layoutRepository: LayoutRepository
) {
    suspend operator fun invoke(folderId: Long, title: String) =
        layoutRepository.renameFolder(folderId, FolderPlanner.normalizeTitle(title))
}
