package dev.muzziknod.host.contract.testkit

import dev.muzziknod.host.contract.AudioBuffer
import dev.muzziknod.host.contract.MidiEvent
import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.ProcessContext
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Records which declared ports a [Module] actually touches during one [Module.process]
 * call, so [ModuleContractComplianceTests] can check that against the module's declared
 * [dev.muzziknod.host.contract.PortSpec] list.
 */
private class RecordingProcessContext : ProcessContext {
    val readPortIds = mutableSetOf<String>()
    val writtenPortIds = mutableSetOf<String>()

    override fun readAudio(portId: String): AudioBuffer {
        readPortIds += portId
        return AudioBuffer(FloatArray(0))
    }

    override fun writeAudio(portId: String, buffer: AudioBuffer) {
        writtenPortIds += portId
    }

    override fun readMidi(portId: String): List<MidiEvent> {
        readPortIds += portId
        return emptyList()
    }

    override fun writeMidi(portId: String, events: List<MidiEvent>) {
        writtenPortIds += portId
    }
}

/**
 * Reusable module-contract compliance suite (`contracts/module-contract.md`). Reference
 * modules subclass this in their own `commonTest` and implement [createModule] — Gradle/
 * KMP has no way for one module's test source set to depend on another's, so this suite
 * lives in `core-host`'s `commonMain` instead of `commonTest`.
 *
 * Host-level guarantees (fault isolation, deferred removal) are graph/registry behavior,
 * not something a single module instance can assert about itself — those are covered by
 * `RoutingGraphFaultIsolationTest` and `ModuleRemovalDeferralTest` instead.
 */
abstract class ModuleContractComplianceTests {
    abstract fun createModule(): Module

    @Test
    fun declaredPortIdsAreUnique() {
        val module = createModule()
        val duplicateIds = module.contract.ports
            .groupingBy { it.id }
            .eachCount()
            .filterValues { it > 1 }
        assertTrue(duplicateIds.isEmpty(), "Duplicate port ids declared: ${duplicateIds.keys}")
    }

    @Test
    fun onLoadThenProcessDoesNotThrow() {
        val module = createModule()
        module.onLoad()
        module.process(RecordingProcessContext())
    }

    @Test
    fun processOnlyTouchesDeclaredPorts() {
        val module = createModule()
        module.onLoad()
        val context = RecordingProcessContext()
        module.process(context)

        val declaredInputIds = module.contract.ports
            .filter { it.direction == PortDirection.Input }
            .map { it.id }
            .toSet()
        val declaredOutputIds = module.contract.ports
            .filter { it.direction == PortDirection.Output }
            .map { it.id }
            .toSet()

        for (portId in context.readPortIds) {
            if (portId !in declaredInputIds) {
                fail("process() read undeclared or non-input port '$portId'")
            }
        }
        for (portId in context.writtenPortIds) {
            if (portId !in declaredOutputIds) {
                fail("process() wrote undeclared or non-output port '$portId'")
            }
        }
    }

    @Test
    fun onRemoveAfterProcessDoesNotThrow() {
        val module = createModule()
        module.onLoad()
        module.process(RecordingProcessContext())
        module.onRemove()
    }
}