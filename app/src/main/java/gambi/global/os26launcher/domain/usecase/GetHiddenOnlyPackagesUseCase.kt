package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.repository.LayoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * P3-7 — các gói (theo hồ sơ người dùng) chỉ còn app ĐÃ ẨN: mọi activity khởi động của gói đều bị ẩn. Dùng cho nơi liệt kê theo GÓI chứ
 * không theo activity (bảng chọn widget): gói còn ít nhất một activity hiện thì vẫn được liệt kê.
 */
class GetHiddenOnlyPackagesUseCase @Inject constructor(
    private val layoutRepository: LayoutRepository,
    private val observeVisibleApps: ObserveVisibleAppsUseCase
) {
    /** P3-7: bản theo dõi liên tục (widget đã đặt trên Home của app ẩn: ẩn tạm khi app đang ẩn). */
    fun observe(): Flow<Set<Pair<String, Long>>> =
        combine(layoutRepository.hiddenApps, observeVisibleApps()) { hidden, visible ->
            if (hidden.isEmpty()) {
                emptySet()
            } else {
                val shown = visible.mapTo(HashSet()) { it.key.packageName to it.key.userSerial }
                hidden.mapTo(HashSet()) { it.key.packageName to it.key.userSerial } - shown
            }
        }.distinctUntilChanged()

    /** Cặp (packageName, userSerial). */
    suspend operator fun invoke(): Set<Pair<String, Long>> {
        val hidden = layoutRepository.hiddenApps.first()
        if (hidden.isEmpty()) return emptySet()
        val visible = observeVisibleApps().first().mapTo(HashSet()) { it.key.packageName to it.key.userSerial }
        return hidden.mapTo(HashSet()) { it.key.packageName to it.key.userSerial } - visible
    }
}
