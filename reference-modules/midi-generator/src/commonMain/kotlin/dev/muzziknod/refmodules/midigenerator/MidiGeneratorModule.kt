package dev.muzziknod.refmodules.midigenerator

import dev.muzziknod.host.contract.MidiEvent
import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.ModuleContract
import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.contract.ProcessContext

private const val OUTPUT_PORT_ID = "out"

/**
 * Reference module: emits a fixed MIDI event sequence every cycle. Source-only (no
 * input port) — used as the upstream half of US2's connect/disconnect scenarios.
 */
class MidiGeneratorModule(override val instanceId: String) : Module {
    override val contract = ModuleContract(
        typeId = "midi-generator",
        version = 1,
        ports = listOf(
            PortSpec(id = OUTPUT_PORT_ID, direction = PortDirection.Output, type = PortType.Midi),
        ),
    )

    private var cycleCount = 0

    // Fixed-size, reused every cycle instead of reallocated — Constitution III forbids
    // allocation on the processing hot path.
    private val emittedEvents = mutableListOf(
        MidiEvent(status = 0x90, data1 = 60, data2 = 100, frameOffset = 0),
        MidiEvent(status = 0x80, data1 = 60, data2 = 0, frameOffset = 64),
    )

    override fun onLoad() {
        cycleCount = 0
    }

    override fun process(context: ProcessContext) {
        val noteNumber = 60 + (cycleCount % 12)
        emittedEvents[0] = emittedEvents[0].copy(data1 = noteNumber)
        emittedEvents[1] = emittedEvents[1].copy(data1 = noteNumber)
        context.writeMidi(OUTPUT_PORT_ID, emittedEvents)
        cycleCount++
    }

    override fun onRemove() {
        // No resources to release for this reference module.
    }
}