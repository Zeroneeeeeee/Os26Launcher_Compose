package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.AppWidgetRef
import gambi.global.os26launcher.domain.repository.LayoutRepository
import javax.inject.Inject

/**
 * G3c — ghi hàng cho một widget app đã cấp id và gắn xong ([appWidgetId]): hình chữ nhật trống đầu tiên bắt đầu từ trang [preferredPageId], không đẩy icon.
 * Trả id trang đã đặt, null nếu cỡ vượt trần (`AppWidgetLimits`) hoặc lưới; null thì NGƯỜI GỌI phải giải phóng [appWidgetId].
 */
class AddAppWidgetUseCase @Inject constructor(
    private val layoutRepository: LayoutRepository
) {
    suspend operator fun invoke(
        provider: AppWidgetRef,
        appWidgetId: Int,
        spanX: Int,
        spanY: Int,
        preferredPageId: Long?
    ): Long? = layoutRepository.addAppWidget(provider, appWidgetId, spanX, spanY, preferredPageId)
}
