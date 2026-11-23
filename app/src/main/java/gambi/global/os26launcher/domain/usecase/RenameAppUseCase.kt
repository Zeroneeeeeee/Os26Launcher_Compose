package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.repository.AppLabelRepository
import javax.inject.Inject

/**
 * RN — đặt tên riêng cho một app. Chuẩn hóa đầu vào bằng [sanitize]; rỗng hoặc bằng tên gốc nghĩa là khôi phục (xóa mục).
 * Trả tên đã lưu, hoặc null nếu đã khôi phục.
 */
class RenameAppUseCase @Inject constructor(
    private val labels: AppLabelRepository
) {
    suspend operator fun invoke(key: ComponentKey, originalLabel: String, input: String): String? {
        val label = sanitize(input)
        return if (label.isEmpty() || label == originalLabel) {
            labels.setCustomLabel(key, null)
            null
        } else {
            labels.setCustomLabel(key, label)
            label
        }
    }

    companion object {
        /** Số đơn vị UTF-16 tối đa của tên riêng (Q6). */
        const val MAX_LENGTH = 30

        private val BLANKS = Regex("[\\s\\p{Cntrl}]+")

        /** Gộp khoảng trắng, xuống dòng và ký tự điều khiển thành một dấu cách, bỏ đầu/cuối, cắt [MAX_LENGTH] không tách cặp surrogate. */
        fun sanitize(input: String): String {
            val s = input.replace(BLANKS, " ").trim()
            var end = minOf(s.length, MAX_LENGTH)
            if (end < s.length && end > 0 && Character.isHighSurrogate(s[end - 1])) end--
            return s.substring(0, end).trimEnd()
        }
    }
}
