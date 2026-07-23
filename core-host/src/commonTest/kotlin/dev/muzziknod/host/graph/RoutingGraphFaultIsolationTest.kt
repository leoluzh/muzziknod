package dev.muzziknod.host.graph

import dev.muzziknod.host.lifecycle.FakeModule
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.host.lifecycle.ModuleState
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutingGraphFaultIsolationTest {
    @Test
    fun moduleThatThrowsIsFaultedAndExcludedFromLaterCyclesWhileRestKeepsRunning() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)

        val faulty = FakeModule.withPorts("faulty", emptyList()).apply { throwOnProcess = true }
        val healthy = FakeModule.withPorts("healthy", emptyList())
        registry.load(faulty)
        registry.load(healthy)

        graph.processCycle()
        assertEquals(ModuleState.Faulted, registry.get("faulty")!!.state)
        assertEquals(1, healthy.processCount)
        assertEquals(1, faulty.processCount)

        graph.processCycle()
        assertEquals(1, faulty.processCount, "faulted module must not be processed again")
        assertEquals(2, healthy.processCount, "rest of the graph keeps running across cycles")
    }
}