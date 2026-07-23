package dev.muzziknod.refmodules.oscillator

import dev.muzziknod.host.contract.AudioBuffer
import dev.muzziknod.host.contract.BufferFormat
import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.ModuleContract
import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.contract.ProcessContext
import kotlin.math.PI
import kotlin.math.sin

private const val OUTPUT_PORT_ID = "out"
private const val BUFFER_SIZE = 128

/**
 * Reference module: emits a sine wave on its single audio output. No native DSP — pure
 * Kotlin per spec Assumptions, just enough to exercise host load/route/remove behavior.
 */
class OscillatorModule(
    override val instanceId: String,
    private val frequencyHz: Double = 440.0,
    private val sampleRate: Int = 48_000,
) : Module {
    override val contract = ModuleContract(
        typeId = "oscillator",
        version = 1,
        ports = listOf(
            PortSpec(
                id = OUTPUT_PORT_ID,
                direction = PortDirection.Output,
                type = PortType.Audio,
                sampleRate = sampleRate,
                bufferFormat = BufferFormat.Float32,
            ),
        ),
    )

    private var phase = 0.0

    // Both the backing array and its AudioBuffer wrapper are allocated once in onLoad()
    // and mutated/reused every process() call — Constitution III forbids allocation on
    // the processing hot path, even for a pure-Kotlin reference module with no real
    // audio device behind it yet.
    private lateinit var outputBuffer: AudioBuffer

    override fun onLoad() {
        phase = 0.0
        outputBuffer = AudioBuffer(FloatArray(BUFFER_SIZE))
    }

    override fun process(context: ProcessContext) {
        val phaseIncrement = 2.0 * PI * frequencyHz / sampleRate
        val samples = outputBuffer.samples
        for (i in samples.indices) {
            samples[i] = sin(phase).toFloat()
            phase += phaseIncrement
        }
        context.writeAudio(OUTPUT_PORT_ID, outputBuffer)
    }

    override fun onRemove() {
        // No resources to release for this reference module.
    }
}
