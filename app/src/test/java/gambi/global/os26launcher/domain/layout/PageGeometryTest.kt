package gambi.global.os26launcher.domain.layout

import gambi.global.os26launcher.domain.layout.GridInsertPlanner.Slot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageGeometryTest {

    @Test
    fun `slot with any span above one is a widget`() {
        assertFalse(PageGeometry.isWidget(Slot(1, 0, 0)))
        assertTrue(PageGeometry.isWidget(Slot(1, 0, 0, spanX = 2)))
        assertTrue(PageGeometry.isWidget(Slot(1, 0, 0, spanY = 2)))
    }

    @Test
    fun `free indices cover every cell without widgets`() {
        val free = PageGeometry.freeIndices(listOf(Slot(1, 0, 0), Slot(2, 3, 1)), 4, 2)
        assertArrayEquals((0 until 8).toList().toIntArray(), free)
    }

    @Test
    fun `free indices skip cells covered by widgets`() {
        val free = PageGeometry.freeIndices(listOf(Slot(50, 1, 0, 2, 2)), 4, 2)
        assertArrayEquals(intArrayOf(0, 3, 4, 7), free)
    }

    @Test
    fun `widget parts outside the grid are clipped`() {
        val free = PageGeometry.freeIndices(listOf(Slot(50, 3, 1, 2, 2)), 4, 2)
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4, 5, 6), free)
    }

    @Test
    fun `seqOf finds position or returns minus one`() {
        val free = intArrayOf(0, 3, 4, 7)
        assertEquals(2, PageGeometry.seqOf(free, 4))
        assertEquals(-1, PageGeometry.seqOf(free, 1))
        assertEquals(-1, PageGeometry.seqOf(free, 9))
    }

    @Test
    fun `lowerBound returns first position at or after the index`() {
        val free = intArrayOf(0, 3, 4, 7)
        assertEquals(1, PageGeometry.lowerBound(free, 3))
        assertEquals(1, PageGeometry.lowerBound(free, 1))
        assertEquals(4, PageGeometry.lowerBound(free, 8))
    }

    @Test
    fun `resolveInsertSeq maps covered targets to the next free cell or the last one`() {
        val free = intArrayOf(0, 3, 4, 7)
        assertEquals(1, PageGeometry.resolveInsertSeq(free, 3))
        assertEquals(1, PageGeometry.resolveInsertSeq(free, 2))
        assertEquals(3, PageGeometry.resolveInsertSeq(free, 20))
        assertEquals(-1, PageGeometry.resolveInsertSeq(IntArray(0), 0))
    }
}
