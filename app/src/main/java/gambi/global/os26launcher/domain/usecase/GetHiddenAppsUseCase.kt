package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.repository.LayoutRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** M7 — danh sách app đang bị ẩn, dùng cho màn Thư viện ứng dụng. */
class GetHiddenAppsUseCase @Inject constructor(
    private val layoutRepository: LayoutRepository
) {
    operator fun invoke(): Flow<List<AppItem>> = layoutRepository.hiddenApps
}
