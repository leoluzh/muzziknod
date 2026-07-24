package dev.muzziknod.modules.midisequencer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatternStopTest {
    @Test
    fun stopFlushesSustainedNoteOffOnNextProcessCycle() {
        val module = MidiSequencerModule("seq-stop")
        module.setLength(2)
        module.setStep(0, listOf(NoteEvent(note = 60, velocity = 100, gateSteps = 4)))
        module.play()

        val context = CapturingProcessContext()
        module.process(context) // emits note-on 60, note stays sustained (gate 4 > 1 step elapsed)
        context.midiWrites.clear()

        module.stop()
        module.process(context)

        val events = context.midiWrites.single().second
        assertEquals(listOf(noteOffEvent(60)), events, "stop() must flush the sustained note-off on the very next process() call, and nothing else")
    }

    @Test
    fun stopIsIdempotentAndProcessGoesQuietAfterFlush() {
        val module = MidiSequencerModule("seq-stop-idempotent")
        module.setStep(0, listOf(NoteEvent(note = 60, velocity = 100, gateSteps = 4)))
        module.play()

        val context = CapturingProcessContext()
        module.process(context)
        context.midiWrites.clear()

        module.stop()
        module.stop() // calling stop() again before the flush is consumed must not duplicate work
        module.process(context)
        assertEquals(1, context.midiWrites.size)
        assertEquals(listOf(noteOffEvent(60)), context.midiWrites.single().second)

        context.midiWrites.clear()
        module.process(context)
        assertTrue(context.midiWrites.isEmpty(), "once idle (stopped, no pending flush), process() must not write anything")
    }
}