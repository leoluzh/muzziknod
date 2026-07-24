package dev.muzziknod.modules.midisequencer

import dev.muzziknod.host.contract.AudioBuffer
import dev.muzziknod.host.contract.MidiEvent
import dev.muzziknod.host.contract.ProcessContext

/** Records every `writeMidi` call per cycle, in order, for direct single-module tests. */
class CapturingProcessContext : ProcessContext {
    val midiWrites: MutableList<Pair<String, List<MidiEvent>>> = mutableListOf()

    override fun readAudio(portId: String): AudioBuffer = AudioBuffer(FloatArray(0))
    override fun writeAudio(portId: String, buffer: AudioBuffer) {}
    override fun readMidi(portId: String): List<MidiEvent> = emptyList()

    override fun writeMidi(portId: String, events: List<MidiEvent>) {
        midiWrites += portId to events
    }
}

fun noteOnEvent(note: Int, velocity: Int) = MidiEvent(status = 0x90, data1 = note, data2 = velocity, frameOffset = 0)
fun noteOffEvent(note: Int) = MidiEvent(status = 0x80, data1 = note, data2 = 0, frameOffset = 0)