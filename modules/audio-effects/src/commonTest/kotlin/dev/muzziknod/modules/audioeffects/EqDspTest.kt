package dev.muzziknod.modules.audioeffects

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * `setBandFrequency`/`setBandGain`/`setBandQ` per [EqBand] change `EqModule` output
 * behavior across subsequent cycles (US2 AC4).
 */
class EqDspTest {
    private fun runCycle(module: EqModule, input: FloatArray): FloatArray {
        val context = FixedInputContext(input)
        module.process(context)
        return context.lastOutput
    }

    private fun assertBandChangeAltersOutput(band: EqBand) {
        val baseline = EqModule(instanceId = "eq-baseline-$band").also { it.onLoad() }
        val changed = EqModule(instanceId = "eq-changed-$band").also { it.onLoad() }
        val input = testSignal()

        var baselineOutput = FloatArray(0)
        var changedOutput = FloatArray(0)
        repeat(3) {
            baselineOutput = runCycle(baseline, input)
            changedOutput = runCycle(changed, input)
        }
        assertContentEquals(baselineOutput, changedOutput, "identical params must produce identical output")

        changed.setBandFrequency(band, 3000.0)
        changed.setBandGain(band, 12.0)
        changed.setBandQ(band, 3.0)

        repeat(3) {
            baselineOutput = runCycle(baseline, input)
            changedOutput = runCycle(changed, input)
        }
        assertTrue(
            !baselineOutput.contentEquals(changedOutput),
            "changing $band band's frequency/gain/Q must change EQ output within a few cycles",
        )
    }

    @Test
    fun changingLowBandChangesOutput() = assertBandChangeAltersOutput(EqBand.Low)

    @Test
    fun changingMidBandChangesOutput() = assertBandChangeAltersOutput(EqBand.Mid)

    @Test
    fun changingHighBandChangesOutput() = assertBandChangeAltersOutput(EqBand.High)
}
