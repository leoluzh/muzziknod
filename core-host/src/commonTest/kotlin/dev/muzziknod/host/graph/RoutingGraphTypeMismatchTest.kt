package dev.muzziknod.host.graph

import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.lifecycle.FakeModule
import dev.muzziknod.host.lifecycle.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertIs

class RoutingGraphTypeMismatchTest {
    @Test
    fun connectingAudioOutputToMidiInputIsRejected() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)

        val audioSource = FakeModule.withPorts(
            "osc",
            listOf(PortSpec(id = "out", direction = PortDirection.Output, type = PortType.Audio, sampleRate = 48_000)),
        )
        val midiSink = FakeModule.withPorts(
            "midi-logger",
            listOf(PortSpec(id = "in", direction = PortDirection.Input, type = PortType.Midi)),
        )
        registry.load(audioSource)
        registry.load(midiSink)

        val result = graph.connect("osc", "out", "midi-logger", "in")

        assertIs<ConnectResult.Rejected>(result)
    }
}