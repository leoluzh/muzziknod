package dev.muzziknod.modules.audioeffects

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

private const val CYCLE_SIZE = 128

/**
 * `setDelayTimeMs`/`setFeedback` change `DelayModule` output; delay timing stays
 * correct across 44.1kHz/48kHz/96kHz `sampleRate` constructor values (FR-009; US2
 * AC1).
 */
class DelayDspTest {
    private fun delayImpulseLandsAtExpectedSample(sampleRate: Int) {
        val module = DelayModule(instanceId = "delay-timing-$sampleRate", sampleRate = sampleRate)
        module.onLoad()
        val delayTimeMs = 375.0
        val expectedDelaySamples = ((delayTimeMs / 1000.0) * sampleRate).toInt()

        val impulse = FloatArray(CYCLE_SIZE).also { it[0] = 1f }
        val silence = FloatArray(CYCLE_SIZE)

        val output = mutableListOf<Float>()
        var cycleInput = impulse
        while (output.size < expectedDelaySamples + CYCLE_SIZE) {
            val context = FixedInputContext(cycleInput)
            module.process(context)
            output += context.lastOutput.toList()
            cycleInput = silence
        }

        // Index 0 carries the dry blend of the impulse itself (default mix = 0.5);
        // everything strictly between it and the delay tap must be silent.
        for (i in 1 until expectedDelaySamples) {
            assertTrue(abs(output[i]) < 0.01f, "sample $i must be silent before the delay tap arrives (sampleRate=$sampleRate)")
        }
        assertTrue(
            abs(output[expectedDelaySamples]) > 0.1f,
            "the delayed impulse must land exactly at sample $expectedDelaySamples (sampleRate=$sampleRate)",
        )
    }

    @Test
    fun delayTimingIsCorrectAt44100Hz() = delayImpulseLandsAtExpectedSample(44_100)

    @Test
    fun delayTimingIsCorrectAt48000Hz() = delayImpulseLandsAtExpectedSample(48_000)

    @Test
    fun delayTimingIsCorrectAt96000Hz() = delayImpulseLandsAtExpectedSample(96_000)

    @Test
    fun changingDelayTimeAndFeedbackChangesOutput() {
        val baseline = DelayModule(instanceId = "delay-baseline").also { it.onLoad() }
        val changed = DelayModule(instanceId = "delay-changed").also { it.onLoad() }
        val input = testSignal()

        // Default delayTimeMs (375ms => 18000 samples at 48kHz) never wraps its
        // circular buffer within a short test; shrink it first so feedback actually
        // gets read back within a handful of cycles.
        baseline.setDelayTimeMs(5.0)
        changed.setDelayTimeMs(5.0)

        fun runCycle(module: DelayModule): FloatArray {
            val context = FixedInputContext(input)
            module.process(context)
            return context.lastOutput
        }

        repeat(10) {
            runCycle(baseline)
            runCycle(changed)
        }

        changed.setDelayTimeMs(8.0)
        changed.setFeedback(0.9)

        var baselineOutput = FloatArray(0)
        var changedOutput = FloatArray(0)
        repeat(10) {
            baselineOutput = runCycle(baseline)
            changedOutput = runCycle(changed)
        }
        assertTrue(
            !baselineOutput.contentEquals(changedOutput),
            "changing delayTimeMs/feedback must change delay output within a few cycles",
        )
    }
}
