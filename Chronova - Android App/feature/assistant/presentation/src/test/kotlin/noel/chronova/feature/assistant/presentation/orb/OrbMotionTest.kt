package noel.chronova.feature.assistant.presentation.orb

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

    @Test
    fun theRestingOrbDrawsAboutTwelveFramesASecondAndTheOthersEveryFrame() {
        assertEquals(83L, OrbMotion.frameIntervalMs(OrbMode.Idle, reducedMotion = false))
        listOf(OrbMode.Listening, OrbMode.Thinking, OrbMode.Speaking).forEach { mode ->
            assertEquals("$mode", 0L, OrbMotion.frameIntervalMs(mode, reducedMotion = false))
        }
        OrbMode.entries.forEach { mode -> assertEquals("$mode still", 250L, OrbMotion.frameIntervalMs(mode, reducedMotion = true)) }
    }

    @Test
    fun atTwelveFramesASecondTheRestingSwayStepsLessThanTwoDegrees() {
        // The step between two drawn idle frames stays too small to see as a jump on a 60 dp orb.
        val step = OrbMotion.IDLE_FRAME_MS / 1000f
        val biggest = (0..2000).maxOf { i ->
            val t = i * 0.01f
            abs(OrbMotion.pose(OrbMode.Idle, t + step).yawDegrees - OrbMotion.pose(OrbMode.Idle, t).yawDegrees)
        }
        assertTrue("largest idle step $biggest degrees", biggest < 2f)
    }

    @Test
    fun withRemoveAnimationsThePoseNeverChangesWithinAMode() {
        OrbMode.entries.forEach { mode ->
            val poses = (0..200).map { OrbMotion.pose(mode, it * 0.05f, level = 1f, reducedMotion = true) }.toSet()
            assertEquals("$mode", 1, poses.size)
        }
    }
}
