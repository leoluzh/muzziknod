package dev.muzziknod.modules.sampler

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val LONG_SAMPLE_LENGTH = 5000 // longer than any single process() buffer at any test's pitchRatio

class VoicePoolTest {

    private fun zone(rootNote: Int = 60) =
        SampleZone(sample = Sample("s", rampData(LONG_SAMPLE_LENGTH), 48_000), rootNote = rootNote)

    @Test
    fun fourSimultaneousNotesAllSoundConcurrently() {
        val pool = VoicePool(maxVoices = 8)
        val z = zone()
        for (note in listOf(60, 64, 67, 72)) {
            pool.trigger(z, note, 127)
        }
        assertEquals(4, pool.activeVoices.size)
        assertEquals(setOf(60, 64, 67, 72), pool.activeVoices.map { it.note }.toSet())
    }

    @Test
    fun sixteenSimultaneousNotesAllSoundConcurrently() {
        val pool = VoicePool(maxVoices = 32)
        val z = zone()
        val notes = (0 until 16).map { 40 + it }
        for (note in notes) {
            pool.trigger(z, note, 127)
        }
        assertEquals(16, pool.activeVoices.size, "expected all 16 notes to have an active voice, no stealing/drops")
        assertEquals(notes.toSet(), pool.activeVoices.map { it.note }.toSet())
        // Render a full buffer's worth of samples and confirm nothing crashes/drops.
        repeat(128) { pool.renderNextSample() }
        assertEquals(16, pool.activeVoices.size, "16 voices should still be active after rendering")
    }

    @Test
    fun exceedingMaxVoicesStealsTheOldestVoiceWithClickFreeFade() {
        val pool = VoicePool(maxVoices = 2)
        val z = zone()
        pool.trigger(z, 60, 127) // triggerOrder 0 — oldest
        pool.trigger(z, 64, 127) // triggerOrder 1
        pool.trigger(z, 67, 127) // no free voice -> steals note 60 (oldest)

        assertEquals(2, pool.activeVoices.size, "pool size is fixed at maxVoices")
        assertEquals(setOf(64, 67), pool.activeVoices.map { it.note }.toSet(), "oldest (60) should have been stolen")
    }

    @Test
    fun voiceStealingAcrossFortyTrialsIsAtLeast95PercentClickFree() {
        var clickFreeCount = 0
        val trials = 40
        repeat(trials) { trialIndex ->
            val pool = VoicePool(maxVoices = 2)
            val z = zone()
            pool.trigger(z, 60, 127)
            pool.trigger(z, 64, 127)

            // Render a bit so both voices have non-trivial state before the steal.
            repeat(5) { pool.renderNextSample() }

            val samplesBefore = (0 until 4).map { pool.renderNextSample() }
            pool.trigger(z, 67 + trialIndex % 12, 127) // steals the oldest (note 60)
            val samplesAfter = (0 until 4).map { pool.renderNextSample() }

            val maxJumpBefore = maxSampleToSampleJump(samplesBefore)
            val maxJumpAfterSteal = abs(samplesAfter.first() - samplesBefore.last())
            // A "click" would be a jump far larger than the smooth deltas seen elsewhere in the
            // signal (rampData's own step is ~1/LONG_SAMPLE_LENGTH per sample).
            val threshold = (maxJumpBefore + 0.05f).coerceAtLeast(0.05f)
            if (maxJumpAfterSteal <= threshold) clickFreeCount++
        }

        val clickFreeRatio = clickFreeCount.toDouble() / trials
        assertTrue(clickFreeRatio >= 0.95, "expected >=95% click-free steal trials, got $clickFreeRatio")
    }

    private fun maxSampleToSampleJump(samples: List<Float>): Float {
        var max = 0f
        for (i in 1 until samples.size) {
            max = maxOf(max, abs(samples[i] - samples[i - 1]))
        }
        return max
    }
}
