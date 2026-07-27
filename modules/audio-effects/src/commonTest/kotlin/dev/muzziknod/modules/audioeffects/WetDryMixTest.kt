package dev.muzziknod.modules.audioeffects

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val EPSILON = 1e-4f

/**
 * `mix = 0.0` is 100% dry, `mix = 1.0` is 100% wet, `mix = 0.5` is a proportional
 * blend (FR-002-FR-005, SC-001, SC-002; US1 AC1-3). `WetDryMixer` is the single
 * crossfade every one of the four effect types routes its dry/wet blend through
 * (`ReverbModule`, `DelayModule`, `DistortionModule`), so exercising it directly
 * covers the shared contract those types can't yet expose a `setMix` for (that
 * setter arrives in US2, T025-T028). `EqModule` has no `mix` concept (data-model.md);
 * its own passthrough case — every band at 0 dB gain — is asserted separately below.
 */
class WetDryMixTest {
    @Test
    fun zeroMixIsSampleIdenticalToDry() {
        val drySamples = floatArrayOf(-1f, -0.25f, 0f, 0.5f, 0.9f)
        val wetSamples = floatArrayOf(0.3f, 0.99f, -0.4f, -1f, 0.1f)
        for (i in drySamples.indices) {
            assertEquals(drySamples[i], WetDryMixer.mix(drySamples[i], wetSamples[i], 0.0))
        }
    }

    @Test
    fun fullMixIsSampleIdenticalToWet() {
        val drySamples = floatArrayOf(-1f, -0.25f, 0f, 0.5f, 0.9f)
        val wetSamples = floatArrayOf(0.3f, 0.99f, -0.4f, -1f, 0.1f)
        for (i in drySamples.indices) {
            assertEquals(wetSamples[i], WetDryMixer.mix(drySamples[i], wetSamples[i], 1.0))
        }
    }

    @Test
    fun halfMixIsProportionalBlend() {
        val drySamples = floatArrayOf(-1f, -0.25f, 0f, 0.5f, 0.9f)
        val wetSamples = floatArrayOf(0.3f, 0.99f, -0.4f, -1f, 0.1f)
        for (i in drySamples.indices) {
            val expected = 0.5f * drySamples[i] + 0.5f * wetSamples[i]
            assertTrue(abs(WetDryMixer.mix(drySamples[i], wetSamples[i], 0.5) - expected) < EPSILON)
        }
    }

    @Test
    fun distortionAtDefaultFullWetHasNoDryComponent() {
        val module = DistortionModule(instanceId = "distortion-wet")
        module.onLoad()
        val input = FloatArray(16) { i -> if (i % 2 == 0) 0.8f else -0.8f }
        val context = FixedInputContext(input)
        module.process(context)
        // Default mix is 1.0 (100% wet) — soft-clip + lowpass must alter every sample.
        for (i in input.indices) {
            assertTrue(
                abs(context.lastOutput[i] - input[i]) > EPSILON,
                "index $i: fully-wet distortion output must differ from dry input",
            )
        }
    }

    @Test
    fun eqAtDefaultZeroGainBandsIsPassthrough() {
        val module = EqModule(instanceId = "eq-passthrough")
        module.onLoad()
        val input = FloatArray(32) { i -> kotlin.math.sin(i * 0.3).toFloat() }
        val context = FixedInputContext(input)
        module.process(context)
        for (i in input.indices) {
            assertTrue(
                abs(context.lastOutput[i] - input[i]) < EPSILON,
                "index $i: all bands at 0 dB gain must be a passthrough",
            )
        }
    }
}
