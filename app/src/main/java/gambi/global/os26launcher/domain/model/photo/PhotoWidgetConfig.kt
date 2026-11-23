package gambi.global.os26launcher.domain.model.photo

/**
 * G3b — vùng cắt CHUẨN HÓA theo ảnh nguồn đã lưu (đã xoay đúng chiều): mỗi cạnh trong 0..1, `left < right`, `top < bottom`.
 * Chuẩn hóa vì tỉ lệ widget thật lệch tỉ lệ danh nghĩa và đổi theo cỡ lưới (plan D2).
 */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    /** Hợp lệ: số hữu hạn, nằm trong 0..1 và có diện tích dương. */
    val isValid: Boolean
        get() = left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
                left >= 0f && top >= 0f && right <= 1f && bottom <= 1f && right > left && bottom > top

    companion object {
        /**
         * Vùng giữa lớn nhất của ảnh [srcW] x [srcH] có tỉ lệ [aspect] (rộng / cao), dạng chuẩn hóa. Vùng mặc định khi người dùng chưa chỉnh.
         * [aspect] không dương hoặc ảnh không có cỡ thì trả cả ảnh.
         */
        fun centered(srcW: Int, srcH: Int, aspect: Float): CropRect {
            if (srcW <= 0 || srcH <= 0 || !(aspect > 0f)) return FULL
            val srcAspect = srcW.toFloat() / srcH
            return if (srcAspect > aspect) {
                val w = aspect / srcAspect
                CropRect((1f - w) / 2f, 0f, (1f + w) / 2f, 1f)
            } else {
                val h = srcAspect / aspect
                CropRect(0f, (1f - h) / 2f, 1f, (1f + h) / 2f)
            }
        }

        val FULL = CropRect(0f, 0f, 1f, 1f)
    }
}

/**
 * Một ảnh trong widget. [id] là chuỗi hex ngẫu nhiên (không phải tên file gốc). [aspect] = rộng / cao của khung lúc cắt. [version] tăng mỗi lần
 * vùng cắt đổi, để khóa cache bitmap bỏ bản cũ.
 */
data class PhotoRef(val id: String, val crop: CropRect, val aspect: Float, val version: Int)

/** Danh sách ảnh của một widget, theo thứ tự thêm (cũng là thứ tự đổi ảnh theo giờ). */
data class PhotoWidgetConfig(val photos: List<PhotoRef> = emptyList()) {
    companion object {
        /** CHỈNH TAY (khuyến nghị plan G3b, câu hỏi 2): số ảnh tối đa mỗi widget. */
        const val MAX_PHOTOS = 10
    }
}

/** Kết quả nhập một ảnh vào vùng nháp (`staging`) của widget. */
sealed interface PhotoImportResult {
    /** [width] x [height]: cỡ ảnh đã lưu (đã xoay đúng chiều), dùng để dựng vùng cắt mặc định. */
    data class Staged(val photoId: String, val width: Int, val height: Int) : PhotoImportResult

    /** Không đọc được / không giải mã được / hết bộ nhớ / lỗi ghi. */
    data object Failed : PhotoImportResult

    /** Widget đã đủ [PhotoWidgetConfig.MAX_PHOTOS] ảnh. */
    data object Full : PhotoImportResult
}
