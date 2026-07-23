package dev.muzziknod.host.lifecycle

import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.graph.ConnectResult
import dev.muzziknod.host.graph.RoutingGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ModuleRemovalTest {
    private val ioPorts = listOf(
        PortSpec(id = "in", direction = PortDirection.Input, type = PortType.Midi),
        PortSpec(id = "out", direction = PortDirection.Output, type = PortType.Midi),
    )

    @Test
    fun removingMiddleModuleOfThreeClearsItsConnectionsAndKeepsOthersActive() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)

        val a = FakeModule.withPorts("a", ioPorts)
        val b = FakeModule.withPorts("b", ioPorts)
        val c = FakeModule.withPorts("c", ioPorts)
        registry.load(a)
        registry.load(b)
        registry.load(c)

        assertIs<ConnectResult.Connected>(graph.connect("a", "out", "b", "in"))
        assertIs<ConnectResult.Connected>(graph.connect("b", "out", "c", "in"))
        assertEquals(2, graph.connections().size)

        graph.removeModule("b")

        assertTrue(graph.connections().isEmpty(), "removing b must clear both connections touching it")
        assertEquals(null, registry.get("b"))
        assertEquals(ModuleState.Active, registry.get("a")!!.state)
        assertEquals(ModuleState.Active, registry.get("c")!!.state)
        assertEquals(1, b.removeCount)
    }
}