package gambi.global.os26launcher.domain.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DockInsertPlannerTest {

    @Test
    fun `insert into empty dock`() {
        assertEquals(listOf(9L), DockInsertPlanner.plan(emptyList(), 9, 0, maxSlots = 4))
    }

    @Test
    fun `insert in the middle`() {
        assertEquals(listOf(1L, 9L, 2L, 3L), DockInsertPlanner.plan(listOf(1, 2, 3), 9, 1, maxSlots = 4))
    }

    @Test
    fun `slot is clamped to the dock bounds`() {
        assertEquals(listOf(9L, 1L, 2L), DockInsertPlanner.plan(listOf(1, 2), 9, -3, maxSlots = 4))
        assertEquals(listOf(1L, 2L, 9L), DockInsertPlanner.plan(listOf(1, 2), 9, 10, maxSlots = 4))
    }

    @Test
    fun `full dock rejects a new item`() {
        assertNull(DockInsertPlanner.plan(listOf(1, 2, 3, 4), 9, 0, maxSlots = 4))
    }

    @Test
    fun `reordering inside a full dock is allowed`() {
        assertEquals(listOf(4L, 1L, 2L, 3L), DockInsertPlanner.plan(listOf(1, 2, 3, 4), 4, 0, maxSlots = 4))
    }

    @Test
    fun `slot index refers to the dock without the moving item`() {
        // Moving 1 to slot 2: the rest is [2, 3], so 1 lands at the end.
        assertEquals(listOf(2L, 3L, 1L), DockInsertPlanner.plan(listOf(1, 2, 3), 1, 2, maxSlots = 4))
    }
}
