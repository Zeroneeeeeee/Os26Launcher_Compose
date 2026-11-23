package gambi.global.os26launcher.domain.layout

import gambi.global.os26launcher.domain.layout.EmptyPagePrunePlanner.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyPagePrunePlannerTest {

    @Test
    fun `nothing pruned when every page is used`() {
        val pages = listOf(Page(1, 0), Page(2, 1))
        assertTrue(EmptyPagePrunePlanner.plan(pages, setOf(1, 2)).isEmpty())
    }

    @Test
    fun `empty pages including middle ones are pruned`() {
        val pages = listOf(Page(1, 0), Page(2, 1), Page(3, 2), Page(4, 3))
        assertEquals(listOf(2L, 4L), EmptyPagePrunePlanner.plan(pages, setOf(1, 3)))
    }

    @Test
    fun `all empty keeps the page with the lowest rank`() {
        val pages = listOf(Page(10, 5), Page(11, 1), Page(12, 3))
        assertEquals(listOf(12L, 10L), EmptyPagePrunePlanner.plan(pages, emptySet()))
    }

    @Test
    fun `result is ordered by rank`() {
        val pages = listOf(Page(3, 2), Page(1, 0), Page(2, 1))
        assertEquals(listOf(2L, 3L), EmptyPagePrunePlanner.plan(pages, setOf(1)))
    }

    @Test
    fun `no pages yields nothing`() {
        assertTrue(EmptyPagePrunePlanner.plan(emptyList(), emptySet()).isEmpty())
    }

    @Test
    fun `used ids not matching any page are ignored`() {
        val pages = listOf(Page(1, 0), Page(2, 1))
        assertEquals(listOf(2L), EmptyPagePrunePlanner.plan(pages, setOf(1, 99)))
    }
}
