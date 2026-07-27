package dev.muzziknod.modules.audioeffects

import kotlin.test.Test
import kotlin.test.assertTrue

private const val WAY_TOO_LOW = -1_000_000.0
private const val WAY_TOO_HIGH = 1_000_000.0

/**
 * Out-of-range setter arguments are clamped to the declared `ParameterSpec.range` on
 * all four module types, never throw (FR-013; US2 AC5, Edge Cases). Setters have no
 * public getter to assert the clamped value directly, so this asserts the black-box
 * contract: no exception, and subsequent processing stays finite (an unclamped extreme
 * value — e.g. a `Q` of a million — would blow up the biquad/feedback math into
 * NaN/Infinity).
 */
class ParameterClampingTest {
    private fun assertFinite(samples: FloatArray, label: String) {
        for (sample in samples) {
            assertTrue(sample.isFinite(), "$label produced a non-finite sample after an out-of-range setter call")
        }
    }

    @Test
    fun reverbClampsOutOfRangeSetters() {
        val module = ReverbModule(instanceId = "reverb-clamp").also { it.onLoad() }
        module.setMix(WAY_TOO_HIGH)
        module.setDecayMs(WAY_TOO_LOW)
        module.setRoomSize(WAY_TOO_HIGH)
        val context = FixedInputContext(testSignal())
        module.process(context)
        assertFinite(context.lastOutput, "ReverbModule")
    }

    @Test
    fun delayClampsOutOfRangeSetters() {
        val module = DelayModule(instanceId = "delay-clamp").also { it.onLoad() }
        module.setMix(WAY_TOO_LOW)
        module.setDelayTimeMs(WAY_TOO_HIGH)
        module.setFeedback(WAY_TOO_HIGH)
        val context = FixedInputContext(testSignal())
        module.process(context)
        assertFinite(context.lastOutput, "DelayModule")
    }

    @Test
    fun distortionClampsOutOfRangeSetters() {
        val module = DistortionModule(instanceId = "distortion-clamp").also { it.onLoad() }
        module.setMix(WAY_TOO_HIGH)
        module.setDrive(WAY_TOO_LOW)
        module.setTone(WAY_TOO_HIGH)
        val context = FixedInputContext(testSignal())
        module.process(context)
        assertFinite(context.lastOutput, "DistortionModule")
    }

    @Test
    fun eqClampsOutOfRangeSetters() {
        val module = EqModule(instanceId = "eq-clamp").also { it.onLoad() }
        for (band in EqBand.entries) {
            module.setBandFrequency(band, WAY_TOO_HIGH)
            module.setBandGain(band, WAY_TOO_LOW)
            module.setBandQ(band, WAY_TOO_LOW)
        }
        val context = FixedInputContext(testSignal())
        module.process(context)
        assertFinite(context.lastOutput, "EqModule")
    }
}
