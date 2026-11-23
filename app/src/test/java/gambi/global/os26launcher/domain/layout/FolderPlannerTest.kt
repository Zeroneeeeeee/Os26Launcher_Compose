package gambi.global.os26launcher.domain.layout

import gambi.global.os26launcher.domain.layout.FolderPlanner.Dissolve
import gambi.global.os26launcher.domain.layout.FolderPlanner.Participant
import gambi.global.os26launcher.domain.model.ContainerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderPlannerTest {

    private val homeApp = Participant(ContainerId.HOME, isApp = true)

    @Test
    fun `title is trimmed and capped`() {
        assertEquals("Games", FolderPlanner.normalizeTitle("  Games  "))
        assertEquals("", FolderPlanner.normalizeTitle("   "))
        assertEquals("a".repeat(FolderPlanner.MAX_TITLE_LENGTH), FolderPlanner.normalizeTitle("a".repeat(40)))
    }

    @Test
    fun `center zone uses the narrow threshold to enter and the wide one to exit`() {
        // iconSize 100: enter half-side 45 px, exit half-side 55 px.
        assertTrue(FolderPlanner.isInCenterZone(44f, -44f, 100, wasInside = false))
        assertFalse(FolderPlanner.isInCenterZone(50f, 0f, 100, wasInside = false))
        assertTrue(FolderPlanner.isInCenterZone(50f, 0f, 100, wasInside = true))
        assertFalse(FolderPlanner.isInCenterZone(0f, 56f, 100, wasInside = true))
    }

    @Test
    fun `create allowed from home dock or another folder onto a home app`() {
        assertTrue(FolderPlanner.canCreate(homeApp, homeApp))
        assertTrue(FolderPlanner.canCreate(Participant(ContainerId.DOCK, true), homeApp))
        assertTrue(FolderPlanner.canCreate(Participant(42L, true), homeApp))
    }

    @Test
    fun `create allowed onto a dock app from dock home or folder`() {
        val dockApp = Participant(ContainerId.DOCK, true)
        assertTrue(FolderPlanner.canCreate(dockApp, dockApp))
        assertTrue(FolderPlanner.canCreate(homeApp, dockApp))
        assertTrue(FolderPlanner.canCreate(Participant(42L, true), dockApp))
    }

    @Test
    fun `create rejected for non app non single cell or folder child target`() {
        assertFalse(FolderPlanner.canCreate(Participant(ContainerId.HOME, isApp = false), homeApp))
        assertFalse(FolderPlanner.canCreate(Participant(ContainerId.HOME, true, spanX = 2), homeApp))
        assertFalse(FolderPlanner.canCreate(homeApp, Participant(42L, true)))
        assertFalse(FolderPlanner.canCreate(homeApp, Participant(ContainerId.HOME, true, spanY = 2)))
    }

    @Test
    fun `container zero is not a valid source`() {
        // characterization: only HOME, DOCK or a positive folder id qualify; 0 is rejected.
        assertFalse(FolderPlanner.canCreate(Participant(0L, true), homeApp))
        assertFalse(FolderPlanner.canAdd(Participant(0L, true), 0))
    }

    @Test
    fun `add allowed until the folder is full`() {
        assertTrue(FolderPlanner.canAdd(homeApp, FolderPlanner.MAX_ITEMS - 1))
        assertFalse(FolderPlanner.canAdd(homeApp, FolderPlanner.MAX_ITEMS))
        assertTrue(FolderPlanner.canAdd(Participant(ContainerId.DOCK, true), 0))
        assertTrue(FolderPlanner.canAdd(Participant(7L, true), 0))
    }

    @Test
    fun `folders and widgets cannot be added`() {
        assertFalse(FolderPlanner.canAdd(Participant(ContainerId.HOME, isApp = false), 0))
        assertFalse(FolderPlanner.canAdd(Participant(ContainerId.HOME, true, spanX = 2, spanY = 2), 0))
    }

    @Test
    fun `dissolve depends on remaining children`() {
        assertEquals(Dissolve.DELETE, FolderPlanner.dissolve(-1))
        assertEquals(Dissolve.DELETE, FolderPlanner.dissolve(0))
        assertEquals(Dissolve.REPLACE_WITH_SINGLE, FolderPlanner.dissolve(1))
        assertEquals(Dissolve.KEEP, FolderPlanner.dissolve(2))
    }

    @Test
    fun `folders hold up to fifteen pages of nine apps`() {
        // FM Q1: 15 pages x 9 = 135 apps.
        assertEquals(9, FolderPlanner.PAGE_SIZE)
        assertEquals(15, FolderPlanner.MAX_PAGES)
        assertEquals(135, FolderPlanner.MAX_ITEMS)
        assertTrue(FolderPlanner.canAdd(homeApp, 134))
        assertFalse(FolderPlanner.canAdd(homeApp, 135))
    }

    @Test
    fun `page count rounds up and stays within one to fifteen`() {
        assertEquals(1, FolderPlanner.pageCount(0))
        assertEquals(1, FolderPlanner.pageCount(9))
        assertEquals(2, FolderPlanner.pageCount(10))
        assertEquals(15, FolderPlanner.pageCount(135))
        assertEquals(15, FolderPlanner.pageCount(200))
    }
}
