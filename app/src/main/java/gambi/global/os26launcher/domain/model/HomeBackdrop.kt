package gambi.global.os26launcher.domain.model

import android.graphics.Bitmap

/**
 * HW — ảnh nền của màn Home theo lựa chọn hiện tại (kết quả của `BackdropRepository.loadHomeBackdrop`).
 *
 * `Bitmap` là ngoại lệ có chủ ý với luật "domain không import android.*" (như `BackdropRepository`).
 */
sealed interface HomeBackdrop {
    /** Có ảnh để vẽ. [isDefault] = ảnh mặc định / nền màu / ảnh cài sẵn (không phải ảnh người dùng tự chọn). */
    class Ready(val bitmap: Bitmap, val isDefault: Boolean) : HomeBackdrop

    /** Chưa chọn ảnh và không có ảnh mặc định: nền đen. */
    data object Empty : HomeBackdrop

    /** Có ảnh đã chọn nhưng giải mã không được (file hỏng, hết bộ nhớ): nền đen, không sập. */
    data object Error : HomeBackdrop
}
