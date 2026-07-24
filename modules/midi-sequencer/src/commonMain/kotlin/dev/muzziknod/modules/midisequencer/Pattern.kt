package dev.muzziknod.modules.midisequencer

data class NoteEvent(
    val note: Int,
    val velocity: Int,
    val gateSteps: Int = 1,
)

data class Step(
    val index: Int,
    val notes: List<NoteEvent> = emptyList(),
)

class Pattern(length: Int = DEFAULT_LENGTH, bpm: Int = DEFAULT_BPM) {
    var length: Int = length.coerceAtLeast(1)
        private set
    var bpm: Int = bpm.coerceIn(MIN_BPM, MAX_BPM)
        private set

    private val stepsByIndex = mutableMapOf<Int, Step>()

    fun step(index: Int): Step = stepsByIndex[index] ?: Step(index)

    fun setStep(index: Int, notes: List<NoteEvent>) {
        require(index in 0 until length) { "Step index $index out of range [0, $length)" }
        stepsByIndex[index] = Step(index, notes)
    }

    fun clearStep(index: Int) {
        stepsByIndex.remove(index)
    }

    fun setBpm(newBpm: Int) {
        bpm = newBpm.coerceIn(MIN_BPM, MAX_BPM)
    }

    /** Returns the new valid current-step position if [currentStep] is out of range, else null. */
    fun setLength(newLength: Int, currentStep: Int): Int? {
        length = newLength.coerceAtLeast(1)
        stepsByIndex.keys.filter { it >= length }.forEach { stepsByIndex.remove(it) }
        return if (currentStep >= length) currentStep % length else null
    }

    private companion object {
        const val DEFAULT_LENGTH = 16
        const val DEFAULT_BPM = 120
        const val MIN_BPM = 20
        const val MAX_BPM = 300
    }
}