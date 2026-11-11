package noel.chronova.app.core.timeline

import noel.chronova.app.core.timeline.Fixtures.at
import noel.chronova.app.core.timeline.Fixtures.origin
import noel.chronova.app.domain.model.TimeScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineMathTest {

    private val hourPx = TimelineMath.pxPerMinute(unitWidthPx = 96f, scale = TimeScale.HOURS) // 1.6 px/min

    // Scenario 1 — timestamp to pixel
    @Test
    fun timeToX_hoursScale() {
        assertEquals(0f, TimelineMath.timeToX(origin, origin, hourPx), 0.001f)
        assertEquals(864f, TimelineMath.timeToX(at(9), origin, hourPx), 0.001f)
        assertEquals(1296f, TimelineMath.timeToX(at(13, 30), origin, hourPx), 0.001f)
    }

    @Test
    fun timeToX_daysScale() {
        val dayPx = TimelineMath.pxPerMinute(unitWidthPx = 480f, scale = TimeScale.DAYS)
        assertEquals(180f, TimelineMath.timeToX(at(9), origin, dayPx), 0.001f)
        assertEquals(480f, TimelineMath.timeToX(origin + 24 * HOUR, origin, dayPx), 0.001f)
    }

    @Test
    fun timeToX_beforeOriginIsNegative() {
        assertEquals(-96f, TimelineMath.timeToX(origin - HOUR, origin, hourPx), 0.001f)
    }

    @Test
    fun durationToWidth_scalesWithZoom() {
        assertEquals(192f, TimelineMath.durationToWidth(2 * HOUR, hourPx), 0.001f)
        val zoomedIn = TimelineMath.pxPerMinute(192f, TimeScale.HOURS)
        assertEquals(384f, TimelineMath.durationToWidth(2 * HOUR, zoomedIn), 0.001f)
    }

    // Scenario 2 — pixel to time
    @Test
    fun xToTime_inverseOfTimeToX() {
        assertEquals(at(9), TimelineMath.xToTime(864f, origin, hourPx))
        assertEquals(origin - HOUR, TimelineMath.xToTime(-96f, origin, hourPx))
        val t = at(16, 45)
        assertEquals(t, TimelineMath.xToTime(TimelineMath.timeToX(t, origin, hourPx), origin, hourPx))
    }

    @Test
    fun pxToMillis_signedDragDistance() {
        assertEquals(30 * MINUTE, TimelineMath.pxToMillis(48f, hourPx))
        assertEquals(-30 * MINUTE, TimelineMath.pxToMillis(-48f, hourPx))
    }

    // Scenario 8 — zoom/scale changes never touch timestamps; positions are re-derived
    @Test
    fun zoomChange_preservesTimestamps() {
        val t = at(13, 30)
        listOf(
            TimelineMath.pxPerMinute(48f, TimeScale.HOURS),
            TimelineMath.pxPerMinute(240f, TimeScale.HOURS),
            TimelineMath.pxPerMinute(480f, TimeScale.DAYS),
        ).forEach { ppm ->
            val x = TimelineMath.timeToX(t, origin, ppm)
            assertEquals(t, TimelineMath.xToTime(x, origin, ppm))
        }
    }

    @Test
    fun ticks_hoursHaveHalfHourMinors() {
        val ticks = TimelineMath.ticks(lengthMillis = 2 * HOUR, scale = TimeScale.HOURS)
        assertEquals(listOf(0L, 30 * MINUTE, 60 * MINUTE, 90 * MINUTE, 120 * MINUTE), ticks.map { it.offsetMillis })
        assertEquals(listOf(true, false, true, false, true), ticks.map { it.isMajor })
    }

    @Test
    fun ticks_daysHaveSixHourMinors() {
        val ticks = TimelineMath.ticks(lengthMillis = 3 * 24 * HOUR, scale = TimeScale.DAYS)
        assertEquals(13, ticks.size)
        assertEquals(4, ticks.count { it.isMajor })
        assertTrue(ticks.filter { it.isMajor }.all { it.offsetMillis % (24 * HOUR) == 0L })
    }
}
