package dev.muzziknod.modules.midisequencer

import kotlin.test.Test
import kotlin.test.assertEquals

class PatternPlaybackTest {
    @Test
    fun playedPatternEmitsProgrammedNotesInOrderAcrossManyLoops() {
        val module = MidiSequencerModule("seq-playback")
        module.setLength(4)
        module.setStep(0, listOf(NoteEvent(note = 60, velocity = 100)))
        // step 1 left empty on purpose (Edge Cases: an empty step emits nothing)
        module.setStep(2, listOf(NoteEvent(note = 64, velocity = 90)))
        module.setStep(3, listOf(NoteEvent(note = 67, velocity = 80)))
        module.play()

        val loops = 20
        val context = CapturingProcessContext()
        repeat(loops * 4) {
            module.process(context)
        }
        // flush the final loop's last sustained note deterministically
        module.stop()
        module.process(context)

        val allEvents = context.midiWrites.flatMap { it.second }
        val noteOnSequence = allEvents.filter { it.status == 0x90 }.map { it.data1 }
        val noteOffCount = allEvents.count { it.status == 0x80 }
        val noteOnCount = noteOnSequence.size

        val expectedOnSequence = (1..loops).flatMap { listOf(60, 64, 67) }
        assertEquals(expectedOnSequence, noteOnSequence, "note-on order must repeat the programmed pattern every loop, in step order, with no drift")
        assertEquals(loops * 3, noteOnCount)
        assertEquals(noteOnCount, noteOffCount, "every emitted note-on must eventually receive a matching note-off")

        // step 1 never had notes programmed - never appears as a note-on
        assertEquals(0, noteOnSequence.count { it != 60 && it != 64 && it != 67 })
    }

    @Test
    fun playbackWrapsFromLastStepBackToFirst() {
        val module = MidiSequencerModule("seq-wrap")
        module.setLength(3)
        module.play()

        val context = CapturingProcessContext()
        assertEquals(0, module.currentStep)
        module.process(context)
        assertEquals(1, module.currentStep)
        module.process(context)
        assertEquals(2, module.currentStep)
        module.process(context)
        assertEquals(0, module.currentStep, "must wrap back to step 0 after the last step (FR-005)")
    }
}