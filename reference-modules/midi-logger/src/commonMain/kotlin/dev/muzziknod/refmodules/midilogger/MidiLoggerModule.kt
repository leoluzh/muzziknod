package dev.muzziknod.refmodules.midilogger

import dev.muzziknod.host.contract.MidiEvent
import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.ModuleContract
import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.contract.ProcessContext

private const val INPUT_PORT_ID = "in"
private const val OUTPUT_PORT_ID = "out"

/**
 * Reference module: records every MIDI event it receives (for test assertions) and
 * relays them unchanged on its own output — the passthrough/relay behavior is what lets
 * two instances of this one module type exercise the feedback-cycle rejection test
 * (US2 AC4) without needing a 4th reference module.
 */
class MidiLoggerModule(override val instanceId: String) : Module {
    override val contract = ModuleContract(
        typeId = "midi-logger",
        version = 1,
        ports = listOf(
            PortSpec(id = INPUT_PORT_ID, direction = PortDirection.Input, type = PortType.Midi),
            PortSpec(id = OUTPUT_PORT_ID, direction = PortDirection.Output, type = PortType.Midi),
        ),
    )

    private val _receivedEvents = mutableListOf<MidiEvent>()

    /** Every event received across all cycles so far, in arrival order — test-only API. */
    val receivedEvents: List<MidiEvent> get() = _receivedEvents

    override fun onLoad() {
        _receivedEvents.clear()
    }

    override fun process(context: ProcessContext) {
        val events = context.readMidi(INPUT_PORT_ID)
        _receivedEvents.addAll(events)
        context.writeMidi(OUTPUT_PORT_ID, events)
    }

    override fun onRemove() {
        // No resources to release for this reference module.
    }
}