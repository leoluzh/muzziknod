package dev.muzziknod.modules.sampler

import kotlin.math.floor

enum class VoiceState { Free, Playing, Releasing }

private const val FADE_SAMPLES = 32
private const val FADE_STEP = 1f / FADE_SAMPLES

/** One playback slot, reused across triggers — never allocated inside process() (Constitution III). */
class Voice {
    var state: VoiceState = VoiceState.Free
        private set
    var zone: SampleZone? = null
        private set
    var position: Double = 0.0
    var pitchRatio: Double = 1.0
    var note: Int = -1
    var gain: Double = 1.0
    var triggerOrder: Long = 0
    var fadeGain: Float = 1.0f
        private set

    // Steal handling: while non-zero, the OLD (stolen) zone's tail crossfades out against
    // the NEW zone's head fading in, so a steal never clicks and the new note is
    // immediately audible (research.md "Polyphony and voice stealing").
    private var stolenZone: SampleZone? = null
    private var stolenPosition: Double = 0.0
    private var stolenPitchRatio: Double = 1.0
    private var stolenGain: Double = 1.0
    private var crossfadeRemaining: Int = 0

    fun trigger(zone: SampleZone, note: Int, gain: Double, pitchRatio: Double, triggerOrder: Long) {
        if (state != VoiceState.Free) {
            stolenZone = this.zone
            stolenPosition = this.position
            stolenPitchRatio = this.pitchRatio
            stolenGain = this.gain
            crossfadeRemaining = FADE_SAMPLES
        } else {
            stolenZone = null
            crossfadeRemaining = 0
        }

        this.zone = zone
        this.note = note
        this.gain = gain
        this.pitchRatio = pitchRatio
        this.triggerOrder = triggerOrder
        this.position = 0.0
        this.fadeGain = 1.0f
        this.state = VoiceState.Playing
    }

    /** Starts a short click-free fade to silence — used for note-off release (Loop mode). */
    fun startRelease() {
        if (state == VoiceState.Playing) {
            state = VoiceState.Releasing
        }
    }

    /** Renders one sample via pitch-ratio-advanced, linear-interpolated playback. */
    fun renderNextSample(): Float {
        val currentZone = zone ?: return 0f
        var output = advanceMain(currentZone) * gain.toFloat()

        if (crossfadeRemaining > 0) {
            val oldSample = stolenZone?.let { advanceStolen(it) * stolenGain.toFloat() } ?: 0f
            val fadeOutWeight = crossfadeRemaining / FADE_SAMPLES.toFloat()
            output = oldSample * fadeOutWeight + output * (1f - fadeOutWeight)
            crossfadeRemaining -= 1
            if (crossfadeRemaining <= 0) stolenZone = null
        }

        if (state == VoiceState.Releasing) {
            output *= fadeGain
            fadeGain -= FADE_STEP
            if (fadeGain <= 0f) free()
        }
        return output
    }

    /** Reads+advances the currently-triggered zone; frees (OneShot) or wraps (Loop) at end-of-data. */
    private fun advanceMain(currentZone: SampleZone): Float {
        val data = currentZone.sample.data
        if (position >= data.size) {
            free()
            return 0f
        }
        val sample = interpolate(data, position)
        position += pitchRatio
        if (position >= data.size) {
            if (currentZone.loopMode == LoopMode.Loop) position %= data.size else free()
        }
        return sample
    }

    /** Reads+advances the stolen (fading-out) zone; simply goes silent if it runs out mid-crossfade. */
    private fun advanceStolen(previousZone: SampleZone): Float {
        val data = previousZone.sample.data
        if (stolenPosition >= data.size) return 0f
        val sample = interpolate(data, stolenPosition)
        stolenPosition += stolenPitchRatio
        if (stolenPosition >= data.size && previousZone.loopMode == LoopMode.Loop) {
            stolenPosition %= data.size
        }
        return sample
    }

    private fun interpolate(data: FloatArray, pos: Double): Float {
        val index0 = floor(pos).toInt().coerceIn(0, data.size - 1)
        val index1 = (index0 + 1).coerceAtMost(data.size - 1)
        val frac = (pos - index0).toFloat()
        return data[index0] + (data[index1] - data[index0]) * frac
    }

    private fun free() {
        state = VoiceState.Free
        zone = null
    }
}
