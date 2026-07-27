package dev.muzziknod.modules.audioeffects

import dev.muzziknod.host.contract.AudioBuffer
import dev.muzziknod.host.contract.BufferFormat
import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.ModuleContract
import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.contract.ProcessContext

/**
 * Emits the same fixed [signal] every cycle. Local `Module` test double — mirrors
 * `midi-sequencer`'s `FakeMidiSink` pattern (can't depend on another module's test
 * source set under Gradle/KMP).
 */
class FakeAudioGenerator(override val instanceId: String, private val signal: FloatArray) : Module {
    override val contract: ModuleContract = ModuleContract(
        typeId = "fake-audio-generator",
        version = 1,
        ports = listOf(
            PortSpec(id = "out", direction = PortDirection.Output, type = PortType.Audio, sampleRate = 48_000, bufferFormat = BufferFormat.Float32),
        ),
    )

    private val buffer = AudioBuffer(signal)

    override fun onLoad() {}

    override fun process(context: ProcessContext) {
        context.writeAudio("out", buffer)
    }

    override fun onRemove() {}
}

/** Records every buffer it receives on its input port, cycle by cycle. */
class FakeAudioSink(override val instanceId: String) : Module {
    override val contract: ModuleContract = ModuleContract(
        typeId = "fake-audio-sink",
        version = 1,
        ports = listOf(
            PortSpec(id = "in", direction = PortDirection.Input, type = PortType.Audio, sampleRate = 48_000, bufferFormat = BufferFormat.Float32),
        ),
    )

    val received: MutableList<FloatArray> = mutableListOf()

    override fun onLoad() {}

    override fun process(context: ProcessContext) {
        received += context.readAudio("in").samples.copyOf()
    }

    override fun onRemove() {}
}
