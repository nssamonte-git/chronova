package noel.chronova.app.voice.orb

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** What the assistant orb is doing; each mode moves and glows differently. */
enum class OrbMode { Idle, Listening, Thinking, Speaking }

/**
 * The orb's pose for one frame: it sways and tilts (never spins all the way —
 * the hourglass is only on the front), pulses in size, and glows.
 */
data class OrbPose(val yawDegrees: Float, val pitchDegrees: Float, val scale: Float, val glow: Float) {
    companion object {
        val Still = OrbPose(0f, 0f, 1f, 1f)
    }
}

/** Pure motion curves for the orb, so they can be unit-tested without a renderer. */
object OrbMotion {
    /**
     * [seconds] since the orb appeared; [level] the microphone level in 0..1 (listening only).
     * With [reducedMotion] (the system's "remove animations") the orb stays still.
     */
    fun pose(mode: OrbMode, seconds: Float, level: Float = 0f, reducedMotion: Boolean = false): OrbPose {
        if (reducedMotion) return OrbPose.Still.copy(glow = if (mode == OrbMode.Idle) 1f else 1.2f)
        val voice = level.coerceIn(0f, 1f)
        return when (mode) {
            OrbMode.Idle -> OrbPose(
                yawDegrees = 22f * wave(seconds, 9f),
                pitchDegrees = 6f * wave(seconds, 13f),
                scale = 1f + 0.015f * wave(seconds, 4f),
                glow = 1f,
            )
            OrbMode.Listening -> OrbPose(
                yawDegrees = 12f * wave(seconds, 7f),
                pitchDegrees = 4f * wave(seconds, 11f),
                scale = 1f + 0.08f * voice,
                glow = 1f + 0.8f * voice,
            )
            OrbMode.Thinking -> OrbPose(
                yawDegrees = 28f * wave(seconds, 2.2f),
                pitchDegrees = 8f * wave(seconds, 3f),
                scale = 1f,
                glow = 1.1f + 0.25f * wave(seconds, 0.8f),
            )
            OrbMode.Speaking -> {
                val beat = abs(wave(seconds, 0.9f))
                OrbPose(
                    yawDegrees = 15f * wave(seconds, 6f),
                    pitchDegrees = 4f * wave(seconds, 9f),
                    scale = 1f + 0.03f * beat,
                    glow = 1.1f + 0.6f * beat,
                )
            }
        }
    }

    /** sin over a [period] in seconds, in -1..1. */
    private fun wave(seconds: Float, period: Float): Float = sin(2.0 * PI * seconds / period).toFloat()
}
