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
import kotlin.test.assertTrue

class RoutingGraphDisconnectTest {
    @Test
    fun disconnectingOneLinkStopsFlowBetweenThoseTwoModulesOnly() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)
        val emitted = listOf(MidiEvent(status = 0x90, data1 = 60, data2 = 100, frameOffset = 0))

        val generator = FakeModule.withPorts(
            "gen",
            listOf(PortSpec(id = "out", direction = PortDirection.Output, type = PortType.Midi)),
        ).apply { onProcessAction = { it.writeMidi("out", emitted) } }

        var loggerAReceived: List<MidiEvent> = emptyList()
        val loggerA = FakeModule.withPorts(
            "logger-a",
            listOf(PortSpec(id = "in", direction = PortDirection.Input, type = PortType.Midi)),
        ).apply { onProcessAction = { loggerAReceived = it.readMidi("in") } }

        var loggerBReceived: List<MidiEvent> = emptyList()
        val loggerB = FakeModule.withPorts(
            "logger-b",
            listOf(PortSpec(id = "in", direction = PortDirection.Input, type = PortType.Midi)),
        ).apply { onProcessAction = { loggerBReceived = it.readMidi("in") } }

        registry.load(generator)
        registry.load(loggerA)
        registry.load(loggerB)

        val connectionToA = graph.connect("gen", "out", "logger-a", "in")
        assertIs<ConnectResult.Connected>(connectionToA)
        val connectionToB = graph.connect("gen", "out", "logger-b", "in")
        assertIs<ConnectResult.Connected>(connectionToB)

        graph.disconnect(connectionToA.connection.id)
        graph.processCycle()

        assertTrue(loggerAReceived.isEmpty(), "logger-a should no longer receive events after disconnect")
        assertEquals(emitted, loggerBReceived)
    }
}