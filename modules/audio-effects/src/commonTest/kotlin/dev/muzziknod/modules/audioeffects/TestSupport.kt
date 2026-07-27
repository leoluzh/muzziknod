package dev.muzziknod.modules.audioeffects

import dev.muzziknod.host.contract.AudioBuffer
import dev.muzziknod.host.contract.MidiEvent
import dev.muzziknod.host.contract.ProcessContext
import kotlin.math.sin

/** A non-trivial, non-periodic-in-buffer-length test signal for DSP behavioral tests. */
fun testSignal(size: Int = 128): FloatArray = FloatArray(size) { i -> (sin(i * 0.15) * 0.5).toFloat() }

/** Feeds a fixed [input] buffer to "in" and records whatever is written to "out". */
class FixedInputContext(private val input: FloatArray) : ProcessContext {
    var lastOutput: FloatArray = FloatArray(0)
        private set

    override fun readAudio(portId: String): AudioBuffer =
        if (portId == "in") AudioBuffer(input) else AudioBuffer(FloatArray(0))

    override fun writeAudio(portId: String, buffer: AudioBuffer) {
        if (portId == "out") lastOutput = buffer.samples.copyOf()
    }

    override fun readMidi(portId: String): List<MidiEvent> = emptyList()
    override fun writeMidi(portId: String, events: List<MidiEvent>) {}
}

/** No input connected: `readAudio` returns an empty buffer, per FR-014/contract Edge Cases. */
class EmptyInputContext : ProcessContext {
    var lastOutput: FloatArray = FloatArray(0)
        private set

    override fun readAudio(portId: String): AudioBuffer = AudioBuffer(FloatArray(0))

    override fun writeAudio(portId: String, buffer: AudioBuffer) {
        if (portId == "out") lastOutput = buffer.samples.copyOf()
    }

    override fun readMidi(portId: String): List<MidiEvent> = emptyList()
    override fun writeMidi(portId: String, events: List<MidiEvent>) {}
}
