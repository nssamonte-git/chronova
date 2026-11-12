package noel.chronova.core.model

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
    fun aPinchZoomsByItsFactorWithinTheScaleLimits() {
        val config = TimelineConfig()
        assertEquals(144f, config.zoomedBy(1.5f).unitWidthDp, 0.001f)
        assertEquals(64f, config.zoomedBy(2f / 3f).unitWidthDp, 0.001f)
        assertEquals(TimeScale.HOURS.maxUnitWidthDp, config.zoomedBy(10f).unitWidthDp)
        assertEquals(TimeScale.HOURS.minUnitWidthDp, config.zoomedBy(0.1f).unitWidthDp)
        assertSame(config, config.zoomedBy(Float.NaN))
        assertSame(config, config.zoomedBy(0f))
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
    fun snapOnlyAcceptsTheScalesIntervals() {
        assertEquals(30, TimelineConfig().withSnap(30).snapMinutes)
        assertTrue(runCatching { TimelineConfig().withSnap(20) }.isFailure)
        assertTrue(runCatching { TimelineConfig().withSnap(24 * 60) }.isFailure)
        assertEquals(24 * 60, TimelineConfig().withTimeScale(TimeScale.WEEKS).withSnap(24 * 60).snapMinutes)
    }

    @Test
    fun switchingScaleKeepsSnapOnlyWhenOffered() {
        val hourly = TimelineConfig().withSnap(60)
        assertEquals(60, hourly.withTimeScale(TimeScale.DAYS).snapMinutes)
        assertEquals(24 * 60, hourly.withTimeScale(TimeScale.WEEKS).snapMinutes)
        assertEquals(15, hourly.withTimeScale(TimeScale.MONTHS).withTimeScale(TimeScale.HOURS).snapMinutes)
    }

    @Test
    fun restoreRepairsOutOfRangeValues() {
        val restored = TimelineConfig.restore("WEEKS", unitWidthDp = 5_000f, snapMinutes = 15, rowHeightDp = 10f)
        assertEquals(TimeScale.WEEKS, restored.timeScale)
        assertEquals(TimeScale.WEEKS.maxUnitWidthDp, restored.unitWidthDp)
        assertEquals(24 * 60, restored.snapMinutes)
        assertEquals(TimelineConfig.MIN_ROW_HEIGHT_DP, restored.rowHeightDp)
        assertEquals(TimelineConfig(), TimelineConfig.restore("YEARS", 1f, 1, 1f))
    }

    @Test
    fun rowHeightStepsAreClamped() {
        assertEquals(64f, TimelineConfig().withRowHeightStep(1).rowHeightDp)
        assertEquals(80f, TimelineConfig().withRowHeightStep(10).rowHeightDp)
        assertEquals(40f, TimelineConfig().withRowHeightStep(-10).rowHeightDp)
    }
}
