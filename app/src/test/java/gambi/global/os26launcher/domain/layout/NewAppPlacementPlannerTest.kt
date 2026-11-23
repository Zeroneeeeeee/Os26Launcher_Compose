package gambi.global.os26launcher.domain.layout

import gambi.global.os26launcher.domain.layout.GridInsertPlanner.PageSlots
import gambi.global.os26launcher.domain.layout.GridInsertPlanner.Slot
import gambi.global.os26launcher.domain.layout.NewAppPlacementPlanner.Spot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewAppPlacementPlannerTest {

    private fun slot(id: Long, x: Int, y: Int, spanX: Int = 1, spanY: Int = 1) = Slot(id, x, y, spanX, spanY)

    private fun page(id: Long, vararg slots: Slot) = PageSlots(id, slots.toList())

    @Test
    fun `no pages starts at the first cell of page zero`() {
        val spots = NewAppPlacementPlanner.plan(emptyList(), 2, columns = 4, rows = 2)
        assertEquals(listOf(Spot(0, 0, 0), Spot(0, 1, 0)), spots)
    }

    @Test
    fun `new apps go after the last icon without filling earlier gaps`() {
        val pages = listOf(page(100, slot(1, 0, 0), slot(2, 3, 0)))
        val spots = NewAppPlacementPlanner.plan(pages, 2, columns = 4, rows = 2)
        assertEquals(listOf(Spot(0, 0, 1), Spot(0, 1, 1)), spots)
    }

    @Test
    fun `batch overflows onto new pages`() {
        val pages = listOf(page(100, slot(1, 2, 1)))
        val spots = NewAppPlacementPlanner.plan(pages, 3, columns = 4, rows = 2)
        assertEquals(listOf(Spot(0, 3, 1), Spot(1, 0, 0), Spot(1, 1, 0)), spots)
    }

    @Test
    fun `last cell taken creates a new page even with gaps mid page`() {
        val pages = listOf(page(100, slot(1, 0, 0), slot(2, 3, 1)))
        val spots = NewAppPlacementPlanner.plan(pages, 1, columns = 4, rows = 2)
        assertEquals(listOf(Spot(1, 0, 0)), spots)
    }

    @Test
    fun `cells covered by a widget are skipped`() {
        val pages = listOf(page(100, slot(50, 0, 1, 2, 1), slot(1, 3, 0)))
        val spots = NewAppPlacementPlanner.plan(pages, 2, columns = 4, rows = 2)
        assertEquals(listOf(Spot(0, 2, 1), Spot(0, 3, 1)), spots)
    }

    @Test
    fun `widget alone does not count as the last icon`() {
        val pages = listOf(page(100, slot(50, 0, 0, 2, 1)))
        val spots = NewAppPlacementPlanner.plan(pages, 1, columns = 4, rows = 2)
        assertEquals(listOf(Spot(0, 2, 0)), spots)
    }

    @Test
    fun `only the last page is considered`() {
        val pages = listOf(page(100), page(101, slot(1, 0, 0)))
        val spots = NewAppPlacementPlanner.plan(pages, 1, columns = 4, rows = 2)
        assertEquals(listOf(Spot(1, 1, 0)), spots)
    }

    @Test
    fun `icons outside the grid are ignored`() {
        val pages = listOf(page(100, slot(1, 1, 0), slot(2, 0, 5)))
        val spots = NewAppPlacementPlanner.plan(pages, 1, columns = 4, rows = 2)
        assertEquals(listOf(Spot(0, 2, 0)), spots)
    }

    @Test
    fun `zero count returns no spots`() {
        assertTrue(NewAppPlacementPlanner.plan(emptyList(), 0, columns = 4, rows = 2).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid grid throws`() {
        NewAppPlacementPlanner.plan(emptyList(), 1, columns = 0, rows = 2)
    }
}
