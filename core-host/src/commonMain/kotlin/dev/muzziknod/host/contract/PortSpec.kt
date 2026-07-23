package dev.muzziknod.host.contract

enum class PortDirection { Input, Output }

enum class PortType { Audio, Midi }

enum class BufferFormat { Float32, Int16 }

data class PortSpec(
    val id: String,
    val direction: PortDirection,
    val type: PortType,
    val sampleRate: Int? = null,
    val bufferFormat: BufferFormat? = null,
)