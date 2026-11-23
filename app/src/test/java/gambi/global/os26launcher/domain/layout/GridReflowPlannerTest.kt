package gambi.global.os26launcher.domain.layout

import gambi.global.os26launcher.domain.layout.GridInsertPlanner.PageSlots
import gambi.global.os26launcher.domain.layout.GridInsertPlanner.Placement
import gambi.global.os26launcher.domain.layout.GridInsertPlanner.Slot
import gambi.global.os26launcher.domain.layout.GridReflowPlanner.SpanChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridReflowPlannerTest {

    private fun slot(id: Long, x: Int, y: Int, spanX: Int = 1, spanY: Int = 1) = Slot(id, x, y, spanX, spanY)

    private fun page(id: Long, vararg slots: Slot) = PageSlots(id, slots.toList())

    @Test
    fun `gaps are compacted in reading order`() {
        val pages = listOf(page(100, slot(1, 0, 0), slot(2, 2, 0), slot(3, 1, 1)))
        val plan = GridReflowPlanner.plan(pages, columns = 4, rows = 2)
        assertEquals(listOf(Placement(2, 0, 1, 0), Placement(3, 0, 2, 0)), plan.placements)
        assertEquals(1, plan.totalPages)
        assertTrue(plan.spanChanges.isEmpty())
    }

    @Test
    fun `shrinking the grid spills icons onto a new page`() {
        val pages = listOf(PageSlots(100, (0 until 8).map { slot((it + 1).toLong(), it % 4, it / 4) }))
        val plan = GridReflowPlanner.plan(pages, columns = 2, rows = 2)
        assertEquals(
            listOf(
                Placement(3, 0, 0, 1),
                Placement(4, 0, 1, 1),
                Placement(5, 1, 0, 0),
                Placement(6, 1, 1, 0),
                Placement(7, 1, 0, 1),
                Placement(8, 1, 1, 1)
            ),
            plan.placements
        )
        assertEquals(2, plan.totalPages)
    }

    @Test
    fun `ties on the same cell are ordered by item id`() {
        val pages = listOf(page(100, slot(7, 0, 0), slot(3, 0, 0)))
        val plan = GridReflowPlanner.plan(pages, columns = 4, rows = 2)
        assertEquals(listOf(Placement(7, 0, 1, 0)), plan.placements)
    }

    @Test
    fun `positions outside the grid keep their reading order`() {
        val pages = listOf(page(100, slot(1, 5, 0), slot(2, 0, 1)))
        val plan = GridReflowPlanner.plan(pages, columns = 4, rows = 2)
        assertEquals(listOf(Placement(1, 0, 0, 0), Placement(2, 0, 1, 0)), plan.placements)
    }

    @Test
    fun `widget takes the first fitting spot and later icons fill gaps beside it`() {
        val pages = listOf(page(100, slot(1, 0, 0), slot(50, 2, 0, 2, 2), slot(2, 0, 1)))
        val plan = GridReflowPlanner.plan(pages, columns = 4, rows = 2)
        assertEquals(listOf(Placement(50, 0, 1, 0), Placement(2, 0, 3, 0)), plan.placements)
        assertEquals(1, plan.totalPages)
    }

    @Test
    fun `icons after a widget never move back to an earlier page`() {
        // Page 0 keeps a free cell at (3,1), but icon 8 stays on page 1 after the widget that did not fit.
        val page0 = PageSlots(100, (0 until 7).map { slot((it + 1).toLong(), it % 4, it / 4) })
        val page1 = page(101, slot(50, 0, 0, 2, 1), slot(8, 2, 0))
        val plan = GridReflowPlanner.plan(listOf(page0, page1), columns = 4, rows = 2)
        assertTrue(plan.placements.isEmpty())
        assertEquals(2, plan.totalPages)
    }

    @Test
    fun `span larger than the grid is clamped and reported`() {
        val pages = listOf(page(100, slot(50, 0, 0, 5, 1)))
        val plan = GridReflowPlanner.plan(pages, columns = 4, rows = 2)
        assertEquals(listOf(SpanChange(50, 4, 1)), plan.spanChanges)
        assertTrue(plan.placements.isEmpty())
    }

    @Test
    fun `no pages still reports one total page`() {
        // characterization: an empty input yields totalPages = 1, not 0.
        val plan = GridReflowPlanner.plan(emptyList(), columns = 4, rows = 2)
        assertTrue(plan.placements.isEmpty())
        assertEquals(1, plan.totalPages)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non positive grid throws`() {
        GridReflowPlanner.plan(emptyList(), columns = 0, rows = 2)
    }
}
