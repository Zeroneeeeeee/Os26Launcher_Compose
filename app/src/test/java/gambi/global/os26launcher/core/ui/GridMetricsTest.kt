package gambi.global.os26launcher.core.ui

import gambi.global.os26launcher.domain.model.GridPosition
import gambi.global.os26launcher.domain.model.GridPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridMetricsTest {

    private fun cells4x6(showLabels: Boolean = true): GridMetrics.Cells =
        GridMetrics(4, 6, showLabels, LEGACY).measure(400, 800, 1f)!!

    @Test
    fun `measure at density one with labels`() {
        // Raw cell 90 x 124 px; icon = 90 * 0.9 rounded down to a multiple of 4.
        val expected = GridMetrics.Cells(
            cellWidth = 88, cellHeight = 124, iconSize = 80,
            gutterX = 8, gutterY = 8, paddingLeft = 8, paddingTop = 8,
            columns = 4, rows = 6, showLabels = true, labelScale = 1f, innerGapPx = 4, labelTextSp = 10f
        )
        assertEquals(expected, cells4x6())
    }

    @Test
    fun `measure without labels drops the inner gap`() {
        val cells = cells4x6(showLabels = false)
        assertFalse(cells.showLabels)
        assertEquals(80, cells.iconSize)
        assertEquals(0, cells.innerGapPx)
        assertEquals(1f, cells.labelScale, 0f)
    }

    @Test
    fun `measure scales with density`() {
        val cells = GridMetrics(4, 6, sizing = LEGACY).measure(800, 1600, 2f)!!
        assertEquals(176, cells.cellWidth)
        assertEquals(248, cells.cellHeight)
        assertEquals(160, cells.iconSize)
        assertEquals(16, cells.gutterX)
        assertEquals(16, cells.paddingLeft)
        assertEquals(8, cells.innerGapPx)
    }

    @Test
    fun `measure returns null for unlaid views bad grids or too narrow cells`() {
        assertNull(GridMetrics(4, 6).measure(0, 800, 1f))
        assertNull(GridMetrics(4, 6).measure(400, 0, 1f))
        assertNull(GridMetrics(0, 6).measure(400, 800, 1f))
        assertNull(GridMetrics(4, 0).measure(400, 800, 1f))
        // (200 - 16 - 24) / 4 = 40 px < 48 dp minimum.
        assertNull(GridMetrics(4, 6).measure(200, 800, 1f))
    }

    @Test
    fun `short cells shrink the label`() {
        // Raw cell height 72: full-label icon 40 < 44, scale 40/44 >= 0.8, icon recomputed with the shrunken label.
        val cells = GridMetrics(4, 1, sizing = LEGACY).measure(400, 88, 1f)!!
        assertTrue(cells.showLabels)
        assertEquals(40f / 44f, cells.labelScale, 1e-6f)
        assertEquals(3, cells.innerGapPx)
        assertEquals(44, cells.iconSize)
        assertEquals(72, cells.cellHeight)
    }

    @Test
    fun `very short cells hide the label and give its space to the icon`() {
        val cells = GridMetrics(4, 1, sizing = LEGACY).measure(400, 70, 1f)!!
        assertFalse(cells.showLabels)
        assertEquals(48, cells.iconSize)
        assertEquals(0, cells.innerGapPx)
        assertEquals(1f, cells.labelScale, 0f)
    }

    @Test
    fun `icon never drops below the safety floor`() {
        val cells = GridMetrics(4, 1, sizing = LEGACY).measure(400, 20, 1f)!!
        assertEquals(8, cells.iconSize)
        assertEquals(4, cells.cellHeight)
        assertFalse(cells.showLabels)
    }

    @Test
    fun `cellAt maps pixels to cells with gutters owned by the preceding cell`() {
        val cells = cells4x6()
        assertEquals(GridPosition(0, 0), cells.cellAt(8f, 8f))
        assertEquals(GridPosition(0, 0), cells.cellAt(100f, 8f)) // inside the gutter after column 0
        assertEquals(GridPosition(1, 0), cells.cellAt(104f, 8f))
        assertEquals(GridPosition(3, 5), cells.cellAt(391f, 799f))
    }

    @Test
    fun `cellAt returns null in padding or past the last cell`() {
        val cells = cells4x6()
        assertNull(cells.cellAt(7f, 8f))
        assertNull(cells.cellAt(8f, 7f))
        assertNull(cells.cellAt(392f, 8f))
        assertNull(cells.cellAt(8f, 800f))
    }

    @Test
    fun `cellAt returns null for zero pitch`() {
        val cells = GridMetrics.Cells(0, 0, 0, 0, 0, 0, 0, 4, 6, true)
        assertNull(cells.cellAt(0f, 0f))
    }

    @Test
    fun `from preset copies columns rows and label flag`() {
        // Only the home pager grid caps icons to the dock height (FS 2026-11-20).
        assertEquals(GridMetrics(5, 6, false, capToDock = true), GridMetrics.from(GridPreset.P5X6, showLabels = false))
        assertEquals(GridMetrics(4, 5, true, capToDock = true), GridMetrics.from(GridPreset.P4X5))
    }

    @Test
    fun `default sizing uses the larger icon scale and the smaller label`() {
        // Polish 2026-11-19: icon = 0.96 x cell width. 2026-11-21: label 11.5sp (iOS ~12 pt).
        val cells = GridMetrics(4, 6).measure(400, 800, 1f)!!
        assertEquals(84, cells.iconSize) // 90 * 0.96 = 86 -> rounded down to 84
        assertEquals(11.5f, cells.labelTextSp, 0f)
        assertTrue(cells.showLabels)
    }

    @Test
    fun `dock cap applies only when enabled`() {
        // Pager 400 x 200: dock height = 200 / 6 = 33 px, cap = 26 px -> 24 after rounding; labels then too small and hidden.
        val capped = GridMetrics(2, 1, capToDock = true).measure(400, 200, 1f)!!
        assertEquals(24, capped.iconSize)
        assertFalse(capped.showLabels)
        val free = GridMetrics(2, 1).measure(400, 200, 1f)!!
        assertEquals(92, free.iconSize)
        assertTrue(free.showLabels)
    }

    private companion object {
        /** Sizing before the 2026-11-19 polish (icon 0.9 x cell, 10sp label): keeps the algorithm tests independent of the tuned defaults. */
        val LEGACY = GridMetrics.IconSizing(iconScale = 0.9f, labelTextSp = 10f)
    }
}
