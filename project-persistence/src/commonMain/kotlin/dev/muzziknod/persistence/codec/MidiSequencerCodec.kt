package dev.muzziknod.persistence.codec

import dev.muzziknod.host.contract.Module
import dev.muzziknod.modules.midisequencer.MidiSequencerModule
import dev.muzziknod.modules.midisequencer.NoteEvent
import dev.muzziknod.persistence.model.MidiSequencerData
import dev.muzziknod.persistence.model.ModuleSnapshot
import dev.muzziknod.persistence.model.NoteEventSnapshot
import dev.muzziknod.persistence.model.StepSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class MidiSequencerCodec : ModuleStateCodec {
    override val typeId: String = "midi-sequencer"

    override fun capture(module: Module): ModuleSnapshot {
        val sequencer = module as MidiSequencerModule
        val steps = (0 until sequencer.pattern.length)
            .map { index -> index to sequencer.pattern.step(index) }
            .filter { (_, step) -> step.notes.isNotEmpty() }
            .map { (index, step) ->
                StepSnapshot(
                    index = index,
                    notes = step.notes.map { NoteEventSnapshot(it.note, it.velocity, it.gateSteps) },
                )
            }
        val data = MidiSequencerData(bpm = sequencer.pattern.bpm, length = sequencer.pattern.length, steps = steps)
        return ModuleSnapshot(
            instanceId = sequencer.instanceId,
            typeId = typeId,
            moduleData = Json.encodeToJsonElement(data),
        )
    }

    override fun restore(instanceId: String, snapshot: ModuleSnapshot): Module {
        val sequencer = MidiSequencerModule(instanceId = instanceId)
        val data = snapshot.moduleData?.let { Json.decodeFromJsonElement<MidiSequencerData>(it) }
        if (data != null) {
            sequencer.setLength(data.length)
            sequencer.setBpm(data.bpm)
            for (step in data.steps) {
                sequencer.setStep(step.index, step.notes.map { NoteEvent(it.note, it.velocity, it.gateSteps) })
            }
        }
        return sequencer
    }
}
