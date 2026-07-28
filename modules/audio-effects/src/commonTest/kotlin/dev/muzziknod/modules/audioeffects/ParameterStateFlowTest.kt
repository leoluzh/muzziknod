package dev.muzziknod.modules.audioeffects

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Each effect module's published `StateFlow<Double>` matches the smoothed value after
 * calling the corresponding `set...()` and running one `process()` cycle
 * (contracts/host-observability-contract.md).
 */
class ParameterStateFlowTest {
    @Test
    fun delayModuleParameterStateFlowsMatchSmoothedValuesAfterOneCycle() {
        val module = DelayModule(instanceId = "delay-1").also { it.onLoad() }
        module.setMix(0.9)
        module.setDelayTimeMs(500.0)
        module.setFeedback(0.4)

        // The smoother ramps over 64 samples; a single 128-sample cycle is enough
        // for the target to be fully reached (ParameterSmoother's rampSamples default).
        module.process(FixedInputContext(testSignal()))

        assertEquals(0.9, module.mix.value)
        assertEquals(500.0, module.delayTimeMs.value)
        assertEquals(0.4, module.feedback.value)
    }

    @Test
    fun reverbModuleParameterStateFlowsMatchSmoothedValuesAfterOneCycle() {
        val module = ReverbModule(instanceId = "reverb-1").also { it.onLoad() }
        module.setMix(0.8)
        module.setDecayMs(2500.0)
        module.setRoomSize(0.9)

        module.process(FixedInputContext(testSignal()))

        assertEquals(0.8, module.mix.value)
        assertEquals(2500.0, module.decayMs.value)
        assertEquals(0.9, module.roomSize.value)
    }

    @Test
    fun distortionModuleParameterStateFlowsMatchSmoothedValuesAfterOneCycle() {
        val module = DistortionModule(instanceId = "distortion-1").also { it.onLoad() }
        module.setMix(0.6)
        module.setDrive(10.0)
        module.setTone(3000.0)

        module.process(FixedInputContext(testSignal()))

        assertEquals(0.6, module.mix.value)
        assertEquals(10.0, module.drive.value)
        assertEquals(3000.0, module.tone.value)
    }

    @Test
    fun eqModuleParameterStateFlowsMatchSmoothedValuesAfterOneCycle() {
        val module = EqModule(instanceId = "eq-1").also { it.onLoad() }
        module.setBandFrequency(EqBand.Low, 200.0)
        module.setBandGain(EqBand.Low, 6.0)
        module.setBandQ(EqBand.Low, 2.0)

        module.process(FixedInputContext(testSignal()))

        assertEquals(200.0, module.bandFrequency(EqBand.Low).value)
        assertEquals(6.0, module.bandGain(EqBand.Low).value)
        assertEquals(2.0, module.bandQ(EqBand.Low).value)
    }
}
