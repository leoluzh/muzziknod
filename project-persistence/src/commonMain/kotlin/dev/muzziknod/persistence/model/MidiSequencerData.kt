package dev.muzziknod.persistence.model

import kotlinx.serialization.Serializable

/** `ModuleSnapshot.moduleData` payload for `typeId = "midi-sequencer"`. */
@Serializable
data class MidiSequencerData(
    val bpm: Int,
    val length: Int,
    val steps: List<StepSnapshot>,
)

/** Only non-empty steps are included — matches `Pattern`'s own sparse storage. */
@Serializable
data class StepSnapshot(
    val index: Int,
    val notes: List<NoteEventSnapshot>,
)

@Serializable
data class NoteEventSnapshot(
    val note: Int,
    val velocity: Int,
    val gateSteps: Int = 1,
)
