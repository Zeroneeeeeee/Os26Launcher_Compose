package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.repository.LayoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * P3-7 — danh sách app ĐƯỢC PHÉP HIỆN: `AppRepository.apps` trừ app đã ẩn.
 *
 * Quy tắc (chủ dự án 2026-11-20): app đã ẩn chỉ xuất hiện ở màn "Ứng dụng đã ẩn" (sau này có khóa vân tay / PIN). Mọi màn khác liệt kê app
 * (Cài đặt, Trung tâm điều khiển, bảng chọn widget, ...) phải đọc danh sách từ đây, KHÔNG đọc thẳng `AppRepository.apps`.
 *
 * Giống `AppRepository.apps`: phát danh sách rỗng trước giá trị thật (người gọi tự bỏ rỗng nếu cần).
 */
class ObserveVisibleAppsUseCase @Inject constructor(
    private val appRepository: AppRepository,
    private val layoutRepository: LayoutRepository
) {
    operator fun invoke(): Flow<List<AppItem>> =
        combine(appRepository.apps, layoutRepository.hiddenApps) { all, hidden ->
            if (hidden.isEmpty()) {
                all
            } else {
                val hiddenKeys = hidden.mapTo(HashSet()) { it.key }
                all.filter { it.key !in hiddenKeys }
            }
        }.distinctUntilChanged()
}
