package gambi.global.os26launcher.domain.layout

import gambi.global.os26launcher.domain.layout.GridInsertPlanner.Slot
import gambi.global.os26launcher.domain.layout.GridRemovePlanner.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridRemovePlannerTest {

    private fun slot(id: Long, x: Int, y: Int, spanX: Int = 1, spanY: Int = 1) = Slot(id, x, y, spanX, spanY)

    @Test
    fun `items after the removed one shift back exactly one step`() {
        // Item 4 shifts one step (to (3,0)), so the gap before it is preserved, not compacted.
        val slots = listOf(slot(1, 0, 0), slot(2, 1, 0), slot(3, 2, 0), slot(4, 0, 1))
        val moves = GridRemovePlanner.plan(slots, 2, columns = 4, rows = 2)
        assertEquals(listOf(Move(3, 1, 0), Move(4, 3, 0)), moves)
    }

    @Test
    fun `items before the removed one stay put`() {
        val slots = listOf(slot(1, 0, 0), slot(2, 2, 0), slot(3, 3, 0))
        val moves = GridRemovePlanner.plan(slots, 2, columns = 4, rows = 2)
        assertEquals(listOf(Move(3, 2, 0)), moves)
    }

    @Test
    fun `removing the last item moves nothing`() {
        val slots = listOf(slot(1, 0, 0), slot(2, 1, 0))
        assertTrue(GridRemovePlanner.plan(slots, 2, columns = 4, rows = 2).isEmpty())
    }

    @Test
    fun `unknown item yields no moves`() {
        val slots = listOf(slot(1, 0, 0), slot(2, 1, 0))
        assertTrue(GridRemovePlanner.plan(slots, 99, columns = 4, rows = 2).isEmpty())
    }

    @Test
    fun `removing a widget moves nothing`() {
        val slots = listOf(slot(50, 0, 0, 2, 1), slot(1, 2, 0), slot(2, 3, 0))
        assertTrue(GridRemovePlanner.plan(slots, 50, columns = 4, rows = 2).isEmpty())
    }

    @Test
    fun `icons step over cells covered by a widget and widgets never move`() {
        val slots = listOf(slot(1, 0, 0), slot(50, 1, 0, 2, 1), slot(2, 3, 0), slot(3, 0, 1))
        val moves = GridRemovePlanner.plan(slots, 1, columns = 4, rows = 2)
        assertEquals(listOf(Move(2, 0, 0), Move(3, 3, 0)), moves)
    }

    @Test
    fun `icon below the last row is not shifted`() {
        val slots = listOf(slot(1, 0, 0), slot(2, 0, 2))
        assertTrue(GridRemovePlanner.plan(slots, 1, columns = 4, rows = 2).isEmpty())
    }

    @Test
    fun `icon past the last column is shifted by its reading index`() {
        // characterization: x >= columns is not skipped; (5,0) reads as index 5 = (1,1) and moves to (0,1).
        val slots = listOf(slot(1, 0, 0), slot(2, 5, 0))
        val moves = GridRemovePlanner.plan(slots, 1, columns = 4, rows = 2)
        assertEquals(listOf(Move(2, 0, 1)), moves)
    }
}
