package dev.muzziknod.ui.state

import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.ModuleContract
import dev.muzziknod.host.contract.ProcessContext
import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.refmodules.oscillator.OscillatorModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Runs an arbitrary [onProcess] callback each cycle — used to call back into the
 * host/viewModel from *inside* an active [RoutingGraph.processCycle], so a test can
 * observe state while a cycle is genuinely in progress. */
private class TriggerModule(override val instanceId: String, private val onProcess: () -> Unit) : Module {
    override val contract = ModuleContract(typeId = "trigger", version = 1, ports = emptyList())
    override fun onLoad() {}
    override fun process(context: ProcessContext) = onProcess()
    override fun onRemove() {}
}

class HostViewModelRemovalTest {
    @Test
    fun immediateRemovalDropsTheModuleFromUiStateEntirely() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)
        val viewModel = HostViewModel(registry, graph, CoroutineScope(Dispatchers.Unconfined))
        registry.load(OscillatorModule(instanceId = "osc-1"))
        assertEquals(1, viewModel.uiState.value.modules.size)

        // No cycle is in progress, so RoutingGraph.removeModule() removes
        // immediately — the instance simply disappears from uiState.
        viewModel.removeModule("osc-1")

        assertEquals(0, viewModel.uiState.value.modules.size)
    }

    @Test
    fun deferredRemovalKeepsPendingRemovalTrueUntilTheCycleFinishes() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)
        val viewModel = HostViewModel(registry, graph, CoroutineScope(Dispatchers.Unconfined))
        registry.load(OscillatorModule(instanceId = "target"))
        var pendingDuringCycle: Boolean? = null
        registry.load(
            TriggerModule("trigger") {
                viewModel.removeModule("target")
                pendingDuringCycle = viewModel.uiState.value.modules.find { it.instanceId == "target" }?.pendingRemoval
            },
        )

        // A cycle is in progress here — RoutingGraph.removeModule() defers "target"'s
        // removal (FR-010), so during the cycle it must still be present with
        // pendingRemoval = true (FR-015).
        graph.processCycle()

        assertEquals(true, pendingDuringCycle, "target must still be reported with pendingRemoval=true while the cycle is in progress")
        // The deferred removal runs right after the cycle returns — by now it's gone entirely.
        assertFalse(viewModel.uiState.value.modules.any { it.instanceId == "target" })
    }
}
