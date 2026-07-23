package dev.muzziknod.host.contract

class AudioBuffer(val samples: FloatArray)

data class MidiEvent(
    val status: Int,
    val data1: Int,
    val data2: Int,
    val frameOffset: Int,
)