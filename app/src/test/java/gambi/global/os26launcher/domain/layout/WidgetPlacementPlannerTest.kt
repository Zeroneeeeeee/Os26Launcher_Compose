package gambi.global.os26launcher.domain.layout

import gambi.global.os26launcher.domain.layout.GridInsertPlanner.PageSlots
import gambi.global.os26launcher.domain.layout.GridInsertPlanner.Slot
import gambi.global.os26launcher.domain.layout.WidgetPlacementPlanner.FitSpot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetPlacementPlannerTest {

    private fun slot(id: Long, x: Int, y: Int, spanX: Int = 1, spanY: Int = 1) = Slot(id, x, y, spanX, spanY)

    private fun page(id: Long, vararg slots: Slot) = PageSlots(id, slots.toList())

    private val fullPage = PageSlots(100, (0 until 8).map { slot((it + 1).toLong(), it % 4, it / 4) })

    @Test
    fun `empty page fits at origin`() {
        assertEquals(FitSpot(0, 0, 0), WidgetPlacementPlanner.firstFit(listOf(page(1)), 2, 2, 0, 4, 2))
    }

    @Test
    fun `icons are obstacles and never pushed`() {
        val pages = listOf(page(1, slot(1, 1, 0)))
        assertEquals(FitSpot(0, 2, 0), WidgetPlacementPlanner.firstFit(pages, 2, 2, 0, 4, 2))
    }

    @Test
    fun `widget spans are obstacles`() {
        val pages = listOf(page(1, slot(50, 0, 0, 2, 2)))
        assertEquals(FitSpot(0, 2, 0), WidgetPlacementPlanner.firstFit(pages, 2, 1, 0, 4, 2))
    }

    @Test
    fun `scan starts at the start page and wraps around`() {
        val pages = listOf(page(1), fullPage, page(3, slot(1, 0, 0)))
        assertEquals(FitSpot(2, 1, 0), WidgetPlacementPlanner.firstFit(pages, 2, 2, 1, 4, 2))
        assertEquals(FitSpot(0, 0, 0), WidgetPlacementPlanner.firstFit(listOf(page(1), fullPage), 2, 2, 1, 4, 2))
    }

    @Test
    fun `start page index is clamped`() {
        val pages = listOf(page(1), page(2))
        assertEquals(FitSpot(1, 0, 0), WidgetPlacementPlanner.firstFit(pages, 1, 1, 99, 4, 2))
    }

    @Test
    fun `no room returns null`() {
        assertNull(WidgetPlacementPlanner.firstFit(listOf(fullPage), 1, 1, 0, 4, 2))
    }

    @Test
    fun `invalid span or no pages returns null`() {
        assertNull(WidgetPlacementPlanner.firstFit(listOf(page(1)), 5, 1, 0, 4, 2))
        assertNull(WidgetPlacementPlanner.firstFit(listOf(page(1)), 0, 1, 0, 4, 2))
        assertNull(WidgetPlacementPlanner.firstFit(emptyList(), 1, 1, 0, 4, 2))
    }

    @Test
    fun `cells outside the grid are ignored`() {
        val pages = listOf(page(1, slot(1, 4, 0), slot(2, 0, 2)))
        assertEquals(FitSpot(0, 0, 0), WidgetPlacementPlanner.firstFit(pages, 4, 2, 0, 4, 2))
    }
}
