package dev.muzziknod.modules.midisequencer

import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class UnconnectedPlaybackTest {
    @Test
    fun sequencerAdvancesWithNoOutputConnectionAtAll() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)
        val sequencer = MidiSequencerModule("seq-lonely")
        sequencer.setLength(3)
        sequencer.setStep(0, listOf(NoteEvent(note = 60, velocity = 100)))
        sequencer.play()
        registry.load(sequencer)

        repeat(9) { graph.processCycle() } // no connect() call at all

        assertEquals(0, sequencer.currentStep, "9 cycles over a 3-step pattern must land back on step 0, no errors along the way")
    }
}