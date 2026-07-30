package dev.muzziknod.persistence.codec

import dev.muzziknod.modules.sampler.LoopMode
import dev.muzziknod.modules.sampler.SamplerModule
import dev.muzziknod.persistence.model.SamplerData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `SamplerCodec.capture()` never embeds raw sample audio — only a [dev.muzziknod
 * .persistence.model.SampleZoneSnapshot.sourcePath] reference (FR-005, FR-006).
 */
class SamplerCodecTest {

    @Test
    fun captureProducesZonesWithSourcePathAndNoEmbeddedAudio() {
        val sampler = SamplerModule(instanceId = "sampler-1")
        sampler.onLoad()
        val result = sampler.loadSample(
            bytes = tinyMonoWav(sampleValues = shortArrayOf(0, 1000, -1000, 0)),
            id = "kick",
            rootNote = 60,
            lowNote = 48,
            highNote = 72,
            gain = 0.8,
            loopMode = LoopMode.Loop,
            sourcePath = "/samples/kick.wav",
        )
        assertTrue(result is dev.muzziknod.modules.sampler.SampleLoadResult.Loaded, "expected load to succeed: $result")

        val snapshot = SamplerCodec().capture(sampler)
        assertEquals("sampler-1", snapshot.instanceId)
        assertEquals("sampler", snapshot.typeId)
        assertTrue(snapshot.parameters.isEmpty())

        val data = Json.decodeFromJsonElement<SamplerData>(snapshot.moduleData!!)
        assertEquals(1, data.zones.size)
        val zone = data.zones.single()
        assertEquals("/samples/kick.wav", zone.sourcePath)
        assertEquals("kick", zone.sampleId)
        assertEquals(60, zone.rootNote)
        assertEquals(48, zone.lowNote)
        assertEquals(72, zone.highNote)
        assertEquals(0.8, zone.gain, 1e-9)
        assertEquals("Loop", zone.loopMode)

        // The encoded moduleData carries only the zone's descriptive fields above — no raw
        // PCM sample array anywhere in the JSON.
        val encoded = Json.encodeToString(SamplerData.serializer(), data)
        assertFalse(encoded.contains("1000"), "encoded moduleData should not contain raw sample values: $encoded")
    }

    /** Minimal valid 16-bit mono PCM WAV, built by hand so this test needs no platform audio API. */
    private fun tinyMonoWav(sampleValues: ShortArray, sampleRate: Int = 44_100): ByteArray {
        val dataSize = sampleValues.size * 2
        val bytes = ByteArray(44 + dataSize)
        fun writeAscii(offset: Int, text: String) {
            text.forEachIndexed { i, c -> bytes[offset + i] = c.code.toByte() }
        }
        fun writeLeInt(offset: Int, value: Int) {
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
            bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
            bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
        fun writeLeShort(offset: Int, value: Int) {
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }

        writeAscii(0, "RIFF")
        writeLeInt(4, 36 + dataSize)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeLeInt(16, 16)
        writeLeShort(20, 1) // PCM
        writeLeShort(22, 1) // mono
        writeLeInt(24, sampleRate)
        writeLeInt(28, sampleRate * 2) // byte rate = sampleRate * channels * bitsPerSample/8
        writeLeShort(32, 2) // block align
        writeLeShort(34, 16) // bits per sample
        writeAscii(36, "data")
        writeLeInt(40, dataSize)
        sampleValues.forEachIndexed { i, sample -> writeLeShort(44 + i * 2, sample.toInt()) }
        return bytes
    }
}
