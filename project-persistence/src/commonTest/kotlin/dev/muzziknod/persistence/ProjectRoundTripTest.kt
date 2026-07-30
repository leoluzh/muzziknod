package dev.muzziknod.persistence

import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.host.transport.Transport
import dev.muzziknod.modules.audioeffects.DelayModule
import dev.muzziknod.modules.audioeffects.DistortionModule
import dev.muzziknod.modules.audioeffects.EqBand
import dev.muzziknod.modules.audioeffects.EqModule
import dev.muzziknod.modules.audioeffects.ReverbModule
import dev.muzziknod.modules.midisequencer.MidiSequencerModule
import dev.muzziknod.modules.midisequencer.NoteEvent
import dev.muzziknod.persistence.model.ProjectSnapshot
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Full save -> load round trip across every codec-backed module type (sampler excluded
 * here — it needs real files, covered separately in jvmTest's file-based round trip and
 * `SamplerCodecTest`). US1 + US2; FR-001-004, FR-007; SC-001, SC-002.
 */
class ProjectRoundTripTest {

    @Test
    fun roundTripsGraphParametersAndTransportExactly() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)
        val transport = Transport()
        val catalog = defaultProjectPersistenceCatalog()

        val delay = DelayModule(instanceId = "delay-1")
        val reverb = ReverbModule(instanceId = "reverb-1")
        val distortion = DistortionModule(instanceId = "distortion-1")
        val eq = EqModule(instanceId = "eq-1")
        val sequencer = MidiSequencerModule(instanceId = "seq-1")

        registry.load(delay)
        registry.load(reverb)
        registry.load(distortion)
        registry.load(eq)
        registry.load(sequencer)

        delay.setMix(0.75)
        delay.setDelayTimeMs(500.0)
        delay.setFeedback(0.4)
        reverb.setMix(0.6)
        reverb.setDecayMs(2000.0)
        reverb.setRoomSize(0.8)
        distortion.setMix(0.9)
        distortion.setDrive(10.0)
        distortion.setTone(3000.0)
        eq.setBandFrequency(EqBand.Low, 80.0)
        eq.setBandGain(EqBand.Low, -3.0)
        eq.setBandQ(EqBand.High, 2.5)
        sequencer.setLength(8)
        sequencer.setBpm(140)
        sequencer.setStep(0, listOf(NoteEvent(note = 60, velocity = 100, gateSteps = 2)))
        sequencer.setStep(3, listOf(NoteEvent(note = 64, velocity = 90)))

        graph.connect("delay-1", "out", "reverb-1", "in")
        graph.connect("reverb-1", "out", "distortion-1", "in")
        graph.connect("distortion-1", "out", "eq-1", "in")

        transport.setTempo(128.0)
        transport.setPosition(16.0)
        transport.setLoopRange(4.0, 12.0)
        transport.play()

        // One cycle so each module's smoothed parameter StateFlows converge to the set targets
        // (ParameterSmoother's default ramp is 64 samples, well under one 128-sample buffer).
        graph.processCycle()

        val writer = ProjectWriter(registry, graph, transport, catalog)
        val content = Json.encodeToString(ProjectSnapshot.serializer(), writer.buildSnapshot())

        val freshRegistry = ModuleRegistry()
        val freshGraph = RoutingGraph(freshRegistry)
        val freshTransport = Transport()
        val reader = ProjectReader(freshRegistry, freshGraph, freshTransport, catalog)
        val result = reader.loadFromContent(content)

        assertTrue(result.warnings.isEmpty(), "expected no warnings, got ${result.warnings}")

        // Restored modules start with their setter targets applied but their StateFlow
        // mirrors still at construction defaults until the first process() cycle syncs
        // them — same as any freshly-added module (data-model.md "Mapping to live host
        // types"; ParameterSmoother ramps over <=64 samples, well under one cycle).
        freshGraph.processCycle()

        val restoredDelay = freshRegistry.get("delay-1")!!.module as DelayModule
        assertEquals(0.75, restoredDelay.mix.value, 1e-6)
        assertEquals(500.0, restoredDelay.delayTimeMs.value, 1e-6)
        assertEquals(0.4, restoredDelay.feedback.value, 1e-6)

        val restoredReverb = freshRegistry.get("reverb-1")!!.module as ReverbModule
        assertEquals(0.6, restoredReverb.mix.value, 1e-6)
        assertEquals(2000.0, restoredReverb.decayMs.value, 1e-6)
        assertEquals(0.8, restoredReverb.roomSize.value, 1e-6)

        val restoredDistortion = freshRegistry.get("distortion-1")!!.module as DistortionModule
        assertEquals(0.9, restoredDistortion.mix.value, 1e-6)
        assertEquals(10.0, restoredDistortion.drive.value, 1e-6)
        assertEquals(3000.0, restoredDistortion.tone.value, 1e-6)

        val restoredEq = freshRegistry.get("eq-1")!!.module as EqModule
        assertEquals(80.0, restoredEq.bandFrequency(EqBand.Low).value, 1e-6)
        assertEquals(-3.0, restoredEq.bandGain(EqBand.Low).value, 1e-6)
        assertEquals(2.5, restoredEq.bandQ(EqBand.High).value, 1e-6)

        val restoredSequencer = freshRegistry.get("seq-1")!!.module as MidiSequencerModule
        assertEquals(140, restoredSequencer.pattern.bpm)
        assertEquals(8, restoredSequencer.pattern.length)
        assertEquals(listOf(NoteEvent(60, 100, 2)), restoredSequencer.pattern.step(0).notes)
        assertEquals(listOf(NoteEvent(64, 90)), restoredSequencer.pattern.step(3).notes)

        val connectionPairs = freshGraph.connections().map { it.sourceInstanceId to it.targetInstanceId }.toSet()
        assertEquals(
            setOf("delay-1" to "reverb-1", "reverb-1" to "distortion-1", "distortion-1" to "eq-1"),
            connectionPairs,
        )

        val restoredTransport = freshTransport.state.value
        assertEquals(128.0, restoredTransport.tempoBpm, 1e-6)
        assertEquals(16.0, restoredTransport.positionBeats, 1e-6)
        assertEquals(4.0, restoredTransport.loopStart)
        assertEquals(12.0, restoredTransport.loopEnd)
        assertTrue(restoredTransport.isPlaying)
    }
}
