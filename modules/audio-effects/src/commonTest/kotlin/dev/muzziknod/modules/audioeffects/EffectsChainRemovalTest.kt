package dev.muzziknod.modules.audioeffects

import dev.muzziknod.host.graph.ConnectResult
import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.host.lifecycle.ModuleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Removing one effect module from an active chain does not auto-reconnect its
 * neighbors — same deferred-removal/no-reconnect behavior already proven by
 * `core-host` (FR-012; US3 AC2, mirrors 001-core-host FR-009).
 */
class EffectsChainRemovalTest {
    @Test
    fun removingMiddleEffectClearsItsConnectionsWithoutReconnectingNeighbors() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)

        val generator = FakeAudioGenerator("gen", testSignal())
        val eq = EqModule("eq")
        val distortion = DistortionModule("distortion")
        val delay = DelayModule("delay")
        val sink = FakeAudioSink("sink")

        for (module in listOf(generator, eq, distortion, delay, sink)) {
            registry.load(module)
        }

        assertIs<ConnectResult.Connected>(graph.connect("gen", "out", "eq", "in"))
        assertIs<ConnectResult.Connected>(graph.connect("eq", "out", "distortion", "in"))
        assertIs<ConnectResult.Connected>(graph.connect("distortion", "out", "delay", "in"))
        assertIs<ConnectResult.Connected>(graph.connect("delay", "out", "sink", "in"))
        assertEquals(4, graph.connections().size)

        graph.removeModule("distortion")

        assertEquals(null, registry.get("distortion"), "distortion must be gone from the registry")
        assertTrue(
            graph.connections().none { it.sourceInstanceId == "distortion" || it.targetInstanceId == "distortion" },
            "removing distortion must clear both connections touching it",
        )
        assertTrue(
            graph.connections().none { it.sourceInstanceId == "eq" && it.targetInstanceId == "delay" },
            "removal must not auto-reconnect eq directly to delay",
        )
        assertEquals(2, graph.connections().size, "only gen->eq and delay->sink should remain")
        assertEquals(ModuleState.Active, registry.get("eq")!!.state)
        assertEquals(ModuleState.Active, registry.get("delay")!!.state)

        // The rest of the graph keeps running; delay's "in" now has no incoming
        // connection, so it processes silence rather than eq's output.
        graph.processCycle()
        assertTrue(sink.received.isNotEmpty())
    }
}
