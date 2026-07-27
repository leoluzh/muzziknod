package dev.muzziknod.modules.audioeffects

import dev.muzziknod.host.contract.Module
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * No input connected (`context.readAudio("in")` returns an empty buffer) must produce
 * silent output with no exception, for all four module types (FR-014; Edge Cases).
 */
class SilentInputTest {
    private fun assertSilentOutput(module: Module) {
        module.onLoad()
        val context = EmptyInputContext()
        module.process(context)
        assertTrue(
            context.lastOutput.all { it == 0f },
            "${module::class.simpleName} must output silence when no input is connected",
        )
    }

    @Test
    fun reverbIsSilentWithNoInput() = assertSilentOutput(ReverbModule(instanceId = "reverb-silent"))

    @Test
    fun delayIsSilentWithNoInput() = assertSilentOutput(DelayModule(instanceId = "delay-silent"))

    @Test
    fun distortionIsSilentWithNoInput() = assertSilentOutput(DistortionModule(instanceId = "distortion-silent"))

    @Test
    fun eqIsSilentWithNoInput() = assertSilentOutput(EqModule(instanceId = "eq-silent"))
}
