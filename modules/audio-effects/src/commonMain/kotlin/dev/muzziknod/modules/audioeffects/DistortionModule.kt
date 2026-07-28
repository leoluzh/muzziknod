package dev.muzziknod.modules.audioeffects

import dev.muzziknod.host.contract.AudioBuffer
import dev.muzziknod.host.contract.BufferFormat
import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.ModuleContract
import dev.muzziknod.host.contract.ParameterSpec
import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.contract.ProcessContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.tanh

private const val INPUT_PORT_ID = "in"
private const val OUTPUT_PORT_ID = "out"
private const val BUFFER_SIZE = 128

/**
 * Tanh soft-clip waveshaper followed by a one-pole lowpass tone filter
 * (research.md "Per-effect algorithm choice").
 */
class DistortionModule(
    override val instanceId: String,
    private val sampleRate: Int = 48_000,
) : Module {
    override val contract: ModuleContract = ModuleContract(
        typeId = "distortion",
        version = 1,
        ports = listOf(
            PortSpec(
                id = INPUT_PORT_ID,
                direction = PortDirection.Input,
                type = PortType.Audio,
                sampleRate = sampleRate,
                bufferFormat = BufferFormat.Float32,
            ),
            PortSpec(
                id = OUTPUT_PORT_ID,
                direction = PortDirection.Output,
                type = PortType.Audio,
                sampleRate = sampleRate,
                bufferFormat = BufferFormat.Float32,
            ),
        ),
        parameters = listOf(
            ParameterSpec(id = "mix", label = "Mix", range = 0.0..1.0, default = 1.0),
            ParameterSpec(id = "drive", label = "Drive", range = 1.0..20.0, default = 4.0),
            ParameterSpec(id = "tone", label = "Tone", range = 200.0..12000.0, default = 6000.0),
        ),
    )

    private val mixSmoother = ParameterSmoother(1.0)
    private val driveSmoother = ParameterSmoother(4.0)
    private val toneSmoother = ParameterSmoother(6000.0)

    private val _mix = MutableStateFlow(mixSmoother.current)
    private val _drive = MutableStateFlow(driveSmoother.current)
    private val _tone = MutableStateFlow(toneSmoother.current)

    /** Observable mirrors of each parameter's live smoothed value (contracts/host-observability-contract.md). */
    val mix: StateFlow<Double> = _mix.asStateFlow()
    val drive: StateFlow<Double> = _drive.asStateFlow()
    val tone: StateFlow<Double> = _tone.asStateFlow()

    // One-pole lowpass state, carried across process() calls.
    private var lowpassState: Float = 0f

    // Allocated once in onLoad() and mutated/reused every process() call, per Constitution III.
    private lateinit var outputBuffer: AudioBuffer

    override fun onLoad() {
        outputBuffer = AudioBuffer(FloatArray(BUFFER_SIZE))
    }

    fun setMix(value: Double) {
        mixSmoother.setTarget(value.coerceIn(0.0, 1.0))
    }

    fun setDrive(value: Double) {
        driveSmoother.setTarget(value.coerceIn(1.0, 20.0))
    }

    fun setTone(value: Double) {
        toneSmoother.setTarget(value.coerceIn(200.0, 12000.0))
    }

    override fun process(context: ProcessContext) {
        val input = context.readAudio(INPUT_PORT_ID)
        val samples = outputBuffer.samples

        for (i in samples.indices) {
            val drive = driveSmoother.advance()
            val tone = toneSmoother.advance()
            val mix = mixSmoother.advance()

            val dry = if (i < input.samples.size) input.samples[i] else 0f
            val driveGain = tanh(drive)
            val lowpassAlpha = (1.0 - exp(-2.0 * PI * tone / sampleRate)).toFloat()
            val clipped = tanh(drive * dry) / driveGain
            lowpassState += lowpassAlpha * (clipped.toFloat() - lowpassState)
            samples[i] = WetDryMixer.mix(dry, lowpassState, mix)
        }
        context.writeAudio(OUTPUT_PORT_ID, outputBuffer)
        _mix.value = mixSmoother.current
        _drive.value = driveSmoother.current
        _tone.value = toneSmoother.current
    }

    override fun onRemove() {
        // No resources to release beyond the pre-allocated buffer.
    }
}
