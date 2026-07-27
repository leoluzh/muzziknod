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
import kotlin.math.pow

private const val INPUT_PORT_ID = "in"
private const val OUTPUT_PORT_ID = "out"
private const val BUFFER_SIZE = 128

// Classic Schroeder comb/allpass delay lengths in samples at a 44100Hz reference,
// scaled to the module's actual sampleRate at construction time.
private val COMB_DELAYS_AT_44100 = intArrayOf(1116, 1188, 1277, 1356)
private val ALLPASS_DELAYS_AT_44100 = intArrayOf(225, 556)

/** Parallel feedback comb filter — one of four summed to build the reverb's early/mid tail. */
private class CombFilter(delaySamples: Int) {
    private val buffer = FloatArray(delaySamples.coerceAtLeast(1))
    private var index = 0

    fun process(input: Float, feedback: Float): Float {
        val delayed = buffer[index]
        buffer[index] = input + delayed * feedback
        index = (index + 1) % buffer.size
        return delayed
    }
}

/** Series allpass filter — diffuses the comb output into a smoother tail. */
private class AllpassFilter(delaySamples: Int, private val gain: Float = 0.5f) {
    private val buffer = FloatArray(delaySamples.coerceAtLeast(1))
    private var index = 0

    fun process(input: Float): Float {
        val bufOut = buffer[index]
        val output = -gain * input + bufOut
        buffer[index] = input + bufOut * gain
        index = (index + 1) % buffer.size
        return output
    }
}

/**
 * Schroeder/Moorer reverb: 4 parallel [CombFilter]s summed, then through 2 series
 * [AllpassFilter]s (research.md "Per-effect algorithm choice"). `roomSize` scales
 * comb feedback, `decayMs` sets the RT60-style decay target each comb's feedback is
 * derived from.
 */
class ReverbModule(
    override val instanceId: String,
    private val sampleRate: Int = 48_000,
) : Module {
    override val contract: ModuleContract = ModuleContract(
        typeId = "reverb",
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
            ParameterSpec(id = "mix", label = "Mix", range = 0.0..1.0, default = 0.5),
            ParameterSpec(id = "decayMs", label = "Decay Time", range = 50.0..5000.0, default = 1500.0),
            ParameterSpec(id = "roomSize", label = "Room Size", range = 0.0..1.0, default = 0.5),
        ),
    )

    private val mixSmoother = ParameterSmoother(0.5)
    private val decaySmoother = ParameterSmoother(1500.0)
    private val roomSizeSmoother = ParameterSmoother(0.5)

    private val _mix = MutableStateFlow(mixSmoother.current)
    private val _decayMs = MutableStateFlow(decaySmoother.current)
    private val _roomSize = MutableStateFlow(roomSizeSmoother.current)

    /** Observable mirrors of each parameter's live smoothed value (contracts/host-observability-contract.md). */
    val mix: StateFlow<Double> = _mix.asStateFlow()
    val decayMs: StateFlow<Double> = _decayMs.asStateFlow()
    val roomSize: StateFlow<Double> = _roomSize.asStateFlow()

    private lateinit var combs: List<CombFilter>
    private lateinit var combDelaySamples: List<Int>
    private lateinit var allpasses: List<AllpassFilter>

    // Allocated once in onLoad() and mutated/reused every process() call, per Constitution III.
    private lateinit var outputBuffer: AudioBuffer

    override fun onLoad() {
        val scale = sampleRate / 44_100.0
        combDelaySamples = COMB_DELAYS_AT_44100.map { (it * scale).toInt().coerceAtLeast(1) }
        combs = combDelaySamples.map { CombFilter(it) }
        allpasses = ALLPASS_DELAYS_AT_44100.map { AllpassFilter((it * scale).toInt().coerceAtLeast(1)) }
        outputBuffer = AudioBuffer(FloatArray(BUFFER_SIZE))
    }

    fun setMix(value: Double) {
        mixSmoother.setTarget(value.coerceIn(0.0, 1.0))
    }

    fun setDecayMs(value: Double) {
        decaySmoother.setTarget(value.coerceIn(50.0, 5000.0))
    }

    fun setRoomSize(value: Double) {
        roomSizeSmoother.setTarget(value.coerceIn(0.0, 1.0))
    }

    override fun process(context: ProcessContext) {
        val input = context.readAudio(INPUT_PORT_ID)
        val samples = outputBuffer.samples

        for (i in samples.indices) {
            val decayMs = decaySmoother.advance()
            val roomSize = roomSizeSmoother.advance()
            val mix = mixSmoother.advance()

            val dry = if (i < input.samples.size) input.samples[i] else 0f
            var wet = 0f
            for (c in combs.indices) {
                val delaySamples = combDelaySamples[c]
                val delaySeconds = delaySamples.toDouble() / sampleRate
                val decaySeconds = (decayMs / 1000.0).coerceAtLeast(0.001)
                val base = 10.0.pow(-3.0 * delaySeconds / decaySeconds)
                val feedback = (base * (0.85 + 0.15 * roomSize)).coerceIn(0.0, 0.98).toFloat()
                wet += combs[c].process(dry, feedback)
            }
            wet /= combs.size
            for (allpass in allpasses) {
                wet = allpass.process(wet)
            }
            samples[i] = WetDryMixer.mix(dry, wet, mix)
        }
        context.writeAudio(OUTPUT_PORT_ID, outputBuffer)
        _mix.value = mixSmoother.current
        _decayMs.value = decaySmoother.current
        _roomSize.value = roomSizeSmoother.current
    }

    override fun onRemove() {
        // No resources to release beyond the pre-allocated buffers/filters.
    }
}
