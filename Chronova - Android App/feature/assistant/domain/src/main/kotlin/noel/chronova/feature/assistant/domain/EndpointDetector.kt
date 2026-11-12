package noel.chronova.feature.assistant.domain

/**
 * Decides, from microphone levels sampled about every 100 ms, when the user has
 * finished speaking — so a voice-only turn ends without a Done button.
 *
 * The first [calibrationMs] learn the room's noise floor. Afterwards the floor only goes
 * down: whenever the last [calibrationMs] were quieter on average, that is the new floor, so a
 * user already talking when listening starts doesn't keep the bar above their own voice
 * once they pause (seen on the phone: a "yes" said across the start was missed), while
 * steady room noise still keeps it high. Speech is a level clearly
 * above it ([speechRatio] times the floor, and at least [minSpeechLevel]) for at least
 * [minSpeechMs] without a break, so taps, clicks and scattered room noise don't count; the turn ends after
 * [silenceMs] of quiet following speech. With no speech by
 * [noSpeechTimeoutMs] the turn ends empty, and [maxMs] is a hard limit.
 */
class EndpointDetector(
    private val calibrationMs: Long = 300,
    private val silenceMs: Long = 1_200,
    private val noSpeechTimeoutMs: Long = 8_000,
    private val maxMs: Long = 60_000,
    private val speechRatio: Float = 3f,
    private val minSpeechLevel: Int = 1_500,
    private val minSpeechMs: Long = 300,
    private val sampleMs: Long = 100,
) {
    sealed interface Decision {
        data object Continue : Decision

        /** Speech was heard and has ended (or the hard limit was reached). */
        data object Done : Decision

        /** Nothing that sounded like speech before the timeout. */
        data object NothingHeard : Decision
    }

    private var floorSum = 0L
    private var floorCount = 0
    private var floor = 0f
    private val recent = ArrayDeque<Int>()
    private var heardSpeech = false
    private var loudRunMs = 0L
    private var lastSpeechAt = 0L

    /** True once something above the noise floor has been heard. */
    val speechStarted: Boolean get() = heardSpeech

    /**
     * The loudest level held for [minSpeechMs] (the lowest sample of the loudest such stretch).
     * Someone talking to the phone reaches several thousand; a voice across the room stays lower.
     */
    var loudestSpeech: Int = 0
        private set
    private val run = ArrayDeque<Int>()

    /** Feeds one sample: the maximum amplitude (0..32767) since the last one, at [elapsedMs] into the turn. */
    fun sample(amplitude: Int, elapsedMs: Long): Decision {
        if (elapsedMs < calibrationMs) {
            floorSum += amplitude
            floorCount++
            floor = floorSum / floorCount.toFloat()
            return Decision.Continue
        }
        run.addLast(amplitude)
        if (run.size > (minSpeechMs / sampleMs).coerceAtLeast(1)) run.removeFirst()
        if (run.size.toLong() * sampleMs >= minSpeechMs) loudestSpeech = maxOf(loudestSpeech, run.min())
        recent.addLast(amplitude)
        if (recent.size > (calibrationMs / sampleMs).coerceAtLeast(1)) recent.removeFirst()
        if (recent.size.toLong() * sampleMs >= calibrationMs) floor = minOf(floor, recent.average().toFloat())
        val threshold = threshold()
        if (amplitude >= threshold) {
            loudRunMs += sampleMs
            if (loudRunMs >= minSpeechMs) heardSpeech = true
            if (heardSpeech) lastSpeechAt = elapsedMs
        } else {
            loudRunMs = 0
        }
        return when {
            elapsedMs >= maxMs -> if (heardSpeech) Decision.Done else Decision.NothingHeard
            heardSpeech && elapsedMs - lastSpeechAt >= silenceMs -> Decision.Done
            !heardSpeech && elapsedMs >= noSpeechTimeoutMs -> Decision.NothingHeard
            else -> Decision.Continue
        }
    }

    /** A 0..1 level for the listening animation, relative to the speech threshold. */
    fun level(amplitude: Int): Float {
        val floor = floor()
        return ((amplitude - floor) / (threshold() * 3f - floor).coerceAtLeast(1f)).coerceIn(0f, 1f)
    }

    private fun floor(): Float = floor

    private fun threshold(): Float = maxOf(minSpeechLevel.toFloat(), floor * speechRatio)
}
