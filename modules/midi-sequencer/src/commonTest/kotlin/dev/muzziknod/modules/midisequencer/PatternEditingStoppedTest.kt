package dev.muzziknod.modules.midisequencer

import kotlin.test.Test
import kotlin.test.assertEquals

class PatternEditingStoppedTest {
    @Test
    fun editWhileStoppedIsUsedOnNextPlay() {
        val module = MidiSequencerModule("seq-edit-stopped")
        module.setLength(1)
        module.setStep(0, listOf(NoteEvent(note = 60, velocity = 100)))

        // Overwrite before ever playing.
        module.setStep(0, listOf(NoteEvent(note = 72, velocity = 110)))
        module.play()

        val context = CapturingProcessContext()
        module.process(context)

        val events = context.midiWrites.single().second
        assertEquals(listOf(noteOnEvent(72, 110)), events, "the edited step content must be what plays, not the original")
    }

    @Test
    fun bpmAndLengthEditsWhileStoppedTakeEffectOnNextPlay() {
        val module = MidiSequencerModule("seq-edit-config")
        module.setLength(4)
        module.setBpm(200)
        module.setLength(2)
        module.setStep(0, listOf(NoteEvent(note = 50, velocity = 100)))
        module.setStep(1, listOf(NoteEvent(note = 55, velocity = 100)))
        module.play()

        val context = CapturingProcessContext()
        module.process(context)
        module.process(context)
        assertEquals(0, module.currentStep, "a 2-step pattern must wrap after step 1, proving setLength(2) took effect")
    }
}