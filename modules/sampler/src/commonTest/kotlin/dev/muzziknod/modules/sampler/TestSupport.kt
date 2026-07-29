package dev.muzziknod.modules.sampler

import dev.muzziknod.host.contract.AudioBuffer
import dev.muzziknod.host.contract.MidiEvent
import dev.muzziknod.host.contract.ProcessContext
import kotlin.math.PI
import kotlin.math.sin

private const val IN_PORT = "in"
private const val OUT_PORT = "out"
private const val NOTE_ON = 0x90
private const val NOTE_OFF = 0x80

class FakeProcessContext(private val midiIn: List<MidiEvent> = emptyList()) : ProcessContext {
    var lastWritten: AudioBuffer? = null
        private set

    override fun readAudio(portId: String): AudioBuffer = AudioBuffer(FloatArray(0))

    override fun writeAudio(portId: String, buffer: AudioBuffer) {
        lastWritten = buffer
    }

    override fun readMidi(portId: String): List<MidiEvent> = if (portId == IN_PORT) midiIn else emptyList()

    override fun writeMidi(portId: String, events: List<MidiEvent>) {}
}

fun noteOn(note: Int, velocity: Int) = MidiEvent(status = NOTE_ON, data1 = note, data2 = velocity, frameOffset = 0)
fun noteOff(note: Int) = MidiEvent(status = NOTE_OFF, data1 = note, data2 = 0, frameOffset = 0)

/** A single-cycle sine, useful for period/pitch-ratio assertions. */
fun sineData(length: Int, cycles: Double = 1.0): FloatArray =
    FloatArray(length) { i -> sin(2.0 * PI * cycles * i / length).toFloat() }

fun rampData(length: Int): FloatArray = FloatArray(length) { i -> (i + 1) / length.toFloat() }
