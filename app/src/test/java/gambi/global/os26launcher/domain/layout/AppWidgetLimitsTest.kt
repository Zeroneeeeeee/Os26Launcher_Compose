package gambi.global.os26launcher.domain.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppWidgetLimitsTest {

    @Test
    fun `cap is four by four`() {
        assertEquals(4, AppWidgetLimits.MAX_SPAN_X)
        assertEquals(4, AppWidgetLimits.MAX_SPAN_Y)
    }

    @Test
    fun `sizes within the cap are allowed`() {
        assertTrue(AppWidgetLimits.allows(1, 1))
        assertTrue(AppWidgetLimits.allows(4, 4))
        assertTrue(AppWidgetLimits.allows(2, 3))
    }

    @Test
    fun `zero or oversize spans are rejected`() {
        assertFalse(AppWidgetLimits.allows(0, 1))
        assertFalse(AppWidgetLimits.allows(1, 0))
        assertFalse(AppWidgetLimits.allows(5, 1))
        assertFalse(AppWidgetLimits.allows(1, 5))
    }
}
