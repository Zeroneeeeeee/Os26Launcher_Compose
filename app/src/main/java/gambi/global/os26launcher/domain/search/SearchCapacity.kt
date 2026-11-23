package gambi.global.os26launcher.domain.search

import kotlin.math.floor
import kotlin.math.max

object SearchCapacity {

    class CapacityResult(
        val appRows: Int,
        val webRows: Int,
        val maxAppCount: Int
    )

    /**
     * Ô trống: tiêu đề "Gần đây" + [recentRows] hàng app (2, hoặc 1 khi người dùng thu gọn), rồi tiêu đề "Tìm kiếm thịnh hành" + số
     * hàng thịnh hành vừa khít phía trên ô nhập (tối đa [MAX_TRENDING_ROWS]; webRows = 0 thì không hiện mục này). Tham số như
     * [calculateTypingCapacity].
     */
    fun calculateEmptyCapacity(
        listHeightPx: Int,
        topPaddingPx: Int,
        headerHeightPx: Int,
        cellHeightPx: Int,
        webRowHeightPx: Int,
        columns: Int,
        recentRows: Int = EMPTY_RECENT_ROWS
    ): CapacityResult {
        val rows = recentRows
        if (listHeightPx <= 0) return CapacityResult(rows, 0, rows * columns)
        val remaining = listHeightPx - topPaddingPx - headerHeightPx - rows * cellHeightPx - headerHeightPx
        val trendingRows = floor(remaining.toFloat() / webRowHeightPx).toInt().coerceIn(0, MAX_TRENDING_ROWS)
        return CapacityResult(rows, trendingRows, rows * columns)
    }

    /** Ô trống: "Gần đây" mở rộng luôn 2 hàng app (thu gọn: 1 hàng). */
    const val EMPTY_RECENT_ROWS = 2

    /** Ô trống: tối đa chừng này hàng tìm kiếm thịnh hành. */
    private const val MAX_TRENDING_ROWS = 5

    /**
     * [listHeightPx]: chiều cao vùng danh sách, từ đáy ô nhập tới mép trên bàn phím (hoặc thanh điều hướng).
     * [topPaddingPx]: đệm trên của danh sách; [sectionGapPx]: khoảng hở giữa thẻ app và thẻ web.
     */
    fun calculateTypingCapacity(
        listHeightPx: Int,
        topPaddingPx: Int,
        cellHeightPx: Int,
        webRowHeightPx: Int,
        sectionGapPx: Int,
        columns: Int
    ): CapacityResult {
        val appRows = TYPING_APP_ROWS
        if (listHeightPx <= 0) return CapacityResult(appRows, 3, columns * appRows)

        // Khi search (kiểu iOS Spotlight): 1 hàng app (tối đa `columns` = 4 app), không tiêu đề nhóm; phần còn lại cho gợi ý web.
        // Danh sách không cuộn: chỉ lấy số hàng web vừa khít phía trên bàn phím.
        val remainingForWeb = listHeightPx - topPaddingPx - (appRows * cellHeightPx) - sectionGapPx
        val webRows = max(1, floor(remainingForWeb.toFloat() / webRowHeightPx).toInt())

        return CapacityResult(appRows, webRows, appRows * columns)
    }

    /** Lúc gõ: chỉ 1 hàng app khớp nhất (4 cột = tối đa 4 app). */
    private const val TYPING_APP_ROWS = 1
}
