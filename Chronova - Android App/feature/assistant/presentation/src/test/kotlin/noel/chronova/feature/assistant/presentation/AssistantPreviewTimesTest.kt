package noel.chronova.feature.assistant.presentation

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** When a moved task's line shows two clock ranges and the day once ("16:00 – 17:00 → 15:00 – 16:00 · Thu 12 Nov"). */
class AssistantPreviewTimesTest {
    private val original = Locale.getDefault()
    private val zone: ZoneId = ZoneId.of("Asia/Manila")

    @Before fun english() = Locale.setDefault(Locale.ENGLISH)

    @After fun restore() = Locale.setDefault(original)

    private fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun timedTasksAndMilestonesOnTheDayShowTheirTimes() {
        assertTrue(timesWithinDay(at(26, 10), at(26, 13), "Thu 26 Nov", zone))
        assertTrue(timesWithinDay(at(26, 21), at(27, 0), "Thu 26 Nov", zone)) // ends at midnight
        assertTrue(timesWithinDay(at(26, 9), at(26, 9), "Thu 26 Nov", zone)) // a milestone
        assertFalse(timesWithinDay(at(26, 10), at(27, 13), "Thu 26 Nov", zone))
        assertFalse(timesWithinDay(at(25, 10), at(25, 13), "Thu 26 Nov", zone))
    }

    /** A whole day read "00:00 – 00:00 → 10:00 – 13:00 · Thu 26 Nov": no length at all, to the eye. */
    @Test
    fun aWholeDayHasNoTimesToShow() {
        assertFalse(timesWithinDay(at(26, 0), at(27, 0), "Thu 26 Nov", zone))
    }
}
