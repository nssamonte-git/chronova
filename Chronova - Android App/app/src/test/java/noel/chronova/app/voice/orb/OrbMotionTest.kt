package noel.chronova.app.voice.orb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OrbMotionTest {

    private fun range(mode: OrbMode, level: Float = 0f, pick: (OrbPose) -> Float): Pair<Float, Float> {
        val values = (0..2000).map { pick(OrbMotion.pose(mode, it * 0.01f, level)) }
        return values.min() to values.max()
    }

    @Test
    fun theOrbSwaysButNeverTurnsItsHourglassAway() {
        OrbMode.entries.forEach { mode ->
            val (min, max) = range(mode, 1f) { it.yawDegrees }
            assertTrue("$mode yaw $min..$max", min >= -30f && max <= 30f)
            assertTrue("$mode moves", max - min > 10f)
        }
    }

    @Test
    fun listeningFollowsTheVoice() {
        val quiet = OrbMotion.pose(OrbMode.Listening, 1f, level = 0f)
        val loud = OrbMotion.pose(OrbMode.Listening, 1f, level = 1f)
        assertEquals(1f, quiet.scale, 0.001f)
        assertEquals(1.08f, loud.scale, 0.001f)
        assertTrue(loud.glow > quiet.glow)
        assertEquals(loud, OrbMotion.pose(OrbMode.Listening, 1f, level = 5f)) // clamped
    }

    @Test
    fun thinkingMovesFasterThanIdle() {
        fun speed(mode: OrbMode) = (1..100).sumOf { abs(OrbMotion.pose(mode, it * 0.05f).yawDegrees - OrbMotion.pose(mode, (it - 1) * 0.05f).yawDegrees).toDouble() }
        assertTrue(speed(OrbMode.Thinking) > 2 * speed(OrbMode.Idle))
    }

    @Test
    fun speakingPulsesTheGlow() {
        val (min, max) = range(OrbMode.Speaking) { it.glow }
        assertTrue(max - min > 0.4f)
    }

    @Test
    fun reducedMotionHoldsStill() {
        OrbMode.entries.forEach { mode ->
            val a = OrbMotion.pose(mode, 0.3f, 1f, reducedMotion = true)
            val b = OrbMotion.pose(mode, 7.9f, 1f, reducedMotion = true)
            assertEquals(a, b)
            assertEquals(0f, a.yawDegrees)
            assertEquals(1f, a.scale)
        }
    }
}
