package dev.muzziknod.modules.sampler

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin

/** Generates real, decoder-round-trippable WAV/AIFF fixtures in-memory (no committed binary assets). */
object TestAudioFixtures {

    fun sineWav(sampleRate: Int, bitDepth: Int, channels: Int = 1, durationSamples: Int = 200): ByteArray =
        encode(AudioFileFormat.Type.WAVE, sampleRate, bitDepth, channels, durationSamples, bigEndian = false)

    fun sineAiff(sampleRate: Int = 44_100, bitDepth: Int = 16, channels: Int = 1, durationSamples: Int = 200): ByteArray =
        encode(AudioFileFormat.Type.AIFF, sampleRate, bitDepth, channels, durationSamples, bigEndian = true)

    private fun encode(
        type: AudioFileFormat.Type,
        sampleRate: Int,
        bitDepth: Int,
        channels: Int,
        durationSamples: Int,
        bigEndian: Boolean,
    ): ByteArray {
        val frameSize = channels * (bitDepth / 8)
        val format = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate.toFloat(),
            bitDepth,
            channels,
            frameSize,
            sampleRate.toFloat(),
            bigEndian,
        )
        val pcm = ByteArrayOutputStream()
        val maxAmplitude = (1L shl (bitDepth - 1)) - 1
        for (i in 0 until durationSamples) {
            val value = (sin(2.0 * PI * 4.0 * i / durationSamples) * maxAmplitude).toLong()
            val bytes = when (bitDepth) {
                16 -> byteArrayOf((value shr 8).toByte(), value.toByte())
                24 -> byteArrayOf((value shr 16).toByte(), (value shr 8).toByte(), value.toByte())
                else -> error("Unsupported test bit depth: $bitDepth")
            }
            val ordered = if (bigEndian) bytes else bytes.reversedArray()
            for (channel in 0 until channels) {
                pcm.write(ordered)
            }
        }
        val pcmBytes = pcm.toByteArray()
        val frameCount = (pcmBytes.size / frameSize).toLong()
        val out = ByteArrayOutputStream()
        AudioInputStream(ByteArrayInputStream(pcmBytes), format, frameCount).use { stream ->
            AudioSystem.write(stream, type, out)
        }
        return out.toByteArray()
    }

    fun corruptBytes(): ByteArray = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
}
