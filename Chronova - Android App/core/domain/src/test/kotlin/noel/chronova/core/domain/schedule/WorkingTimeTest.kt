package noel.chronova.core.domain.schedule

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 09:00 to 17:00, Monday to Friday. November 2026: the 16th is a Monday. */
class WorkingTimeTest {
    private val zone: ZoneId = ZoneId.of("Asia/Manila")
    private val hour = 3_600_000L
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
    private val ruler = WorkingTime(zone).ruler(at(16, 0), at(27, 23))
    private fun worked(from: Long, to: Long) = ruler.workedBefore(to) - ruler.workedBefore(from)

    @Test
    fun aWorkingDayIsEightHours() {
        assertEquals(8 * hour, worked(at(16, 9), at(16, 17)))
        assertEquals(8 * hour, worked(at(16, 0), at(17, 0))) // a whole day holds one working day
        assertEquals(3 * hour, worked(at(26, 10), at(26, 13)))
        assertEquals(90 * 60_000L, worked(at(16, 8), at(16, 10, 30))) // only from 09:00
    }

    @Test
    fun nightsAndWeekendsHoldNoWorkingTime() {
        assertEquals(0L, worked(at(17, 17), at(18, 9))) // Tuesday evening to Wednesday morning
        assertEquals(0L, worked(at(20, 17), at(23, 9))) // Friday evening to Monday morning
        assertEquals(0L, worked(at(21, 9), at(22, 17))) // a weekend
        assertEquals(4 * hour, worked(at(17, 15), at(18, 11))) // two hours either side of a night
        assertEquals(80 * hour, worked(at(16, 0), at(27, 23))) // two working weeks
    }

    @Test
    fun workingHoursIncludeTheirFirstAndLastMoments() {
        assertTrue(ruler.isWorking(at(16, 9)))
        assertTrue(ruler.isWorking(at(16, 12)))
        assertTrue(ruler.isWorking(at(16, 17)))
        assertFalse(ruler.isWorking(at(16, 8, 59)))
        assertFalse(ruler.isWorking(at(16, 17, 1)))
        assertFalse(ruler.isWorking(at(21, 12))) // Saturday
    }

    @Test
    fun theHoursFollowTheZone() {
        // 09:00 in Manila is 01:00 UTC: nothing of Manila's working day is left by 09:00 UTC.
        val utc = WorkingTime(ZoneId.of("UTC")).ruler(at(16, 0), at(17, 0))
        assertEquals(0L, utc.workedBefore(at(16, 17))) // 09:00 UTC
        assertEquals(1 * hour, utc.workedBefore(at(16, 18)))
    }
}
