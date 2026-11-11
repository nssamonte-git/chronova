package noel.chronova.app.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Test

// Scenario 3 — positive and negative snapping
class TimeSnapperTest {

    private val q = 15 * MINUTE

    @Test
    fun snapsPositiveValuesToNearest() {
        assertEquals(0L, TimeSnapper.snap(7 * MINUTE, q))
        assertEquals(q, TimeSnapper.snap(8 * MINUTE, q))
        assertEquals(q, TimeSnapper.snap(22 * MINUTE, q))
        assertEquals(2 * q, TimeSnapper.snap(23 * MINUTE, q))
    }

    @Test
    fun snapsNegativeValuesSymmetrically() {
        assertEquals(0L, TimeSnapper.snap(-7 * MINUTE, q))
        assertEquals(-q, TimeSnapper.snap(-8 * MINUTE, q))
        assertEquals(-q, TimeSnapper.snap(-22 * MINUTE, q))
        assertEquals(-2 * q, TimeSnapper.snap(-23 * MINUTE, q))
    }

    @Test
    fun tiesRoundTowardPositiveInfinity() {
        val half = q / 2 // 7.5 min
        assertEquals(q, TimeSnapper.snap(half, q))
        assertEquals(0L, TimeSnapper.snap(-half, q))
    }

    @Test
    fun snapsRelativeToOrigin() {
        val origin = 1_000_000L
        assertEquals(origin + 30 * MINUTE, TimeSnapper.snap(origin + 25 * MINUTE, 30 * MINUTE, origin))
        assertEquals(origin - 60 * MINUTE, TimeSnapper.snap(origin - 40 * MINUTE, 60 * MINUTE, origin))
    }

    @Test
    fun alreadySnappedValuesAreUnchanged() {
        listOf(15L, 30L, 60L).forEach { interval ->
            val i = interval * MINUTE
            assertEquals(4 * i, TimeSnapper.snap(4 * i, i))
            assertEquals(-4 * i, TimeSnapper.snap(-4 * i, i))
        }
    }
}
