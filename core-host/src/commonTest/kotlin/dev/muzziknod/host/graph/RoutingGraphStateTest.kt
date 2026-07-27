package dev.muzziknod.host.graph

import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.lifecycle.FakeModule
import dev.muzziknod.host.lifecycle.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RoutingGraphStateTest {
    @Test
    fun stateMatchesConnectionsImmediatelyAfterConnect() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)
        val source = FakeModule.withPorts(
            "source",
            listOf(PortSpec(id = "out", direction = PortDirection.Output, type = PortType.Midi)),
        )
        val target = FakeModule.withPorts(
            "target",
            listOf(PortSpec(id = "in", direction = PortDirection.Input, type = PortType.Midi)),
        )
        registry.load(source)
        registry.load(target)

        val result = graph.connect("source", "out", "target", "in")

        assertIs<ConnectResult.Connected>(result)
        assertEquals(graph.connections().toList(), graph.state.value)
    }

    @Test
    fun stateMatchesConnectionsImmediatelyAfterDisconnect() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)
        val source = FakeModule.withPorts(
            "source",
            listOf(PortSpec(id = "out", direction = PortDirection.Output, type = PortType.Midi)),
        )
        val target = FakeModule.withPorts(
            "target",
            listOf(PortSpec(id = "in", direction = PortDirection.Input, type = PortType.Midi)),
        )
        registry.load(source)
        registry.load(target)
        val connected = graph.connect("source", "out", "target", "in") as ConnectResult.Connected

        graph.disconnect(connected.connection.id)

        assertEquals(emptyList(), graph.state.value)
        assertEquals(graph.connections().toList(), graph.state.value)
    }

    @Test
    fun stateMatchesConnectionsImmediatelyAfterRemoveModule() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)
        val source = FakeModule.withPorts(
            "source",
            listOf(PortSpec(id = "out", direction = PortDirection.Output, type = PortType.Midi)),
        )
        val target = FakeModule.withPorts(
            "target",
            listOf(PortSpec(id = "in", direction = PortDirection.Input, type = PortType.Midi)),
        )
        registry.load(source)
        registry.load(target)
        graph.connect("source", "out", "target", "in")

        graph.removeModule("source")

        assertEquals(emptyList(), graph.state.value)
        assertEquals(graph.connections().toList(), graph.state.value)
    }
}
