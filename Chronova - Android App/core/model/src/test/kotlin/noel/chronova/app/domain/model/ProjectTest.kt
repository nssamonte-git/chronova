package noel.chronova.app.domain.model

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectTest {

    private fun project(start: LocalDate, end: LocalDate) = Project("p", "Launch", start, end)

    @Test
    fun windowSpansWholeDaysInclusive() {
        val window = project(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 4)).window(ZoneOffset.UTC)
        assertEquals(LocalDate.of(2026, 3, 2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), window.startMillis)
        assertEquals(3 * 24 * 60 * 60_000L, window.lengthMillis)
    }

    @Test
    fun singleDayProjectIsOneDayLong() {
        val day = LocalDate.of(2026, 3, 2)
        assertEquals(24 * 60 * 60_000L, project(day, day).window(ZoneOffset.UTC).lengthMillis)
    }

    @Test
    fun windowFollowsLocalMidnightsAcrossDst() {
        // Europe/Berlin springs forward on 29 Mar 2026, so that day has 23 hours.
        val zone = ZoneId.of("Europe/Berlin")
        val day = LocalDate.of(2026, 3, 29)
        assertEquals(23 * 60 * 60_000L, project(day, day).window(zone).lengthMillis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun endBeforeStartIsRejected() {
        project(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankNameIsRejected() {
        Project("p", "  ", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2))
    }
}
