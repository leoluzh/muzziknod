package dev.muzziknod.modules.audioeffects

import dev.muzziknod.host.graph.ConnectResult
import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.LoadResult
import dev.muzziknod.host.lifecycle.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A generator + all four effect types + a sink, chained through `core-host`'s real
 * `RoutingGraph`/`ModuleRegistry`, must deliver the fully processed signal to the sink
 * every cycle, in connection order (FR-001, FR-011, FR-012; SC-004; US3 AC1).
 */
class EffectsChainRoutingTest {
    private fun directChain(
        input: FloatArray,
        eq: EqModule,
        distortion: DistortionModule,
        delay: DelayModule,
        reverb: ReverbModule,
    ): FloatArray {
        var buffer = input
        for (module in listOf<(FloatArray) -> FloatArray>(
            { b -> FixedInputContext(b).also { eq.process(it) }.lastOutput },
            { b -> FixedInputContext(b).also { distortion.process(it) }.lastOutput },
            { b -> FixedInputContext(b).also { delay.process(it) }.lastOutput },
            { b -> FixedInputContext(b).also { reverb.process(it) }.lastOutput },
        )) {
            buffer = module(buffer)
        }
        return buffer
    }

    @Test
    fun sinkReceivesFullyProcessedSignalInConnectionOrder() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)

        val signal = testSignal()
        val generator = FakeAudioGenerator("gen", signal)
        val eqRouted = EqModule("eq-routed")
        val distortionRouted = DistortionModule("distortion-routed")
        val delayRouted = DelayModule("delay-routed")
        val reverbRouted = ReverbModule("reverb-routed")
        val sink = FakeAudioSink("sink")

        for (module in listOf(generator, eqRouted, distortionRouted, delayRouted, reverbRouted, sink)) {
            assertIs<LoadResult.Loaded>(registry.load(module))
        }

        assertIs<ConnectResult.Connected>(graph.connect("gen", "out", "eq-routed", "in"))
        assertIs<ConnectResult.Connected>(graph.connect("eq-routed", "out", "distortion-routed", "in"))
        assertIs<ConnectResult.Connected>(graph.connect("distortion-routed", "out", "delay-routed", "in"))
        assertIs<ConnectResult.Connected>(graph.connect("delay-routed", "out", "reverb-routed", "in"))
        assertIs<ConnectResult.Connected>(graph.connect("reverb-routed", "out", "sink", "in"))

        // Identical chain, driven directly (not through the graph), for comparison.
        val eqDirect = EqModule("eq-direct").also { it.onLoad() }
        val distortionDirect = DistortionModule("distortion-direct").also { it.onLoad() }
        val delayDirect = DelayModule("delay-direct").also { it.onLoad() }
        val reverbDirect = ReverbModule("reverb-direct").also { it.onLoad() }

        val expected = mutableListOf<FloatArray>()
        repeat(10) {
            graph.processCycle()
            expected += directChain(signal, eqDirect, distortionDirect, delayDirect, reverbDirect)
        }

        assertEquals(expected.size, sink.received.size)
        for (i in expected.indices) {
            assertContentEquals(expected[i], sink.received[i], "cycle $i must match the same chain driven directly")
        }
    }
}
