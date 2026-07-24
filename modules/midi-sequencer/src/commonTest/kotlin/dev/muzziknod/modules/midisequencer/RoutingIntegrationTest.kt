package dev.muzziknod.modules.midisequencer

import dev.muzziknod.host.graph.ConnectResult
import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RoutingIntegrationTest {
    @Test
    fun sinkReceivesExactlyWhatTheSequencerEmitsInCycleOrder() {
        fun configure(module: MidiSequencerModule) {
            module.setLength(3)
            module.setStep(0, listOf(NoteEvent(note = 60, velocity = 100)))
            module.setStep(2, listOf(NoteEvent(note = 67, velocity = 80)))
            module.play()
        }

        // Routed through the real core-host RoutingGraph, into a sink module.
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)
        val routedSequencer = MidiSequencerModule("seq-routed").also(::configure)
        val sink = FakeMidiSink("sink")
        registry.load(routedSequencer)
        registry.load(sink)
        assertIs<ConnectResult.Connected>(graph.connect("seq-routed", "out", "sink", "in"))

        // Identical sequencer driven directly, for comparison.
        val directSequencer = MidiSequencerModule("seq-direct").also(::configure)
        val directContext = CapturingProcessContext()

        repeat(10) {
            graph.processCycle()
            directSequencer.process(directContext)
        }

        val directEvents = directContext.midiWrites.flatMap { it.second }
        assertEquals(directEvents, sink.received, "the routing graph must deliver exactly what the sequencer emits, cycle by cycle, in order")
    }
}