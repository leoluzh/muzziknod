package dev.muzziknod.modules.midisequencer

import kotlin.test.Test
import kotlin.test.assertEquals

class PatternStressTest {
    @Test
    fun sixteenStepPatternLoopsFiftyTimesWithNoDriftOrCrashes() {
        val module = MidiSequencerModule("seq-stress")
        module.setLength(16)
        for (i in 0 until 16 step 2) {
            module.setStep(i, listOf(NoteEvent(note = 36 + i, velocity = 100)))
        }
        module.play()

        val context = CapturingProcessContext()
        val loops = 50
        repeat(loops * 16) {
            module.process(context)
        }

        val onSequence = context.midiWrites.flatMap { it.second }.filter { it.status == 0x90 }.map { it.data1 }
        val expectedPerLoop = (0 until 16 step 2).map { 36 + it }
        val expected = (1..loops).flatMap { expectedPerLoop }
        assertEquals(expected, onSequence, "50 loops of a 16-step pattern must reproduce the exact same note sequence every time, no ordering drift")
        assertEquals(0, module.currentStep, "after exactly 50 full loops the position must land back on step 0")
    }
}