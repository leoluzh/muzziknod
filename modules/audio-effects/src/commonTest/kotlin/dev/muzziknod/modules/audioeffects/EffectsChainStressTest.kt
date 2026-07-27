package dev.muzziknod.modules.audioeffects

import dev.muzziknod.host.graph.ConnectResult
import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val STRESS_CYCLES = 20_000

/**
 * A 4-effect chain processing continuously for a large number of cycles — a proxy
 * for SC-003's 30-minute continuous-processing claim — must not throw and must keep
 * stable output levels (no runaway feedback).
 */
class EffectsChainStressTest {
    @Test
    fun longRunningChainStaysStableAndFinite() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)

        val generator = FakeAudioGenerator("gen", testSignal())
        val eq = EqModule("eq")
        val distortion = DistortionModule("distortion")
        val delay = DelayModule("delay")
        val reverb = ReverbModule("reverb")
        val sink = FakeAudioSink("sink")

        for (module in listOf(generator, eq, distortion, delay, reverb, sink)) {
            registry.load(module)
        }
        assertIs<ConnectResult.Connected>(graph.connect("gen", "out", "eq", "in"))
        assertIs<ConnectResult.Connected>(graph.connect("eq", "out", "distortion", "in"))
        assertIs<ConnectResult.Connected>(graph.connect("distortion", "out", "delay", "in"))
        assertIs<ConnectResult.Connected>(graph.connect("delay", "out", "reverb", "in"))
        assertIs<ConnectResult.Connected>(graph.connect("reverb", "out", "sink", "in"))

        repeat(STRESS_CYCLES) { cycle ->
            // Wiggle parameters throughout the run, same as a live-performance scenario.
            if (cycle % 500 == 0) {
                delay.setDelayTimeMs(200.0 + (cycle % 1000))
                delay.setFeedback(0.6)
                reverb.setDecayMs(1000.0 + (cycle % 3000))
                reverb.setRoomSize(0.7)
                distortion.setDrive(8.0)
                eq.setBandGain(EqBand.Mid, 6.0)
            }
            graph.processCycle()
        }

        assertTrue(sink.received.size == STRESS_CYCLES)
        val last = sink.received.last()
        for (sample in last) {
            assertTrue(sample.isFinite(), "output must stay finite after $STRESS_CYCLES cycles (no runaway feedback)")
            assertTrue(kotlin.math.abs(sample) < 10f, "output must stay within a stable level, got $sample")
        }
    }
}
