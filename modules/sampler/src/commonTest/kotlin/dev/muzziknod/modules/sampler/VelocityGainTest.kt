package dev.muzziknod.modules.sampler

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class VelocityGainTest {

    private fun moduleWithZone(rootNote: Int = 60, gain: Double = 1.0): SamplerModule {
        val module = SamplerModule(instanceId = "sampler-1", sampleRate = 48_000, maxVoices = 4)
        module.onLoad()
        module.addZoneForTesting(
            SampleZone(sample = Sample("s1", rampData(10), 48_000), rootNote = rootNote, gain = gain),
        )
        return module
    }

    private fun firstSampleOf(velocity: Int, gain: Double = 1.0, note: Int = 60, rootNote: Int = 60): Float {
        val module = moduleWithZone(rootNote = rootNote, gain = gain)
        val context = FakeProcessContext(listOf(noteOn(note, velocity)))
        module.process(context)
        return context.lastWritten!!.samples[0]
    }

    @Test
    fun outputAmplitudeScalesLinearlyWithVelocity() {
        val full = firstSampleOf(velocity = 127)
        val half = firstSampleOf(velocity = 64)
        val min = firstSampleOf(velocity = 1)

        // rampData[0] = 1/10, so full-velocity output is (1/10) * (127/127) = 0.1
        assertTrue(abs(full - 0.1f) <= 1e-4f, "full velocity output was $full")
        assertTrue(abs(half - 0.1f * (64f / 127f)) <= 1e-4f, "half velocity output was $half")
        assertTrue(abs(min - 0.1f * (1f / 127f)) <= 1e-4f, "min velocity output was $min")
    }

    @Test
    fun perZoneGainMultipliesOnTopOfVelocity() {
        val unityGain = firstSampleOf(velocity = 127, gain = 1.0)
        val doubleGain = firstSampleOf(velocity = 127, gain = 2.0)

        assertTrue(abs(doubleGain - unityGain * 2f) <= 1e-4f, "expected doubled gain, got $doubleGain vs $unityGain")
    }

    @Test
    fun velocityAndGainApplyIdenticallyOnATransposedNote() {
        // note=72 is one octave above rootNote=60 — velocity/gain scaling must be
        // unaffected by the transposition (US2 AC3).
        val rootFull = firstSampleOf(velocity = 127, note = 60, rootNote = 60)
        val transposedFull = firstSampleOf(velocity = 127, note = 72, rootNote = 60)
        val transposedHalfVelocity = firstSampleOf(velocity = 64, note = 72, rootNote = 60)
        val transposedDoubleGain = firstSampleOf(velocity = 127, gain = 2.0, note = 72, rootNote = 60)

        assertTrue(
            abs(transposedFull - rootFull) <= 1e-4f,
            "transposed full-velocity first sample should match root ($rootFull), was $transposedFull",
        )
        assertTrue(
            abs(transposedHalfVelocity - rootFull * (64f / 127f)) <= 1e-4f,
            "expected half-velocity scaling on transposed note, got $transposedHalfVelocity",
        )
        assertTrue(
            abs(transposedDoubleGain - rootFull * 2f) <= 1e-4f,
            "expected doubled gain on transposed note, got $transposedDoubleGain",
        )
    }
}
