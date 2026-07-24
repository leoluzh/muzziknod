package dev.muzziknod.modules.midisequencer

internal class SustainedNote(val note: NoteEvent, var stepsRemaining: Int)

internal class Transport {
    var isPlaying: Boolean = false
        private set
    var currentStep: Int = 0

    private val sustained = mutableListOf<SustainedNote>()
    private var pendingNoteOffFlush = false

    fun play() {
        isPlaying = true
    }

    fun stop() {
        if (!isPlaying) return
        isPlaying = false
        pendingNoteOffFlush = sustained.isNotEmpty()
    }

    fun sustain(note: NoteEvent) {
        sustained += SustainedNote(note, note.gateSteps)
    }

    /** Notes whose gate has expired this step (and are removed from the sustained set). */
    fun expireStep(): List<NoteEvent> {
        val expired = mutableListOf<NoteEvent>()
        val iterator = sustained.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.stepsRemaining -= 1
            if (entry.stepsRemaining <= 0) {
                expired += entry.note
                iterator.remove()
            }
        }
        return expired
    }

    /** All currently-sustained notes, flushed once on stop() (FR-007), then cleared. */
    fun flushPendingNoteOffs(): List<NoteEvent> {
        if (!pendingNoteOffFlush) return emptyList()
        pendingNoteOffFlush = false
        val notes = sustained.map { it.note }
        sustained.clear()
        return notes
    }

    fun hasPendingWork(): Boolean = isPlaying || pendingNoteOffFlush
}