package dev.muzziknod.modules.sampler

import kotlin.math.pow

/** Fixed-size voice array, allocated once — never grown/shrunk inside process() (Constitution III). */
class VoicePool(maxVoices: Int) {
    private val voices: Array<Voice> = Array(maxVoices) { Voice() }
    private var triggerCounter: Long = 0

    val activeVoices: List<Voice> get() = voices.filter { it.state != VoiceState.Free }

    /**
     * Claims a free voice for [zone]/[note]/[velocity]; if none is free, steals the voice
     * with the lowest [Voice.triggerOrder] (oldest), which crossfades its old sound out
     * against the new note fading in rather than cutting instantly (FR-009).
     */
    fun trigger(zone: SampleZone, note: Int, velocity: Int) {
        val target = voices.firstOrNull { it.state == VoiceState.Free }
            ?: voices.minByOrNull { it.triggerOrder }
            ?: return
        val gain = zone.gain * (velocity / 127.0)
        val pitchRatio = 2.0.pow((note - zone.rootNote) / 12.0)
        target.trigger(zone, note, gain, pitchRatio, triggerCounter++)
    }

    /** Releases (fades out) matching Loop-mode voices; OneShot voices ignore note-off (FR-005). */
    fun release(note: Int) {
        for (voice in voices) {
            if (voice.note == note && voice.state == VoiceState.Playing && voice.zone?.loopMode == LoopMode.Loop) {
                voice.startRelease()
            }
        }
    }

    fun renderNextSample(): Float {
        var sum = 0f
        for (voice in voices) {
            if (voice.state != VoiceState.Free) {
                sum += voice.renderNextSample()
            }
        }
        return sum
    }
}
