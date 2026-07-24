package dev.muzziknod.modules.midisequencer

import kotlin.test.Test
import kotlin.test.assertEquals

class PatternEditingLiveTest {
    @Test
    fun editingAnUpcomingStepWhilePlayingAppliesNextTimeItsReached() {
        val module = MidiSequencerModule("seq-edit-live")
        module.setLength(3)
        module.setStep(0, listOf(NoteEvent(note = 60, velocity = 100)))
        module.setStep(1, listOf(NoteEvent(note = 64, velocity = 100)))
        module.setStep(2, listOf(NoteEvent(note = 67, velocity = 100)))
        module.play()

        val context = CapturingProcessContext()
        module.process(context) // consumes step 0 (60), moves to step 1
        context.midiWrites.clear()

        // Step 2 has not been reached yet this loop - edit it before we get there.
        module.setStep(2, listOf(NoteEvent(note = 70, velocity = 100)))

        module.process(context) // consumes step 1 (64), moves to step 2
        context.midiWrites.clear()

        module.process(context) // consumes step 2 - must reflect the edit
        val onEvents = context.midiWrites.flatMap { it.second }.filter { it.status == 0x90 }
        assertEquals(listOf(noteOnEvent(70, 100)), onEvents, "the edited note must play, not the original 67")
    }

    @Test
    fun editingAStepAlreadyPassedThisLoopDoesNotRetroactivelyReemit() {
        val module = MidiSequencerModule("seq-edit-past")
        module.setLength(2)
        module.setStep(0, listOf(NoteEvent(note = 60, velocity = 100)))
        module.setStep(1, listOf(NoteEvent(note = 64, velocity = 100)))
        module.play()

        val context = CapturingProcessContext()
        module.process(context) // consumes step 0, moves to step 1
        context.midiWrites.clear()

        // Step 0 already passed this loop - editing it must not cause an extra emission now.
        module.setStep(0, listOf(NoteEvent(note = 99, velocity = 100)))
        module.process(context) // consumes step 1, moves back to step 0 (wraps)
        val onEvents = context.midiWrites.flatMap { it.second }.filter { it.status == 0x90 }
        assertEquals(listOf(noteOnEvent(64, 100)), onEvents, "only step 1's note-on fires this cycle; the step-0 edit has no effect until step 0 is reached again")
    }
}