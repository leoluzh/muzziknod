package dev.muzziknod.persistence

import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.host.transport.Transport
import dev.muzziknod.modules.sampler.LoopMode
import dev.muzziknod.modules.sampler.SamplerModule
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Full save -> disk -> load round trip including a sampler instance backed by a real
 * file on disk (FR-005, FR-006, FR-007; US2 AC4; quickstart.md scenario 1).
 */
class SamplerProjectRoundTripTest {
    private val projectFile = Files.createTempFile("sampler-project-round-trip", ".json")
    private val sampleFile = Files.createTempFile("sampler-round-trip-sample", ".wav")

    @AfterTest
    fun cleanup() {
        Files.deleteIfExists(projectFile)
        Files.deleteIfExists(sampleFile)
    }

    @Test
    fun roundTripsSamplerZoneThroughARealFile() {
        Files.write(sampleFile, tinyMonoWav(shortArrayOf(0, 500, -500, 0)))

        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)
        val transport = Transport()
        val catalog = defaultProjectPersistenceCatalog()

        val sampler = SamplerModule(instanceId = "sampler-1")
        registry.load(sampler)
        val loadResult = sampler.loadSample(
            bytes = Files.readAllBytes(sampleFile),
            id = "kick",
            rootNote = 60,
            gain = 0.9,
            loopMode = LoopMode.OneShot,
            sourcePath = sampleFile.toString(),
        )
        assertTrue(loadResult is dev.muzziknod.modules.sampler.SampleLoadResult.Loaded, "expected load to succeed: $loadResult")

        ProjectWriter(registry, graph, transport, catalog).save(projectFile.toString())

        val freshRegistry = ModuleRegistry()
        val freshGraph = RoutingGraph(freshRegistry)
        val freshTransport = Transport()
        val result = ProjectReader(freshRegistry, freshGraph, freshTransport, catalog).load(projectFile.toString())

        assertTrue(result.warnings.isEmpty(), "expected no warnings, got ${result.warnings}")
        val restoredSampler = freshRegistry.get("sampler-1")!!.module as SamplerModule
        assertEquals(1, restoredSampler.zones.size)
        val zone = restoredSampler.zones.single()
        assertEquals(sampleFile.toString(), zone.sourcePath)
        assertEquals(60, zone.rootNote)
        assertEquals(0.9, zone.gain, 1e-9)
        assertEquals(LoopMode.OneShot, zone.loopMode)
    }

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
        writeLeShort(20, 1)
        writeLeShort(22, 1)
        writeLeInt(24, sampleRate)
        writeLeInt(28, sampleRate * 2)
        writeLeShort(32, 2)
        writeLeShort(34, 16)
        writeAscii(36, "data")
        writeLeInt(40, dataSize)
        sampleValues.forEachIndexed { i, sample -> writeLeShort(44 + i * 2, sample.toInt()) }
        return bytes
    }
}
