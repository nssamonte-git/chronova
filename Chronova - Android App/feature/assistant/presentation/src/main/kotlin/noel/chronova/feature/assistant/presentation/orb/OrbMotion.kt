package noel.chronova.feature.assistant.presentation.orb

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
     * The shortest time between two drawn frames, in milliseconds; 0 means every frame.
     * Each orb frame makes the whole window redraw (it's a TextureView), which kept budget GPUs
     * busy at about 30 fps on screens where nothing else moved (15 Nov 2026). Resting, the orb
     * only sways slowly (about 15° a second at most), so about 12 frames a second look the same.
     * Listening, thinking and speaking follow the voice and keep every frame.
     */
    fun frameIntervalMs(mode: OrbMode, reducedMotion: Boolean): Long = when {
        reducedMotion -> STILL_CHECK_MS
        mode == OrbMode.Idle -> IDLE_FRAME_MS
        else -> 0L
    }

    const val IDLE_FRAME_MS = 83L

    /** With "remove animations" the pose only changes with the mode, so look a few times a second. */
    const val STILL_CHECK_MS = 250L

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
