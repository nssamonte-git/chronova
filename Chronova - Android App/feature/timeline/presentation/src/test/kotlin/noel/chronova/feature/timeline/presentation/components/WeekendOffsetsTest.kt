package noel.chronova.feature.timeline.presentation.components

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** The grid's weekend shade: which stretches of the window are Saturdays and Sundays. */
class WeekendOffsetsTest {
    private val day = 24 * 60 * 60 * 1000L

    private fun millis(date: LocalDate, zone: ZoneId) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun aWeekFromMondayHasItsSaturdayAndSunday() {
        // Monday 2 March 2026 to the next Monday.
        val start = millis(LocalDate.of(2026, 3, 2), ZoneOffset.UTC)
        val spans = weekendOffsets(start, start + 7 * day, ZoneOffset.UTC)
        assertEquals(listOf(5 * day..6 * day, 6 * day..7 * day), spans)
    }

    @Test
    fun weekdaysOnlyHaveNoShade() {
        val start = millis(LocalDate.of(2026, 3, 2), ZoneOffset.UTC)
        assertEquals(emptyList<LongRange>(), weekendOffsets(start, start + 5 * day, ZoneOffset.UTC))
    }

    @Test
    fun aWindowStartingMidSaturdayIsShadedFromItsStart() {
        // Noon on Saturday 7 March 2026 for one day: half of Saturday, then half of Sunday.
        val start = millis(LocalDate.of(2026, 3, 7), ZoneOffset.UTC) + day / 2
        val spans = weekendOffsets(start, start + day, ZoneOffset.UTC)
        assertEquals(listOf(0L..day / 2, day / 2..day), spans)
    }

    @Test
    fun aDaylightSavingSundayIsAnHourShort() {
        // Europe/Paris springs forward on Sunday 29 March 2026: that Sunday lasts 23 hours.
        val paris = ZoneId.of("Europe/Paris")
        val start = millis(LocalDate.of(2026, 3, 28), paris)
        val end = millis(LocalDate.of(2026, 3, 30), paris)
        val spans = weekendOffsets(start, end, paris)
        assertEquals(listOf(0L..day, day..(2 * day - 60 * 60 * 1000L)), spans)
    }
}
