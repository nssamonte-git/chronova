package noel.chronova.core.ui

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** [TimeFormat] in a fixed locale and zone. */
class TimeFormatTest {
    private val original = Locale.getDefault()
    private val utc: ZoneId = ZoneOffset.UTC
    private val manila: ZoneId = ZoneId.of("Asia/Manila")

    /** Tuesday 3 March 2026, 09:05 UTC. */
    private val tuesdayMorning = millis(2026, 3, 3, 9, 5)

    @Before fun english() = Locale.setDefault(Locale.ENGLISH)

    @After fun restore() = Locale.setDefault(original)

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId = utc) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun clockIsTwentyFourHourInTheGivenZone() {
        assertEquals("09:05", TimeFormat.clock(tuesdayMorning, utc))
        assertEquals("17:05", TimeFormat.clock(tuesdayMorning, manila))
        assertEquals("00:00", TimeFormat.clock(millis(2026, 3, 3, 0, 0), utc))
    }

    @Test
    fun clockKeepsWesternDigitsInEveryLocale() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        assertEquals("09:05", TimeFormat.clock(tuesdayMorning, utc))
    }

    @Test
    fun dayLabels() {
        assertEquals("Tue 3 Mar", TimeFormat.day(tuesdayMorning, utc))
        assertEquals("Tue 3", TimeFormat.shortDay(tuesdayMorning, utc))
        assertEquals("3 Mar", TimeFormat.dayMonth(tuesdayMorning, utc))
        assertEquals("Mar 2026", TimeFormat.month(tuesdayMorning, utc))
    }

    @Test
    fun dayFollowsTheZone() {
        // 20:00 UTC on the 3rd is already the 4th in Manila (UTC+8).
        val evening = millis(2026, 3, 3, 20, 0)
        assertEquals("Tue 3 Mar", TimeFormat.day(evening, utc))
        assertEquals("Wed 4 Mar", TimeFormat.day(evening, manila))
    }

    @Test
    fun rangeJoinsTwoClocks() {
        assertEquals("09:05 – 10:30", TimeFormat.range(tuesdayMorning, millis(2026, 3, 3, 10, 30), utc))
    }

    @Test
    fun aWholeDayTaskShowsItsDaysOthersTheirTimes() {
        val manila = ZoneId.of("Asia/Manila")
        // Midnight to midnight: the days it covers, the last one inclusive.
        assertEquals("13 Nov – 19 Nov 2026", TimeFormat.span(millis(2026, 11, 13, 0, 0, manila), millis(2026, 11, 20, 0, 0, manila), manila))
        assertEquals("13 Nov 2026", TimeFormat.span(millis(2026, 11, 13, 0, 0, manila), millis(2026, 11, 14, 0, 0, manila), manila))
        // Anything else keeps its clock times, even when it starts at midnight.
        assertEquals("00:00 – 09:00", TimeFormat.span(millis(2026, 11, 13, 0, 0, manila), millis(2026, 11, 13, 9, 0, manila), manila))
        assertEquals("09:00 – 17:00", TimeFormat.span(millis(2026, 11, 13, 9, 0, manila), millis(2026, 11, 13, 17, 0, manila), manila))
        // Whole days in another zone are times here.
        assertEquals("16:00 – 16:00", TimeFormat.span(millis(2026, 11, 13, 0, 0, manila), millis(2026, 11, 14, 0, 0, manila), ZoneId.of("UTC")))
    }

    @Test
    fun dateRangeShowsTheYearOnceWhenShared() {
        assertEquals("2 Mar 2026", TimeFormat.dateRange(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2)))
        assertEquals("2 Mar – 4 Mar 2026", TimeFormat.dateRange(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 4)))
        assertEquals("28 Feb – 2 Mar 2026", TimeFormat.dateRange(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 2)))
    }

    @Test
    fun dateRangeAcrossYearsShowsBoth() {
        assertEquals("30 Dec 2025 – 2 Jan 2026", TimeFormat.dateRange(LocalDate.of(2025, 12, 30), LocalDate.of(2026, 1, 2)))
    }

    @Test
    fun namesFollowTheLocale() {
        Locale.setDefault(Locale.FRENCH)
        assertEquals("mars 2026", TimeFormat.month(tuesdayMorning, utc))
    }
}
