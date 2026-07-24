package dev.muzziknod.modules.midisequencer

import dev.muzziknod.host.contract.MidiEvent
import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.ModuleContract
import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.contract.ProcessContext

/**
 * Product module (not scaffolding — spec 002-midi-sequencer). Satisfies core-host's
 * [Module] contract unchanged; pattern/transport control is a plain Kotlin API beyond
 * that interface (see contracts/midi-sequencer-api.md).
 */
class MidiSequencerModule(override val instanceId: String) : Module {

    override val contract: ModuleContract = ModuleContract(
        typeId = "midi-sequencer",
        version = 1,
        ports = listOf(
            PortSpec(id = OUTPUT_PORT_ID, direction = PortDirection.Output, type = PortType.Midi),
        ),
    )

    val pattern: Pattern = Pattern()
    private val transport = Transport()

    val isPlaying: Boolean get() = transport.isPlaying
    val currentStep: Int get() = transport.currentStep

    fun setStep(index: Int, notes: List<NoteEvent>) = pattern.setStep(index, notes)

    fun clearStep(index: Int) = pattern.clearStep(index)

    fun setBpm(bpm: Int) = pattern.setBpm(bpm)

    /** Wraps the current playback position into range immediately if it falls outside the new length (FR-010). */
    fun setLength(steps: Int) {
        val wrapped = pattern.setLength(steps, transport.currentStep)
        if (wrapped != null) transport.currentStep = wrapped
    }

    fun play() = transport.play()

    fun stop() = transport.stop()

    override fun onLoad() {}

    override fun process(context: ProcessContext) {
        if (!transport.hasPendingWork()) return

        val events = mutableListOf<MidiEvent>()
        events += transport.flushPendingNoteOffs().map(::noteOff)

        if (transport.isPlaying) {
            events += transport.expireStep().map(::noteOff)
            val step = pattern.step(transport.currentStep)
            for (note in step.notes) {
                events += noteOn(note)
                transport.sustain(note)
            }
            transport.currentStep = (transport.currentStep + 1) % pattern.length
        }

        context.writeMidi(OUTPUT_PORT_ID, events)
    }

    override fun onRemove() {}

    private companion object {
        const val OUTPUT_PORT_ID = "out"
        const val NOTE_ON_STATUS = 0x90
        const val NOTE_OFF_STATUS = 0x80

        fun noteOn(note: NoteEvent) =
            MidiEvent(status = NOTE_ON_STATUS, data1 = note.note, data2 = note.velocity, frameOffset = 0)

        fun noteOff(note: NoteEvent) =
            MidiEvent(status = NOTE_OFF_STATUS, data1 = note.note, data2 = 0, frameOffset = 0)
    }
}