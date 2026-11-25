package gambi.global.os26launcher.domain.layout

import gambi.global.os26launcher.domain.layout.GridInsertPlanner.PageSlots
import gambi.global.os26launcher.domain.layout.GridInsertPlanner.Placement
import gambi.global.os26launcher.domain.layout.GridInsertPlanner.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridInsertPlannerTest {

    private fun slot(id: Long, x: Int, y: Int, spanX: Int = 1, spanY: Int = 1) = Slot(id, x, y, spanX, spanY)

    private fun page(id: Long, vararg slots: Slot) = PageSlots(id, slots.toList())

    /** Page full of ids 1..8 in reading order on a 4x2 grid. */
    private fun fullPage4x2(id: Long = 100L) =
        PageSlots(id, (0 until 8).map { slot((it + 1).toLong(), it % 4, it / 4) })

    @Test
    fun `empty target moves only the dragged item`() {
        val pages = listOf(page(100, slot(1, 0, 0), slot(2, 1, 0)))
        val plan = GridInsertPlanner.plan(pages, 1, 0, 2, 1, columns = 4, rows = 2)
        assertEquals(listOf(Placement(1, 0, 2, 1)), plan.placements)
        assertEquals(0, plan.newPages)
        assertFalse(plan.rejected)
    }

    @Test
    fun `occupied target packs occupant and tail tightly after the target`() {
        // Gap at (3,0) is consumed; item 4 at (0,1) happens to land on its own cell, so it is not reported.
        val pages = listOf(page(100, slot(1, 0, 0), slot(2, 1, 0), slot(3, 2, 0), slot(4, 0, 1)))
        val plan = GridInsertPlanner.plan(pages, 9, 0, 1, 0, columns = 4, rows = 2)
        assertEquals(
            listOf(Placement(9, 0, 1, 0), Placement(2, 0, 2, 0), Placement(3, 0, 3, 0)),
            plan.placements
        )
        assertEquals(0, plan.newPages)
    }

    @Test
    fun `items before the target and their gaps stay untouched`() {
        val pages = listOf(page(100, slot(1, 0, 0), slot(2, 2, 0)))
        val plan = GridInsertPlanner.plan(pages, 9, 0, 2, 0, columns = 4, rows = 2)
        assertEquals(listOf(Placement(9, 0, 2, 0), Placement(2, 0, 3, 0)), plan.placements)
    }

    @Test
    fun `moving within the page onto an occupied cell shifts the tail`() {
        val pages = listOf(page(100, slot(1, 0, 0), slot(2, 1, 0), slot(3, 2, 0)))
        val plan = GridInsertPlanner.plan(pages, 3, 0, 0, 0, columns = 4, rows = 2)
        assertEquals(
            listOf(Placement(3, 0, 0, 0), Placement(1, 0, 1, 0), Placement(2, 0, 2, 0)),
            plan.placements
        )
    }

    @Test
    fun `drop on own cell yields no placements`() {
        val pages = listOf(page(100, slot(1, 1, 0), slot(2, 2, 0)))
        val plan = GridInsertPlanner.plan(pages, 1, 0, 1, 0, columns = 4, rows = 2)
        assertTrue(plan.placements.isEmpty())
        assertEquals(0, plan.newPages)
        assertFalse(plan.rejected)
    }

    @Test
    fun `overflow goes to the first free cell of the next existing page`() {
        val pages = listOf(fullPage4x2(100), page(101, slot(20, 0, 0)))
        val plan = GridInsertPlanner.plan(pages, 9, 0, 0, 0, columns = 4, rows = 2)
        val expected = listOf(Placement(9, 0, 0, 0)) +
                (1..7).map { Placement(it.toLong(), 0, it % 4, it / 4) } +
                listOf(Placement(8, 1, 0, 0), Placement(20, 1, 1, 0))
        assertEquals(expected, plan.placements)
        assertEquals(0, plan.newPages)
    }

    @Test
    fun `overflow past the last page creates a new page`() {
        val plan = GridInsertPlanner.plan(listOf(fullPage4x2()), 9, 0, 3, 1, columns = 4, rows = 2)
        assertEquals(listOf(Placement(9, 0, 3, 1), Placement(8, 1, 0, 0)), plan.placements)
        assertEquals(1, plan.newPages)
    }

    @Test
    fun `target covered by a widget resolves to the next free cell`() {
        val pages = listOf(page(100, slot(50, 0, 0, 2, 2), slot(1, 2, 0)))
        val plan = GridInsertPlanner.plan(pages, 9, 0, 0, 0, columns = 4, rows = 2)
        assertEquals(listOf(Placement(9, 0, 2, 0), Placement(1, 0, 3, 0)), plan.placements)
    }

    @Test
    fun `items outside the grid are ignored and never treated as occupants`() {
        // Item 1 at x=4 has reading index 4, the same as (0,1), but is outside 0 until columns (FX3).
        val pages = listOf(page(100, slot(1, 4, 0), slot(2, 1, 0)))
        val plan = GridInsertPlanner.plan(pages, 9, 0, 0, 1, columns = 4, rows = 2)
        assertEquals(listOf(Placement(9, 0, 0, 1)), plan.placements)
    }

    @Test
    fun `overflow of more than one item moves as a block to a new page`() {
        // Item 10 shares a cell with item 8, so the page holds 9 items on 8 cells (FX3).
        val pages = listOf(PageSlots(100, fullPage4x2().slots + slot(10, 3, 1)))
        val plan = GridInsertPlanner.plan(pages, 9, 0, 2, 1, columns = 4, rows = 2)
        assertEquals(
            listOf(
                Placement(9, 0, 2, 1),
                Placement(7, 0, 3, 1),
                Placement(8, 1, 0, 0),
                Placement(10, 1, 1, 0)
            ),
            plan.placements
        )
        assertEquals(1, plan.newPages)
    }

    @Test
    fun `widget overlapping another widget is rejected`() {
        val pages = listOf(page(100, slot(50, 0, 0, 2, 2)))
        val plan = GridInsertPlanner.plan(pages, 60, 0, 1, 1, columns = 4, rows = 4, movingSpanX = 2, movingSpanY = 2)
        assertTrue(plan.rejected)
        assertTrue(plan.placements.isEmpty())
        assertEquals(0, plan.newPages)
    }

    @Test
    fun `widget larger than the grid is rejected`() {
        val pages = listOf(page(100))
        val plan = GridInsertPlanner.plan(pages, 60, 0, 0, 0, columns = 4, rows = 4, movingSpanX = 5, movingSpanY = 1)
        assertTrue(plan.rejected)
        assertTrue(plan.placements.isEmpty())
    }

    @Test
    fun `widget anchor is clamped into the grid and displaces nothing on free cells`() {
        val pages = listOf(page(100, slot(1, 0, 0)))
        val plan = GridInsertPlanner.plan(pages, 60, 0, 3, 3, columns = 4, rows = 4, movingSpanX = 2, movingSpanY = 2)
        assertEquals(listOf(Placement(60, 0, 2, 2)), plan.placements)
        assertFalse(plan.rejected)
    }

    @Test
    fun `widget covering icons packs the tail into free cells after the anchor`() {
        val pages = listOf(page(100, slot(1, 0, 0), slot(2, 1, 0), slot(3, 2, 0), slot(4, 3, 0)))
        val plan = GridInsertPlanner.plan(pages, 60, 0, 1, 0, columns = 4, rows = 2, movingSpanX = 2, movingSpanY = 1)
        assertEquals(
            listOf(
                Placement(60, 0, 1, 0),
                Placement(2, 0, 3, 0),
                Placement(3, 0, 0, 1),
                Placement(4, 0, 1, 1)
            ),
            plan.placements
        )
        assertEquals(0, plan.newPages)
    }

    // ---- WS: đổi chỗ widget ----

    @Test
    fun `widget dropped onto one widget on the same page swaps places`() {
        // 4x4: W 2x2 ở (0,0), D 2x2 ở (2,2); kéo D lên (0,0): D vào (0,0), W về (2,2).
        val pages = listOf(page(100, slot(50, 0, 0, 2, 2), slot(60, 2, 2, 2, 2)))
        val plan = GridInsertPlanner.plan(pages, 60, 0, 0, 0, columns = 4, rows = 4)
        assertFalse(plan.rejected)
        assertEquals(listOf(Placement(60, 0, 0, 0), Placement(50, 0, 2, 2)), plan.placements)
    }

    @Test
    fun `swap falls back to the other widget anchor when the target would overlap its new spot`() {
        // 4x4: W 4x2 ở (0,0), D 4x2 ở (0,2); kéo D lên 1 hàng (0,1): W ở (0,2) sẽ chồng D ở (0,1) -> D lấy đúng neo của W (0,0).
        val pages = listOf(page(100, slot(50, 0, 0, 4, 2), slot(60, 0, 2, 4, 2)))
        val plan = GridInsertPlanner.plan(pages, 60, 0, 0, 1, columns = 4, rows = 4)
        assertFalse(plan.rejected)
        assertEquals(listOf(Placement(60, 0, 0, 0), Placement(50, 0, 0, 2)), plan.placements)
    }

    @Test
    fun `swap is rejected when the other widget does not fit at the old spot`() {
        // 4x3: W 4x1 ở (0,0), D 2x2 ở (2,1); W (rộng 4) không vừa neo (2,1).
        val pages = listOf(page(100, slot(50, 0, 0, 4, 1), slot(60, 2, 1, 2, 2)))
        val plan = GridInsertPlanner.plan(pages, 60, 0, 0, 0, columns = 4, rows = 3)
        assertTrue(plan.rejected)
        assertTrue(plan.placements.isEmpty())
    }

    @Test
    fun `widget overlapping two widgets is rejected`() {
        val pages = listOf(page(100, slot(50, 0, 0, 2, 2), slot(51, 2, 0, 2, 2), slot(60, 0, 2, 4, 2)))
        val plan = GridInsertPlanner.plan(pages, 60, 0, 0, 0, columns = 4, rows = 4)
        assertTrue(plan.rejected)
    }

    @Test
    fun `widget from another page does not swap`() {
        val pages = listOf(page(100, slot(50, 0, 0, 2, 2)), page(101, slot(60, 0, 0, 2, 2)))
        val plan = GridInsertPlanner.plan(pages, 60, 0, 0, 0, columns = 4, rows = 4)
        assertTrue(plan.rejected)
    }

    @Test
    fun `icons covered by the swapped widget are packed after the earlier anchor`() {
        // 4x5: W 2x2 (0,0), D 2x1 (0,3), icon 1 ở (1,4), icon 2 ở (0,2). Kéo D lên (0,0): D (0,0), W 2x2 về (0,3) phủ icon 1.
        // Đuôi từ neo nhỏ nhất (0): icon 2 rồi icon 1 dồn sát vào ô tự do đầu tiên (2,0), (3,0).
        val pages = listOf(page(100, slot(50, 0, 0, 2, 2), slot(60, 0, 3, 2, 1), slot(1, 1, 4), slot(2, 0, 2)))
        val plan = GridInsertPlanner.plan(pages, 60, 0, 0, 0, columns = 4, rows = 5)
        assertFalse(plan.rejected)
        assertEquals(
            listOf(
                Placement(60, 0, 0, 0),
                Placement(50, 0, 0, 3),
                Placement(2, 0, 2, 0),
                Placement(1, 0, 3, 0)
            ),
            plan.placements
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `target page index out of range throws`() {
        GridInsertPlanner.plan(listOf(page(100)), 1, 1, 0, 0, columns = 4, rows = 2)
    }
}
