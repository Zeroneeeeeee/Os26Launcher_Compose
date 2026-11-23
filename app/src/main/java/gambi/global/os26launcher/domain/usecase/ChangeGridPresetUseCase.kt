package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.GridPreset
import gambi.global.os26launcher.domain.repository.LayoutRepository
import javax.inject.Inject

/**
 * M8a Task 6 + FX3 (B3) — đổi cỡ lưới. Từ FX3 use case chỉ chuyển tiếp: ghi bố cục (Room) và ghi cài đặt (DataStore) nằm cùng
 * trong một vùng khóa của `LayoutReconciler.changeGridPreset`, nơi cũng ghi chú thứ tự ghi an toàn (thu nhỏ: DB trước; mở
 * rộng: cài đặt trước). Bị ngắt giữa chừng thì `LayoutRepositoryImpl` chạy `ensureFitsGrid()` lúc khởi động để bắt kịp.
 */
class ChangeGridPresetUseCase @Inject constructor(
    private val layoutRepository: LayoutRepository
) {
    suspend operator fun invoke(target: GridPreset) = layoutRepository.changeGridPreset(target)
}
