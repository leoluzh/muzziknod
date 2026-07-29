package dev.muzziknod.modules.sampler

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PitchRatioTest {

    private fun moduleWithSineZone(rootNote: Int = 60): SamplerModule {
        val module = SamplerModule(instanceId = "sampler-1", sampleRate = 48_000, maxVoices = 4)
        module.onLoad()
        module.addZoneForTesting(
            SampleZone(sample = Sample("sine", sineData(length = 4000, cycles = 16.0), 48_000), rootNote = rootNote),
        )
        return module
    }

    private fun triggeredVoice(note: Int, rootNote: Int = 60): Voice {
        val module = moduleWithSineZone(rootNote)
        module.process(FakeProcessContext(listOf(noteOn(note, 127))))
        return module.voicePoolForTesting().activeVoices.first()
    }

    @Test
    fun oneOctaveUpDoublesPitchRatio() {
        assertEquals(2.0, triggeredVoice(note = 72).pitchRatio, absoluteTolerance = 1e-9)
    }

    @Test
    fun oneOctaveDownHalvesPitchRatio() {
        assertEquals(0.5, triggeredVoice(note = 48).pitchRatio, absoluteTolerance = 1e-9)
    }

    @Test
    fun twoOctavesUpQuadruplesPitchRatio() {
        assertEquals(4.0, triggeredVoice(note = 84).pitchRatio, absoluteTolerance = 1e-9)
    }

    @Test
    fun twoOctavesDownQuartersPitchRatio() {
        assertEquals(0.25, triggeredVoice(note = 36).pitchRatio, absoluteTolerance = 1e-9)
    }

    @Test
    fun rootNoteTriggersUnityPitchRatio() {
        assertEquals(1.0, triggeredVoice(note = 60).pitchRatio, absoluteTolerance = 1e-9)
    }

    @Test
    fun transposedPlaybackAdvancesPositionByPitchRatioPerSample() {
        val module = moduleWithSineZone()
        module.process(FakeProcessContext(listOf(noteOn(72, 127)))) // one octave up -> ratio 2.0
        val voice = module.voicePoolForTesting().activeVoices.first()
        // BUFFER_SIZE (128) samples already rendered by process(); position should have
        // advanced by 128 * 2.0 = 256.0 from its starting point of 0.0.
        assertTrue(abs(voice.position - 256.0) <= 1e-6, "expected position ~256.0, was ${voice.position}")
    }
}

private fun assertEquals(expected: Double, actual: Double, absoluteTolerance: Double) {
    assertTrue(abs(expected - actual) <= absoluteTolerance, "expected $expected but was $actual")
}
