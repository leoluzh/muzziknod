package dev.muzziknod.modules.midisequencer

import kotlin.test.Test
import kotlin.test.assertEquals

class PatternLengthWrapTest {
    @Test
    fun reducingLengthBelowCurrentStepWrapsPositionImmediately() {
        val module = MidiSequencerModule("seq-wrap-length")
        module.setLength(5)
        module.play()

        val context = CapturingProcessContext()
        repeat(3) { module.process(context) }
        assertEquals(3, module.currentStep)

        module.setLength(2)
        assertEquals(1, module.currentStep, "position must wrap into the new [0, 2) range immediately, not on the next cycle (FR-010)")

        // Must not throw processing the very next cycle after the wrap.
        module.process(context)
        assertEquals(0, module.currentStep)
    }

    @Test
    fun changingBpmDuringPlaybackDoesNotDisruptPlayback() {
        val module = MidiSequencerModule("seq-bpm-live")
        module.setLength(3)
        module.play()

        val context = CapturingProcessContext()
        module.process(context)
        module.setBpm(90)
        module.process(context)
        module.setBpm(180)
        module.process(context)

        assertEquals(0, module.currentStep, "BPM changes must not interrupt step advancement")
    }
}