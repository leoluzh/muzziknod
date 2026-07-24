package dev.muzziknod.modules.midisequencer

import dev.muzziknod.host.contract.MidiEvent
import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.ModuleContract
import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.contract.ProcessContext

/**
 * Local `Module` test double recording every MIDI event it receives on its input port.
 * Mirrors core-host's own `FakeModule` pattern — this module can't depend on
 * `core-host`'s or `reference-modules`' test source sets (Gradle/KMP limitation, and
 * wrong dependency direction for the latter).
 */
class FakeMidiSink(override val instanceId: String) : Module {
    override val contract: ModuleContract = ModuleContract(
        typeId = "fake-midi-sink",
        version = 1,
        ports = listOf(
            PortSpec(id = "in", direction = PortDirection.Input, type = PortType.Midi),
        ),
    )

    val received: MutableList<MidiEvent> = mutableListOf()

    override fun onLoad() {}

    override fun process(context: ProcessContext) {
        received += context.readMidi("in")
    }

    override fun onRemove() {}
}