package dev.muzziknod.host.graph

import dev.muzziknod.host.contract.MidiEvent
import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.lifecycle.FakeModule
import dev.muzziknod.host.lifecycle.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RoutingGraphConnectTest {
    @Test
    fun connectingGeneratorToLoggerDeliversEventsInEmittedOrder() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)

        val emitted = listOf(
            MidiEvent(status = 0x90, data1 = 60, data2 = 100, frameOffset = 0),
            MidiEvent(status = 0x80, data1 = 60, data2 = 0, frameOffset = 64),
        )
        val generator = FakeModule.withPorts(
            "midi-gen",
            listOf(PortSpec(id = "out", direction = PortDirection.Output, type = PortType.Midi)),
        ).apply { onProcessAction = { it.writeMidi("out", emitted) } }

        var received: List<MidiEvent> = emptyList()
        val logger = FakeModule.withPorts(
            "midi-logger",
            listOf(PortSpec(id = "in", direction = PortDirection.Input, type = PortType.Midi)),
        ).apply { onProcessAction = { received = it.readMidi("in") } }

        registry.load(generator)
        registry.load(logger)

        val connectResult = graph.connect("midi-gen", "out", "midi-logger", "in")
        assertIs<ConnectResult.Connected>(connectResult)

        graph.processCycle()

        assertEquals(emitted, received)
    }
}