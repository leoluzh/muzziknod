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

private const val INPUT_PORT_ID = "in"
private const val OUTPUT_PORT_ID = "out"
private const val BUFFER_SIZE = 128
private const val MAX_DELAY_MS = 2000.0

/**
 * Circular buffer backing a single-tap delay with feedback (research.md
 * "Per-effect algorithm choice"). Sized once for [MAX_DELAY_MS] at construction
 * [sampleRate] so delay time stays correct across sample rates (spec FR-009).
 */
private class DelayLine(sampleRate: Int) {
    private val buffer = FloatArray(((MAX_DELAY_MS / 1000.0) * sampleRate).toInt().coerceAtLeast(1))
    private var writeIndex = 0
    private val sampleRateD = sampleRate.toDouble()

    /** Writes [input] + feedback into the line, returns the delayed ("wet") tap. */
    fun process(input: Float, delayTimeMs: Double, feedback: Double): Float {
        val delaySamples = ((delayTimeMs / 1000.0) * sampleRateD).toInt()
            .coerceIn(1, buffer.size - 1)
        val readIndex = ((writeIndex - delaySamples) % buffer.size + buffer.size) % buffer.size
        val delayed = buffer[readIndex]
        buffer[writeIndex] = input + delayed * feedback.toFloat()
        writeIndex = (writeIndex + 1) % buffer.size
        return delayed
    }
}

/**
 * Single-tap delay over a circular buffer (research.md "Per-effect algorithm
 * choice").
 */
class DelayModule(
    override val instanceId: String,
    private val sampleRate: Int = 48_000,
) : Module {
    override val contract: ModuleContract = ModuleContract(
        typeId = "delay",
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
            ParameterSpec(id = "delayTimeMs", label = "Delay Time", range = 1.0..2000.0, default = 375.0),
            ParameterSpec(id = "feedback", label = "Feedback", range = 0.0..0.95, default = 0.3),
        ),
    )

    private val mixSmoother = ParameterSmoother(0.5)
    private val delayTimeSmoother = ParameterSmoother(375.0)
    private val feedbackSmoother = ParameterSmoother(0.3)

    private val _mix = MutableStateFlow(mixSmoother.current)
    private val _delayTimeMs = MutableStateFlow(delayTimeSmoother.current)
    private val _feedback = MutableStateFlow(feedbackSmoother.current)

    /**
     * Observable mirrors of each parameter's live smoothed value
     * (contracts/host-observability-contract.md) — updated once per [process] cycle
     * (not per sample, to stay allocation-free in the hot path per Constitution III).
     */
    val mix: StateFlow<Double> = _mix.asStateFlow()
    val delayTimeMs: StateFlow<Double> = _delayTimeMs.asStateFlow()
    val feedback: StateFlow<Double> = _feedback.asStateFlow()

    private lateinit var delayLine: DelayLine

    // Allocated once in onLoad() and mutated/reused every process() call, per Constitution III.
    private lateinit var outputBuffer: AudioBuffer

    override fun onLoad() {
        delayLine = DelayLine(sampleRate)
        outputBuffer = AudioBuffer(FloatArray(BUFFER_SIZE))
    }

    fun setMix(value: Double) {
        mixSmoother.setTarget(value.coerceIn(0.0, 1.0))
    }

    fun setDelayTimeMs(value: Double) {
        delayTimeSmoother.setTarget(value.coerceIn(1.0, 2000.0))
    }

    fun setFeedback(value: Double) {
        feedbackSmoother.setTarget(value.coerceIn(0.0, 0.95))
    }

    override fun process(context: ProcessContext) {
        val input = context.readAudio(INPUT_PORT_ID)
        val samples = outputBuffer.samples
        for (i in samples.indices) {
            val dry = if (i < input.samples.size) input.samples[i] else 0f
            val wet = delayLine.process(dry, delayTimeSmoother.advance(), feedbackSmoother.advance())
            samples[i] = WetDryMixer.mix(dry, wet, mixSmoother.advance())
        }
        context.writeAudio(OUTPUT_PORT_ID, outputBuffer)
        _mix.value = mixSmoother.current
        _delayTimeMs.value = delayTimeSmoother.current
        _feedback.value = feedbackSmoother.current
    }

    override fun onRemove() {
        // No resources to release beyond the pre-allocated buffer/delay line.
    }
}
