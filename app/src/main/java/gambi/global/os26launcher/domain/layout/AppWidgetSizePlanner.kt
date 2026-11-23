package gambi.global.os26launcher.domain.layout

import gambi.global.os26launcher.domain.model.AppWidgetProviderEntry
import gambi.global.os26launcher.domain.model.WidgetSize
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * G3c Task 6 — các cỡ (tính theo ô) mà người dùng chọn được khi thêm một widget app (chủ dự án chốt D1: chọn cỡ lúc thêm, không có tay kéo đổi cỡ trên Home).
 * Hàm thuần, không phụ thuộc Android. Công thức chiều rộng n ô = `n * cellW + (n - 1) * gutter` (dp), cùng `GridMetrics`.
 *
 * Quy tắc (đọc `AppWidgetProviderInfo`):
 * - Cỡ mặc định: `targetCellWidth/Height` nếu cả hai > 0 (API 31+), ngược lại số ô nhỏ nhất đủ `minWidth/minHeight`.
 * - Trục cho đổi cỡ (`resizeHorizontal`/`resizeVertical`): biên dưới theo `minResize*` (0 = 1 ô), biên trên theo `maxResize*` (0 = trần). Trục không đổi cỡ được
 *   chỉ có cỡ mặc định.
 * - Mọi cỡ kẹp trong trần [AppWidgetLimits]. Mặc định vượt trần trên trục đổi cỡ được thì bị kẹp xuống trần (nếu còn >= biên dưới); trên trục không đổi cỡ được,
 *   hoặc biên dưới đã vượt trần: không có cỡ hợp lệ -> danh sách rỗng ("Quá lớn cho lưới").
 * - Rút gọn còn tối đa [MAX_CHIPS]: mặc định (luôn đứng đầu), nhỏ nhất, lớn nhất, rồi các cỡ cùng cạnh với mặc định theo diện tích tăng dần.
 */
object AppWidgetSizePlanner {

    const val MAX_CHIPS = 6

    /** Cỡ ô lưới hiện hành, dp. */
    data class Grid(val cellWidthDp: Float, val cellHeightDp: Float, val gutterXDp: Float, val gutterYDp: Float)

    fun plan(
        entry: AppWidgetProviderEntry,
        grid: Grid,
        maxSpanX: Int = AppWidgetLimits.MAX_SPAN_X,
        maxSpanY: Int = AppWidgetLimits.MAX_SPAN_Y
    ): List<WidgetSize> {
        val x = axis(
            entry.minWidthDp, entry.minResizeWidthDp, entry.maxResizeWidthDp, entry.targetCellWidth,
            entry.resizeHorizontal, grid.cellWidthDp, grid.gutterXDp, maxSpanX
        ) ?: return emptyList()
        val y = axis(
            entry.minHeightDp, entry.minResizeHeightDp, entry.maxResizeHeightDp, entry.targetCellHeight,
            entry.resizeVertical, grid.cellHeightDp, grid.gutterYDp, maxSpanY
        ) ?: return emptyList()

        val default = WidgetSize(x.def, y.def)
        val smallest = WidgetSize(x.low, y.low)
        val largest = WidgetSize(x.high, y.high)
        val sameSide = ArrayList<WidgetSize>()
        for (sx in x.low..x.high) {
            for (sy in y.low..y.high) {
                if (sx == x.def || sy == y.def) sameSide.add(WidgetSize(sx, sy))
            }
        }
        sameSide.sortWith(compareBy({ it.spanX * it.spanY }, { it.spanX }))
        return (listOf(default, smallest, largest) + sameSide).distinct().take(MAX_CHIPS)
    }

    private class Axis(val low: Int, val def: Int, val high: Int)

    /** Một trục (ngang hoặc dọc); null = không có cỡ hợp lệ. */
    private fun axis(
        minDp: Int,
        minResizeDp: Int,
        maxResizeDp: Int,
        target: Int,
        resizable: Boolean,
        cellDp: Float,
        gutterDp: Float,
        maxSpan: Int
    ): Axis? {
        if (cellDp <= 0f) return null
        val rawDefault = if (target > 0) target else cellsFor(minDp, cellDp, gutterDp)
        if (!resizable) {
            return if (rawDefault in 1..maxSpan) Axis(rawDefault, rawDefault, rawDefault) else null
        }
        val low = if (minResizeDp > 0) cellsFor(minResizeDp, cellDp, gutterDp) else 1
        val capped = if (maxResizeDp > 0) min(maxSpan, max(1, floor((maxResizeDp + gutterDp) / (cellDp + gutterDp)).toInt())) else maxSpan
        val lowClamped = min(low, rawDefault)
        if (lowClamped > maxSpan) return null
        val high = max(capped, lowClamped)
        val def = rawDefault.coerceIn(lowClamped, high)
        return Axis(lowClamped, def, high)
    }

    /** Số ô nhỏ nhất đủ chứa [dp] (dp <= 0 -> 1 ô). */
    private fun cellsFor(dp: Int, cellDp: Float, gutterDp: Float): Int =
        max(1, ceil((dp + gutterDp) / (cellDp + gutterDp)).toInt())

    /** Chiều rộng (hoặc cao) dp của [span] ô: dùng làm `OPTION_APPWIDGET_*` lúc gắn. */
    fun spanDp(span: Int, cellDp: Float, gutterDp: Float): Int =
        (span * cellDp + (span - 1) * gutterDp).toInt()
}
