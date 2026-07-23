package dev.muzziknod.host.graph

import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.lifecycle.FakeModule
import dev.muzziknod.host.lifecycle.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertIs

class RoutingGraphCycleRejectionTest {
    @Test
    fun closingATwoNodeCycleIsRejected() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)

        val ports = listOf(
            PortSpec(id = "in", direction = PortDirection.Input, type = PortType.Midi),
            PortSpec(id = "out", direction = PortDirection.Output, type = PortType.Midi),
        )
        val l1 = FakeModule.withPorts("l1", ports)
        val l2 = FakeModule.withPorts("l2", ports)
        registry.load(l1)
        registry.load(l2)

        val forward = graph.connect("l1", "out", "l2", "in")
        assertIs<ConnectResult.Connected>(forward)

        val backward = graph.connect("l2", "out", "l1", "in")
        assertIs<ConnectResult.Rejected>(backward)
    }
}