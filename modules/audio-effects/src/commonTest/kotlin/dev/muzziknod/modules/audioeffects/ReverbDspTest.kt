package dev.muzziknod.modules.audioeffects

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * `setDecayMs`/`setRoomSize` change `ReverbModule` output behavior across subsequent
 * cycles without interrupting processing (US2 AC2).
 */
class ReverbDspTest {
    @Test
    fun changingDecayAndRoomSizeChangesOutput() {
        val baseline = ReverbModule(instanceId = "reverb-baseline").also { it.onLoad() }
        val changed = ReverbModule(instanceId = "reverb-changed").also { it.onLoad() }
        val input = testSignal()

        fun runCycle(module: ReverbModule): FloatArray {
            val context = FixedInputContext(input)
            module.process(context)
            return context.lastOutput
        }

        // Comb filter buffers are ~1200-1500 samples long (scaled Schroeder delay
        // lengths); warm them up past a full wrap so their feedback-fed state is
        // actually non-zero before comparing.
        var baselineOutput = FloatArray(0)
        var changedOutput = FloatArray(0)
        repeat(15) {
            baselineOutput = runCycle(baseline)
            changedOutput = runCycle(changed)
        }
        assertContentEquals(baselineOutput, changedOutput, "identical params must produce identical output")

        changed.setDecayMs(4000.0)
        changed.setRoomSize(0.9)

        repeat(15) {
            baselineOutput = runCycle(baseline)
            changedOutput = runCycle(changed)
        }
        assertTrue(
            !baselineOutput.contentEquals(changedOutput),
            "changing decayMs/roomSize must change reverb output within a few cycles",
        )
    }
}
