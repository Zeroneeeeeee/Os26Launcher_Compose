package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.repository.RecentAppsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import javax.inject.Inject

/**
 * TV-2 — app mở gần đây cho thẻ "app gần đây" của Today View: lịch sử mở app của launcher ([RecentAppsRepository]), mới nhất trước,
 * GIAO với [ObserveVisibleAppsUseCase] nên app đã gỡ hay đã ẩn không bao giờ hiện (quy tắc P3-7). Tối đa [limit] app.
 *
 * Không đọc công tắc "Ứng dụng gần đây" của Tìm kiếm: công tắc đó chỉ cho lớp tìm kiếm (plan TV mục 3).
 */
class ObserveRecentAppsUseCase @Inject constructor(
    private val recentApps: RecentAppsRepository,
    private val observeVisibleApps: ObserveVisibleAppsUseCase
) {
    operator fun invoke(limit: Int): Flow<List<AppItem>> =
        combine(
            observeVisibleApps().filter { it.isNotEmpty() }, // bỏ danh sách rỗng phát trước giá trị thật
            recentApps.entries.catch { emit(emptyList()) }
        ) { apps, recents ->
            val byKey = apps.associateBy { it.key }
            recents.asSequence()
                .sortedByDescending { it.lastLaunchedMillis }
                .mapNotNull { byKey[it.key] }
                .take(limit)
                .toList()
        }.distinctUntilChanged()
}
