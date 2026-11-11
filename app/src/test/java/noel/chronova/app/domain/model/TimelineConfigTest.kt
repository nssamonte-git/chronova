package noel.chronova.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineConfigTest {

    @Test
    fun defaultsToHoursAtDefaultZoom() {
        val config = TimelineConfig()
        assertEquals(TimeScale.HOURS, config.timeScale)
        assertEquals(96f, config.unitWidthDp)
        assertEquals(100, config.zoomPercent)
    }

    @Test
    fun zoomStepsAreClampedToTheScaleLimits() {
        var config = TimelineConfig()
        repeat(20) { config = config.zoomedIn() }
        assertEquals(TimeScale.HOURS.maxUnitWidthDp, config.unitWidthDp)
        assertFalse(config.canZoomIn)

        repeat(20) { config = config.zoomedOut() }
        assertEquals(TimeScale.HOURS.minUnitWidthDp, config.unitWidthDp)
        assertFalse(config.canZoomOut)

        assertEquals(96f, config.zoomReset().unitWidthDp)
    }

    @Test
    fun zoomInThenOutReturnsToSameWidth() {
        val config = TimelineConfig()
        assertEquals(96f, config.zoomedIn().zoomedOut().unitWidthDp, 0.001f)
        assertEquals(125, config.zoomedIn().zoomPercent)
    }

    @Test
    fun switchingScaleUsesThatScalesDefaultZoom() {
        val days = TimelineConfig().zoomedIn().withTimeScale(TimeScale.DAYS)
        assertEquals(TimeScale.DAYS, days.timeScale)
        assertEquals(480f, days.unitWidthDp)
        val zoomed = TimelineConfig().zoomedIn()
        assertSame(zoomed, zoomed.withTimeScale(TimeScale.HOURS))
    }

    @Test
    fun snapOnlyAcceptsSupportedIntervals() {
        assertEquals(30, TimelineConfig().withSnap(30).snapMinutes)
        assertTrue(runCatching { TimelineConfig().withSnap(20) }.isFailure)
    }

    @Test
    fun rowHeightStepsAreClamped() {
        assertEquals(64f, TimelineConfig().withRowHeightStep(1).rowHeightDp)
        assertEquals(80f, TimelineConfig().withRowHeightStep(10).rowHeightDp)
        assertEquals(40f, TimelineConfig().withRowHeightStep(-10).rowHeightDp)
    }
}
