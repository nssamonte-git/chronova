package noel.chronova.app.voice

import noel.chronova.app.voice.EndpointDetector.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointDetectorTest {

    /** Feeds [levels] at 100 ms intervals starting at [from] ms; returns the first non-Continue decision and its time. */
    private fun EndpointDetector.feed(levels: List<Int>, from: Long = 0): Pair<Decision, Long> {
        levels.forEachIndexed { i, level ->
            val t = from + i * 100L
            val d = sample(level, t)
            if (d != Decision.Continue) return d to t
        }
        return Decision.Continue to (from + levels.size * 100L)
    }

    @Test
    fun speechThenSilenceEndsTheTurnAfterTheSilenceGap() {
        val d = EndpointDetector()
        val quiet = List(3) { 300 }          // 0–200 ms: calibration (floor 300)
        val speech = List(15) { 6_000 }      // 300–1700 ms
        val silence = List(20) { 350 }       // from 1800 ms
        val (decision, at) = d.feed(quiet + speech + silence)
        assertEquals(Decision.Done, decision)
        // Last speech at 300 + 14 × 100 = 1700 ms; done 1200 ms later.
        assertEquals(2_900L, at)
    }

    @Test
    fun shortPausesInsideASentenceDontEndIt() {
        val d = EndpointDetector()
        val levels = List(3) { 300 } + List(5) { 6_000 } + List(8) { 300 } + List(5) { 6_000 } + List(14) { 300 }
        val (decision, at) = d.feed(levels)
        assertEquals(Decision.Done, decision)
        // second burst ends at 300 + (5+8+5-1)*100 = 2000 ms; +1200 ms silence
        assertEquals(3_200L, at)
    }

    @Test
    fun aNoisyRoomRaisesTheBar() {
        val d = EndpointDetector()
        // Floor 2000 → threshold 6000: 5000-level chatter isn't speech.
        val (decision, at) = d.feed(List(3) { 2_000 } + List(100) { 5_000 })
        assertEquals(Decision.NothingHeard, decision)
        assertEquals(8_000L, at)
    }

    @Test
    fun aClickIsNotSpeech() {
        val d = EndpointDetector()
        // A 200 ms spike (a tap, a click) then quiet: still waiting, then nothing heard.
        val (decision, at) = d.feed(List(3) { 300 } + List(2) { 9_000 } + List(100) { 300 })
        assertEquals(Decision.NothingHeard, decision)
        assertEquals(8_000L, at)
    }

    @Test
    fun scatteredNoiseIsNotSpeech() {
        // A noisy room: single spikes above the bar every few samples, never 300 ms in a row.
        val noise = List(100) { if (it % 3 == 0) 2_300 else 450 }
        val (decision, at) = EndpointDetector().feed(List(3) { 450 } + noise)
        assertEquals(Decision.NothingHeard, decision)
        assertEquals(8_000L, at)
    }

    @Test
    fun silenceOnlyIsNothingHeard() {
        val (decision, at) = EndpointDetector().feed(List(120) { 100 })
        assertEquals(Decision.NothingHeard, decision)
        assertEquals(8_000L, at)
    }

    @Test
    fun theHardLimitEndsEndlessSpeech() {
        val d = EndpointDetector(maxMs = 2_000)
        val (decision, at) = d.feed(List(3) { 300 } + List(50) { 8_000 })
        assertEquals(Decision.Done, decision)
        assertEquals(2_000L, at)
        assertTrue(d.speechStarted)
    }

    @Test
    fun levelIsZeroAtTheFloorAndCapsAtOne() {
        val d = EndpointDetector()
        d.feed(List(3) { 300 })
        assertEquals(0f, d.level(300), 0.001f)
        assertEquals(1f, d.level(30_000), 0.001f)
        assertTrue(d.level(3_000) in 0.05f..0.9f)
    }

    @Test
    fun speechThatStartsDuringCalibrationDoesNotHideTheNextWords() {
        // The user is already talking as listening starts (levels ~8,000), then says "yes" (~6,000).
        val detector = EndpointDetector()
        val levels = listOf(8_000, 9_000, 7_000) + List(5) { 400 } + List(5) { 6_000 } + List(15) { 400 }
        var decision: EndpointDetector.Decision = EndpointDetector.Decision.Continue
        levels.forEachIndexed { i, level -> if (decision == EndpointDetector.Decision.Continue) decision = detector.sample(level, (i + 1) * 100L) }
        assertEquals(EndpointDetector.Decision.Done, decision)
    }

    @Test
    fun theLoudestSustainedLevelIgnoresShortPeaks() {
        val detector = EndpointDetector()
        // A 100 ms click at 12,000, then speech holding 5,000–7,000 for 400 ms.
        listOf(300, 300, 300, 12_000, 300, 300, 7_000, 5_000, 6_000, 7_000, 300).forEachIndexed { i, level -> detector.sample(level, (i + 1) * 100L) }
        assertEquals(5_000, detector.loudestSpeech)
    }
}
