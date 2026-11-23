package gambi.global.os26launcher.domain.layout

import gambi.global.os26launcher.domain.model.AppWidgetProviderEntry
import gambi.global.os26launcher.domain.model.AppWidgetRef
import gambi.global.os26launcher.domain.model.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppWidgetSizePlannerTest {

    // 80 dp cells, 8 dp gutter: n cells span n*80 + (n-1)*8 dp.
    private val grid = AppWidgetSizePlanner.Grid(80f, 80f, 8f, 8f)

    private fun entry(
        minWidth: Int = 70,
        minHeight: Int = 70,
        minResizeWidth: Int = 0,
        minResizeHeight: Int = 0,
        maxResizeWidth: Int = 0,
        maxResizeHeight: Int = 0,
        resizeH: Boolean = false,
        resizeV: Boolean = false,
        targetW: Int = 0,
        targetH: Int = 0
    ) = AppWidgetProviderEntry(
        AppWidgetRef("pkg", "cls", 0L),
        minWidth, minHeight, minResizeWidth, minResizeHeight, maxResizeWidth, maxResizeHeight,
        resizeH, resizeV, targetW, targetH
    )

    @Test
    fun `non resizable widget offers only its default from min size`() {
        assertEquals(listOf(WidgetSize(2, 1)), AppWidgetSizePlanner.plan(entry(minWidth = 160, minHeight = 70), grid))
    }

    @Test
    fun `target cells override min size`() {
        assertEquals(
            listOf(WidgetSize(3, 2)),
            AppWidgetSizePlanner.plan(entry(minWidth = 10, minHeight = 10, targetW = 3, targetH = 2), grid)
        )
    }

    @Test
    fun `non resizable default above the cap gives no sizes`() {
        assertTrue(AppWidgetSizePlanner.plan(entry(targetW = 5, targetH = 1), grid).isEmpty())
    }

    @Test
    fun `fully resizable widget is reduced to six chips with default first`() {
        val sizes = AppWidgetSizePlanner.plan(entry(minWidth = 160, minHeight = 70, resizeH = true, resizeV = true), grid)
        assertEquals(
            listOf(
                WidgetSize(2, 1),
                WidgetSize(1, 1),
                WidgetSize(4, 4),
                WidgetSize(3, 1),
                WidgetSize(2, 2),
                WidgetSize(4, 1)
            ),
            sizes
        )
    }

    @Test
    fun `max resize limits the upper bound`() {
        val sizes = AppWidgetSizePlanner.plan(entry(resizeH = true, maxResizeWidth = 170), grid)
        assertEquals(listOf(WidgetSize(1, 1), WidgetSize(2, 1)), sizes)
    }

    @Test
    fun `default above the cap is clamped on a resizable axis`() {
        // characterization: target cells are applied per axis, even when the other axis target is 0 (KDoc says "both > 0").
        val sizes = AppWidgetSizePlanner.plan(entry(resizeH = true, targetW = 6, targetH = 0), grid)
        assertEquals(listOf(WidgetSize(4, 1), WidgetSize(1, 1), WidgetSize(2, 1), WidgetSize(3, 1)), sizes)
    }

    @Test
    fun `lower bound above the cap gives no sizes`() {
        val sizes = AppWidgetSizePlanner.plan(entry(minWidth = 500, minResizeWidth = 500, resizeH = true), grid)
        assertTrue(sizes.isEmpty())
    }

    @Test
    fun `custom max span narrows the range`() {
        val sizes = AppWidgetSizePlanner.plan(entry(resizeH = true), grid, maxSpanX = 2)
        assertEquals(listOf(WidgetSize(1, 1), WidgetSize(2, 1)), sizes)
    }

    @Test
    fun `zero cell size gives no sizes`() {
        assertTrue(AppWidgetSizePlanner.plan(entry(), AppWidgetSizePlanner.Grid(0f, 80f, 8f, 8f)).isEmpty())
    }

    @Test
    fun `spanDp includes gutters between cells`() {
        assertEquals(80, AppWidgetSizePlanner.spanDp(1, 80f, 8f))
        assertEquals(256, AppWidgetSizePlanner.spanDp(3, 80f, 8f))
    }
}
