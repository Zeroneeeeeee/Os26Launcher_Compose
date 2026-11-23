package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.repository.LayoutRepository
import javax.inject.Inject

/**
 * EP — dọn trang trống trên màn hình chính. Chỉ chuyển tiếp xuống [LayoutRepository]; luật chọn trang nằm ở
 * [gambi.global.os26launcher.domain.layout.EmptyPagePrunePlanner].
 */
class PruneEmptyPagesUseCase @Inject constructor(
    private val layoutRepository: LayoutRepository
) {
    suspend operator fun invoke() = layoutRepository.pruneEmptyPages()
}
