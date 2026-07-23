package dev.muzziknod.host.graph

import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.lifecycle.FakeModule
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.host.lifecycle.ModuleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** SC-004: a graph of >=5 connected modules runs repeated cycles with no ordering errors or crashes. */
class GraphOrderingStressTest {
    @Test
    fun fiveModuleChainProcessesRepeatedlyWithoutOrderingErrorsOrCrashes() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)
        val ioPorts = listOf(
            PortSpec(id = "in", direction = PortDirection.Input, type = PortType.Midi),
            PortSpec(id = "out", direction = PortDirection.Output, type = PortType.Midi),
        )

        val processedThisCycleInOrder = mutableListOf<String>()
        val modules = (0 until 7).map { index ->
            FakeModule.withPorts("m$index", ioPorts).apply {
                onProcessAction = { processedThisCycleInOrder += instanceId }
            }
        }
        modules.forEach { registry.load(it) }
        for (i in 0 until modules.size - 1) {
            assertIs<ConnectResult.Connected>(graph.connect("m$i", "out", "m${i + 1}", "in"))
        }

        val cycles = 50
        repeat(cycles) {
            processedThisCycleInOrder.clear()
            graph.processCycle()

            assertEquals(modules.map { it.instanceId }, processedThisCycleInOrder, "must process in chain order every cycle")
        }

        modules.forEach { module ->
            assertEquals(cycles, module.processCount)
            assertEquals(ModuleState.Active, registry.get(module.instanceId)!!.state)
        }
    }
}