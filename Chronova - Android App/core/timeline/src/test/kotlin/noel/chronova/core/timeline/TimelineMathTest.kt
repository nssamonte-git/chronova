package noel.chronova.core.timeline

import noel.chronova.core.timeline.Fixtures.at
import noel.chronova.core.timeline.Fixtures.origin
import noel.chronova.core.model.TimeScale
import noel.chronova.core.model.TimelineWindow
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

    private val utc = java.time.ZoneOffset.UTC

    private fun window(from: java.time.LocalDate, days: Long, zone: java.time.ZoneId = utc) = TimelineWindow(
        from.atStartOfDay(zone).toInstant().toEpochMilli(),
        from.plusDays(days).atStartOfDay(zone).toInstant().toEpochMilli(),
    )

    @Test
    fun ticks_hoursHaveHalfHourMinors() {
        val ticks = TimelineMath.ticks(TimelineWindow(0L, 2 * HOUR), utc, TimeScale.HOURS)
        assertEquals(listOf(0L, 30 * MINUTE, 60 * MINUTE, 90 * MINUTE, 120 * MINUTE), ticks.map { it.offsetMillis })
        assertEquals(listOf(true, false, true, false, true), ticks.map { it.isMajor })
        assertEquals(listOf(0, -1, 1, -1, 2), ticks.map { it.majorIndex })
    }

    @Test
    fun ticks_daysHaveSixHourMinors() {
        val ticks = TimelineMath.ticks(window(java.time.LocalDate.of(2026, 3, 2), 3), utc, TimeScale.DAYS)
        assertEquals(13, ticks.size)
        assertEquals(4, ticks.count { it.isMajor })
        assertTrue(ticks.filter { it.isMajor }.all { it.offsetMillis % (24 * HOUR) == 0L })
    }

    @Test
    fun ticks_daysFollowLocalMidnightsAcrossDst() {
        // Europe/Berlin springs forward on Sun 29 Mar 2026: that day is 23 hours long.
        val zone = java.time.ZoneId.of("Europe/Berlin")
        val ticks = TimelineMath.ticks(window(java.time.LocalDate.of(2026, 3, 28), 3, zone), zone, TimeScale.DAYS)
        val majors = ticks.filter { it.isMajor }.map { it.offsetMillis }
        assertEquals(listOf(0L, 24 * HOUR, 47 * HOUR, 71 * HOUR), majors)
    }

    @Test
    fun ticks_weeksAreMajorOnMondays() {
        // 2 Mar 2026 is a Monday; 14 days -> majors on 2 and 9 and 16 Mar (the window end).
        val ticks = TimelineMath.ticks(window(java.time.LocalDate.of(2026, 3, 2), 14), utc, TimeScale.WEEKS)
        assertEquals(15, ticks.size)
        assertEquals(listOf(0L, 7 * 24 * HOUR, 14 * 24 * HOUR), ticks.filter { it.isMajor }.map { it.offsetMillis })
    }

    @Test
    fun ticks_monthsUseRealMonthLengths() {
        // Feb 2026 has 28 days, March 31.
        val ticks = TimelineMath.ticks(window(java.time.LocalDate.of(2026, 2, 1), 59), utc, TimeScale.MONTHS)
        assertEquals(listOf(0L, 28 * 24 * HOUR, 59 * 24 * HOUR), ticks.filter { it.isMajor }.map { it.offsetMillis })
        // Minors are Mondays: 2, 9, 16, 23 Feb; 2, 9, 16, 23, 30 Mar.
        assertEquals(9, ticks.count { !it.isMajor })
    }
}
