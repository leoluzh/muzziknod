package dev.muzziknod.modules.audioeffects

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * `setDrive`/`setTone` change `DistortionModule` output behavior across subsequent
 * cycles (US2 AC3).
 */
class DistortionDspTest {
    @Test
    fun changingDriveAndToneChangesOutput() {
        val baseline = DistortionModule(instanceId = "distortion-baseline").also { it.onLoad() }
        val changed = DistortionModule(instanceId = "distortion-changed").also { it.onLoad() }
        val input = testSignal()

        fun runCycle(module: DistortionModule): FloatArray {
            val context = FixedInputContext(input)
            module.process(context)
            return context.lastOutput
        }

        var baselineOutput = FloatArray(0)
        var changedOutput = FloatArray(0)
        repeat(3) {
            baselineOutput = runCycle(baseline)
            changedOutput = runCycle(changed)
        }
        assertContentEquals(baselineOutput, changedOutput, "identical params must produce identical output")

        changed.setDrive(18.0)
        changed.setTone(500.0)

        repeat(3) {
            baselineOutput = runCycle(baseline)
            changedOutput = runCycle(changed)
        }
        assertTrue(
            !baselineOutput.contentEquals(changedOutput),
            "changing drive/tone must change distortion output within a few cycles",
        )
    }
}
