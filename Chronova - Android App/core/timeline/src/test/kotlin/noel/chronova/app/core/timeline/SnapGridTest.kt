package noel.chronova.app.core.timeline

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapGridTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    private fun millis(at: LocalDateTime, zone: ZoneId) = at.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun subDayIntervalsUseAFixedGrid() {
        assertTrue(SnapGrid.of(60, 0L, ZoneOffset.UTC) is FixedGrid)
        assertTrue(SnapGrid.of(6 * 60, 0L, ZoneOffset.UTC) is FixedGrid)
        assertTrue(SnapGrid.of(24 * 60, 0L, ZoneOffset.UTC) is DayGrid)
    }

    @Test
    fun dayGridSnapsToLocalMidnightsAcrossDst() {
        // Window starts Sat 28 Mar 2026; Berlin's 29 Mar is 23 hours long.
        val origin = millis(LocalDate.of(2026, 3, 28).atStartOfDay(), berlin)
        val grid = SnapGrid.of(24 * 60, origin, berlin)
        val mar31 = millis(LocalDate.of(2026, 3, 31).atStartOfDay(), berlin)
        // 30 Mar 13:00 is nearer 31 Mar 00:00 than 30 Mar 00:00.
        assertEquals(mar31, grid.nearest(millis(LocalDateTime.of(2026, 3, 30, 13, 0), berlin)))
        // A fixed 24 h grid would have put this line at 31 Mar 01:00 local.
        assertEquals(mar31, grid.floor(millis(LocalDateTime.of(2026, 3, 31, 0, 30), berlin)))
        assertEquals(mar31, grid.ceil(millis(LocalDateTime.of(2026, 3, 30, 0, 1), berlin)))
    }

    @Test
    fun dayGridTiesRoundLaterAndLinesAreFixedPoints() {
        val grid = SnapGrid.of(24 * 60, 0L, ZoneOffset.UTC)
        val day = 24 * HOUR
        assertEquals(day, grid.nearest(12 * HOUR))
        assertEquals(0L, grid.nearest(12 * HOUR - 1))
        assertEquals(day, grid.floor(day))
        assertEquals(day, grid.ceil(day))
    }

    @Test
    fun weekGridCountsFromTheOriginDate() {
        // Weekly lines on 2, 9, 16 Mar 2026 (origin Monday 2 Mar).
        val origin = millis(LocalDate.of(2026, 3, 2).atStartOfDay(), ZoneOffset.UTC)
        val grid = SnapGrid.of(7 * 24 * 60, origin, ZoneOffset.UTC)
        val mar9 = millis(LocalDate.of(2026, 3, 9).atStartOfDay(), ZoneOffset.UTC)
        assertEquals(mar9, grid.nearest(millis(LocalDateTime.of(2026, 3, 7, 0, 0), ZoneOffset.UTC)))
        assertEquals(origin, grid.nearest(millis(LocalDateTime.of(2026, 3, 5, 11, 0), ZoneOffset.UTC)))
    }

    @Test
    fun moveWithDayGridKeepsDurationAndLandsOnMidnight() {
        val window = noel.chronova.app.domain.model.TimelineWindow(0L, 30 * 24 * HOUR)
        val grid = SnapGrid.of(24 * 60, 0L, ZoneOffset.UTC)
        val task = noel.chronova.app.domain.model.SchedulerTask("t", "p", "T", 2 * 24 * HOUR, 4 * 24 * HOUR)
        val moved = TaskEdits.move(task, 30 * HOUR, grid, window) // +1.25 days -> +1 day
        assertEquals(3 * 24 * HOUR, moved.startMillis)
        assertEquals(task.durationMillis, moved.durationMillis)
    }
}
