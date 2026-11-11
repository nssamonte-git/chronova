package noel.chronova.app.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportMathTest {

    @Test
    fun maxScroll_neverNegative() {
        assertEquals(600f, ViewportMath.maxScroll(contentWidth = 1000f, viewportWidth = 400f))
        assertEquals(0f, ViewportMath.maxScroll(contentWidth = 300f, viewportWidth = 400f))
    }

    @Test
    fun rescale_keepsCentreTime() {
        // Centre at x = 300 + 200 = 500 px = 250 min at 2 px/min.
        val scroll = ViewportMath.rescale(scrollX = 300f, viewportWidth = 400f, oldPxPerMinute = 2f, newPxPerMinute = 2.5f)
        assertEquals(250f, (scroll + 200f) / 2.5f, 0.001f)
        assertEquals(425f, scroll, 0.001f)
    }

    @Test
    fun composeSpan_alwaysContainsTheVisibleSpan() {
        val viewport = 720f
        listOf(0f, 1f, 719f, 720f, 5_000f, 1_234_567f).forEach { scroll ->
            val span = ViewportMath.composeSpan(scroll, viewport)
            assertTrue("start $scroll", span.start <= scroll - viewport + 1f)
            assertTrue("end $scroll", span.end >= scroll + 2 * viewport - 1f)
        }
    }

    @Test
    fun composeSpan_isStableWithinAPage() {
        assertEquals(ViewportMath.composeSpan(720f, 720f), ViewportMath.composeSpan(1439f, 720f))
    }

    @Test
    fun visibleTicks_findsTicksInsideTheRange() {
        val ticks = TimelineMath.ticks(lengthMillis = 4 * HOUR, scale = noel.chronova.app.domain.model.TimeScale.HOURS)
        // Ticks every 30 min: 0, 30, 60, ... 240. Range 45..120 min holds 60, 90, 120.
        val range = TimelineMath.visibleTicks(ticks, 45 * MINUTE, 120 * MINUTE)
        assertEquals(listOf(60 * MINUTE, 90 * MINUTE, 120 * MINUTE), range.map { ticks[it].offsetMillis })
        assertTrue(TimelineMath.visibleTicks(ticks, 300 * MINUTE, 400 * MINUTE).isEmpty())
    }
}
