package gambi.global.os26launcher.core.ui

import kotlin.math.min

/**
 * GL Task 10 (D11) — hệ số co của trang App Library theo kích thước cửa sổ (split-screen, gập màn hình). Hàm thuần, không
 * import `android.*`.
 *
 * `s = clamp(min(1, rộng/REF_W, cao/REF_H), FLOOR, 1)`: [REF_WIDTH_DP] x [REF_HEIGHT_DP] là cỡ cửa sổ dọc đầy đủ mà các
 * `dimens` `category_*` hiện có được thiết kế, nên cửa sổ đủ lớn thì `s = 1` và mọi kích thước **bằng đúng** giá trị cũ.
 * `s` chỉ phụ thuộc cửa sổ (không phụ thuộc số nhóm) nên cỡ ô không nhảy khi cài/gỡ app; danh sách vẫn cuộn.
 *
 * Các hằng ở đây là hằng thuật toán, miễn trừ khỏi `dimens.xml` giống `GridMetrics` (project_structure.md mục 7). Điểm
 * xuất phát, suy luận chưa đo, chỉnh theo mắt.
 */
object LibraryLayoutMetrics {
    const val REF_WIDTH_DP = 360f
    const val REF_HEIGHT_DP = 640f
    const val FLOOR = 0.6f

    // Sàn từng đại lượng (dp / sp).
    const val MIN_TILE_DP = 72
    const val MIN_TILE_GAP_DP = 4
    const val MIN_TILE_PADDING_DP = 6
    const val MIN_MINI_GAP_DP = 2
    const val MIN_RADIUS_DP = 12
    const val MIN_BAR_HEIGHT_DP = 36
    const val MIN_BAR_TOP_DP = 4
    const val MIN_LABEL_SP = 9f
    const val MIN_BAR_TEXT_SP = 13f

    /** [widthDp]/[heightDp]: cửa sổ khả dụng của Library (chiều cao đã trừ padding hệ thống). */
    fun scale(widthDp: Float, heightDp: Float): Float =
        min(1f, min(widthDp / REF_WIDTH_DP, heightDp / REF_HEIGHT_DP)).coerceIn(FLOOR, 1f)

    /** [basePx] nhân [s], không nhỏ hơn [floorPx]; `s >= 1` trả đúng [basePx] (không đổi điểm ảnh). */
    fun scaled(basePx: Int, s: Float, floorPx: Int): Int =
        if (s >= 1f) basePx else maxOf(floorPx, (basePx * s).toInt()).coerceAtMost(basePx)

    fun scaled(base: Float, s: Float, floor: Float): Float =
        if (s >= 1f) base else maxOf(floor, base * s).coerceAtMost(base)
}
