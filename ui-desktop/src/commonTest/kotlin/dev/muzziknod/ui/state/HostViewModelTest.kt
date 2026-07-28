package dev.muzziknod.ui.state

import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.refmodules.midigenerator.MidiGeneratorModule
import dev.muzziknod.refmodules.midilogger.MidiLoggerModule
import dev.muzziknod.refmodules.oscillator.OscillatorModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HostViewModelTest {
    @Test
    fun uiStateReflectsALoadMadeDirectlyOnTheUnderlyingRegistry() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)
        val viewModel = HostViewModel(registry, graph, CoroutineScope(Dispatchers.Unconfined))

        assertTrue(viewModel.uiState.value.modules.isEmpty())

        registry.load(OscillatorModule(instanceId = "osc-1"))

        assertEquals(1, viewModel.uiState.value.modules.size)
        assertEquals("osc-1", viewModel.uiState.value.modules.first().instanceId)
        assertEquals("oscillator", viewModel.uiState.value.modules.first().typeName)
    }

    @Test
    fun uiStateReflectsAConnectMadeDirectlyOnTheUnderlyingGraph() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)
        val viewModel = HostViewModel(registry, graph, CoroutineScope(Dispatchers.Unconfined))
        registry.load(MidiGeneratorModule(instanceId = "gen-1"))
        registry.load(MidiLoggerModule(instanceId = "log-1"))

        assertTrue(viewModel.uiState.value.connections.isEmpty())

        val result = graph.connect("gen-1", "out", "log-1", "in")

        assertIs<dev.muzziknod.host.graph.ConnectResult.Connected>(result)
        assertEquals(graph.connections().toList(), viewModel.uiState.value.connections)
        assertEquals(1, viewModel.uiState.value.connections.size)
    }
}
