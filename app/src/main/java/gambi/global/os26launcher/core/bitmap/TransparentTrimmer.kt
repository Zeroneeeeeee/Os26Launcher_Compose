package gambi.global.os26launcher.core.bitmap

import android.graphics.Bitmap

/** FX1: cắt viền trong suốt (tách nguyên từ `PickedImageSource`, không đổi hành vi). */
object TransparentTrimmer {

    // Pixel có alpha (0–255) lớn hơn giá trị này được coi là nội dung khi cắt viền trong suốt
    private const val ALPHA_THRESHOLD = 16

    /**
     * Cắt bỏ viền trong suốt quanh ảnh (quyết định 5a trong docs/LiquidGlass-Demo.md), để phần còn lại khi phóng
     * centerCrop phủ kín màn hình mà không lộ thứ nằm sau. Một hàng hay cột được coi là có nội dung nếu chứa ít
     * nhất 1% số pixel có alpha trên ngưỡng, để vài pixel lẻ (ví dụ watermark) không chặn việc cắt.
     *
     * Trả về chính [bmp] nếu ảnh không có alpha, không có viền nào để cắt, hoặc gần như trong suốt hoàn toàn.
     * Chỉ cắt viền ngoài; vùng trong suốt nằm bên trong phần nội dung vẫn giữ nguyên.
     */
    fun trim(bmp: Bitmap): Bitmap {
        if (!bmp.hasAlpha()) return bmp
        val w = bmp.width
        val h = bmp.height
        val row = IntArray(w)
        val rowCount = IntArray(h)
        val colCount = IntArray(w)
        // Đọc từng hàng để không phải cấp phát mảng w*h
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if ((row[x] ushr 24) > ALPHA_THRESHOLD) {
                    rowCount[y]++
                    colCount[x]++
                }
            }
        }
        val rowMin = maxOf(1, w / 100)
        val colMin = maxOf(1, h / 100)
        val top = rowCount.indexOfFirst { it >= rowMin }
        val bottom = rowCount.indexOfLast { it >= rowMin }
        val left = colCount.indexOfFirst { it >= colMin }
        val right = colCount.indexOfLast { it >= colMin }
        if (top < 0 || left < 0) return bmp
        val newW = right - left + 1
        val newH = bottom - top + 1
        if (newW == w && newH == h) return bmp
        val out = Bitmap.createBitmap(bmp, left, top, newW, newH)
        bmp.recycle()
        return out
    }
}
