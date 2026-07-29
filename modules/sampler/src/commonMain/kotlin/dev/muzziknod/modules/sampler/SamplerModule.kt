package dev.muzziknod.modules.sampler

import dev.muzziknod.host.contract.AudioBuffer
import dev.muzziknod.host.contract.BufferFormat
import dev.muzziknod.host.contract.MidiEvent
import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.ModuleContract
import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.contract.ProcessContext

private const val INPUT_PORT_ID = "in"
private const val OUTPUT_PORT_ID = "out"
private const val BUFFER_SIZE = 128

sealed class SampleLoadResult {
    data class Loaded(val zone: SampleZone) : SampleLoadResult()
    data class Failed(val reason: String) : SampleLoadResult()
}

/**
 * Product module (spec 005-sampler-module). Satisfies core-host's [Module] contract
 * unchanged; sample loading/mapping is a plain Kotlin API beyond that interface (see
 * contracts/sampler-api.md).
 */
class SamplerModule(
    override val instanceId: String,
    private val sampleRate: Int = 48_000,
    private val maxVoices: Int = 32,
) : Module {

    override val contract: ModuleContract = ModuleContract(
        typeId = "sampler",
        version = 1,
        ports = listOf(
            PortSpec(id = INPUT_PORT_ID, direction = PortDirection.Input, type = PortType.Midi),
            PortSpec(
                id = OUTPUT_PORT_ID, direction = PortDirection.Output, type = PortType.Audio,
                sampleRate = sampleRate, bufferFormat = BufferFormat.Float32,
            ),
        ),
    )

    private val mutableZones = mutableListOf<SampleZone>()
    val zones: List<SampleZone> get() = mutableZones

    private lateinit var voicePool: VoicePool
    private lateinit var outputBuffer: AudioBuffer

    override fun onLoad() {
        voicePool = VoicePool(maxVoices)
        outputBuffer = AudioBuffer(FloatArray(BUFFER_SIZE))
    }

    /** Not-real-time: decodes [bytes], resamples to [sampleRate]; never called from process() (FR-001, FR-002). */
    fun loadSample(
        bytes: ByteArray,
        id: String,
        rootNote: Int,
        lowNote: Int = 0,
        highNote: Int = 127,
        gain: Double = 1.0,
        loopMode: LoopMode = LoopMode.OneShot,
    ): SampleLoadResult {
        val decoded = try {
            decodeSample(bytes, sampleRate)
        } catch (e: Exception) {
            return SampleLoadResult.Failed(e.message ?: e.toString())
        }
        if (decoded.samples.isEmpty()) {
            return SampleLoadResult.Failed("Decoded sample '$id' has no audio data")
        }
        val zone = SampleZone(
            sample = Sample(id, decoded.samples, sampleRate),
            rootNote = rootNote,
            lowNote = lowNote,
            highNote = highNote,
            gain = gain,
            loopMode = loopMode,
        )
        mutableZones += zone
        return SampleLoadResult.Loaded(zone)
    }

    fun unloadSample(zone: SampleZone) {
        mutableZones -= zone
    }

    /** White-box test seam: injects a pre-built zone without going through the decoder. */
    internal fun addZoneForTesting(zone: SampleZone) {
        mutableZones += zone
    }

    /** White-box test seam: exposes the pool so tests can assert on voice state directly. */
    internal fun voicePoolForTesting(): VoicePool = voicePool

    override fun process(context: ProcessContext) {
        for (event in context.readMidi(INPUT_PORT_ID)) {
            dispatch(event)
        }

        val samples = outputBuffer.samples
        for (i in samples.indices) {
            samples[i] = voicePool.renderNextSample()
        }
        context.writeAudio(OUTPUT_PORT_ID, outputBuffer)
    }

    private fun dispatch(event: MidiEvent) {
        val note = event.data1
        val velocity = event.data2
        val isNoteOn = (event.status and 0xF0) == NOTE_ON_STATUS
        val isNoteOff = (event.status and 0xF0) == NOTE_OFF_STATUS

        if (isNoteOn && velocity > 0) {
            // Index-based (not firstOrNull/for-in) to avoid the ArrayList iterator
            // allocation that a MutableList for-loop would otherwise incur here (Constitution III).
            var matched: SampleZone? = null
            for (i in mutableZones.indices) {
                val candidate = mutableZones[i]
                if (candidate.covers(note)) {
                    matched = candidate
                    break
                }
            }
            val zone = matched ?: return
            voicePool.trigger(zone, note, velocity)
        } else if (isNoteOff || (isNoteOn && velocity == 0)) {
            voicePool.release(note)
        }
    }

    private companion object {
        const val NOTE_ON_STATUS = 0x90
        const val NOTE_OFF_STATUS = 0x80
    }

    override fun onRemove() {
        // No resources to release beyond the pre-allocated VoicePool/output buffer.
    }
}
