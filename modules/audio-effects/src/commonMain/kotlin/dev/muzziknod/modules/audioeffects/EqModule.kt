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
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private const val INPUT_PORT_ID = "in"
private const val OUTPUT_PORT_ID = "out"
private const val BUFFER_SIZE = 128

enum class EqBand { Low, Mid, High }

private data class BandDefaults(val freqHz: Double, val gainDb: Double, val q: Double)

private val BAND_DEFAULTS = mapOf(
    EqBand.Low to BandDefaults(freqHz = 100.0, gainDb = 0.0, q = 0.7),
    EqBand.Mid to BandDefaults(freqHz = 1000.0, gainDb = 0.0, q = 0.7),
    EqBand.High to BandDefaults(freqHz = 8000.0, gainDb = 0.0, q = 0.7),
)

/**
 * RBJ (Audio EQ Cookbook) peaking biquad — direct form I, coefficients recomputed
 * whenever the band's frequency/gain/Q changes.
 */
private class Biquad(sampleRate: Int) {
    private val sampleRateD = sampleRate.toDouble()

    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    fun setCoefficients(freqHz: Double, gainDb: Double, q: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freqHz / sampleRateD
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val rawB0 = 1.0 + alpha * a
        val rawB1 = -2.0 * cosW0
        val rawB2 = 1.0 - alpha * a
        val rawA0 = 1.0 + alpha / a
        val rawA1 = -2.0 * cosW0
        val rawA2 = 1.0 - alpha / a

        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = rawA2 / rawA0
    }

    fun process(input: Float): Float {
        val output = (b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2).toFloat()
        x2 = x1
        x1 = input
        y2 = y1
        y1 = output
        return output
    }
}

/**
 * 3-band parametric EQ using RBJ peaking biquads in series (research.md
 * "Per-effect algorithm choice"). No `mix` parameter — every band at 0 dB gain is
 * already passthrough (spec Edge Cases).
 */
class EqModule(
    override val instanceId: String,
    private val sampleRate: Int = 48_000,
) : Module {
    override val contract: ModuleContract = ModuleContract(
        typeId = "eq",
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
        parameters = EqBand.entries.flatMap { band ->
            val defaults = BAND_DEFAULTS.getValue(band)
            listOf(
                ParameterSpec(
                    id = "${band.name.lowercase()}.freqHz",
                    label = "${band.name} Frequency",
                    range = 20.0..20000.0,
                    default = defaults.freqHz,
                ),
                ParameterSpec(
                    id = "${band.name.lowercase()}.gainDb",
                    label = "${band.name} Gain",
                    range = -24.0..24.0,
                    default = defaults.gainDb,
                ),
                ParameterSpec(
                    id = "${band.name.lowercase()}.q",
                    label = "${band.name} Q",
                    range = 0.1..10.0,
                    default = defaults.q,
                ),
            )
        },
    )

    private val bandFrequencySmoothers = EqBand.entries.associateWith { ParameterSmoother(BAND_DEFAULTS.getValue(it).freqHz) }
    private val bandGainSmoothers = EqBand.entries.associateWith { ParameterSmoother(BAND_DEFAULTS.getValue(it).gainDb) }
    private val bandQSmoothers = EqBand.entries.associateWith { ParameterSmoother(BAND_DEFAULTS.getValue(it).q) }

    private val _bandFrequency = EqBand.entries.associateWith { MutableStateFlow(bandFrequencySmoothers.getValue(it).current) }
    private val _bandGain = EqBand.entries.associateWith { MutableStateFlow(bandGainSmoothers.getValue(it).current) }
    private val _bandQ = EqBand.entries.associateWith { MutableStateFlow(bandQSmoothers.getValue(it).current) }

    /** Observable mirrors of each band's live smoothed value (contracts/host-observability-contract.md). */
    fun bandFrequency(band: EqBand): StateFlow<Double> = _bandFrequency.getValue(band).asStateFlow()
    fun bandGain(band: EqBand): StateFlow<Double> = _bandGain.getValue(band).asStateFlow()
    fun bandQ(band: EqBand): StateFlow<Double> = _bandQ.getValue(band).asStateFlow()

    private lateinit var biquads: Map<EqBand, Biquad>

    // Allocated once in onLoad() and mutated/reused every process() call, per Constitution III.
    private lateinit var outputBuffer: AudioBuffer

    override fun onLoad() {
        biquads = EqBand.entries.associateWith { Biquad(sampleRate) }
        outputBuffer = AudioBuffer(FloatArray(BUFFER_SIZE))
    }

    fun setBandFrequency(band: EqBand, hz: Double) {
        bandFrequencySmoothers.getValue(band).setTarget(hz.coerceIn(20.0, 20000.0))
    }

    fun setBandGain(band: EqBand, db: Double) {
        bandGainSmoothers.getValue(band).setTarget(db.coerceIn(-24.0, 24.0))
    }

    fun setBandQ(band: EqBand, q: Double) {
        bandQSmoothers.getValue(band).setTarget(q.coerceIn(0.1, 10.0))
    }

    override fun process(context: ProcessContext) {
        val input = context.readAudio(INPUT_PORT_ID)
        val samples = outputBuffer.samples
        for (i in samples.indices) {
            var sample = if (i < input.samples.size) input.samples[i] else 0f
            for (band in EqBand.entries) {
                val freqHz = bandFrequencySmoothers.getValue(band).advance()
                val gainDb = bandGainSmoothers.getValue(band).advance()
                val q = bandQSmoothers.getValue(band).advance()
                val biquad = biquads.getValue(band)
                biquad.setCoefficients(freqHz, gainDb, q)
                sample = biquad.process(sample)
            }
            samples[i] = sample
        }
        context.writeAudio(OUTPUT_PORT_ID, outputBuffer)
        for (band in EqBand.entries) {
            _bandFrequency.getValue(band).value = bandFrequencySmoothers.getValue(band).current
            _bandGain.getValue(band).value = bandGainSmoothers.getValue(band).current
            _bandQ.getValue(band).value = bandQSmoothers.getValue(band).current
        }
    }

    override fun onRemove() {
        // No resources to release beyond the pre-allocated buffer.
    }
}
