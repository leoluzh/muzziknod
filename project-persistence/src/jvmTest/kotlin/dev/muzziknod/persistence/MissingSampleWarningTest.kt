package dev.muzziknod.persistence

import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.host.transport.Transport
import dev.muzziknod.modules.audioeffects.DelayModule
import dev.muzziknod.modules.sampler.LoopMode
import dev.muzziknod.modules.sampler.SamplerModule
import dev.muzziknod.persistence.model.ModuleSnapshot
import dev.muzziknod.persistence.model.ProjectSnapshot
import dev.muzziknod.persistence.model.SampleZoneSnapshot
import dev.muzziknod.persistence.model.SamplerData
import dev.muzziknod.persistence.model.TransportSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A sampler zone whose `sourcePath` can't be read is reported as a warning; the rest
 * of the project (including other modules) still loads (FR-010; US3 AC1).
 */
class MissingSampleWarningTest {
    private val deletedSampleFile = Files.createTempFile("missing-sample-warning-test", ".wav")

    @AfterTest
    fun cleanup() {
        Files.deleteIfExists(deletedSampleFile)
    }

    @Test
    fun missingSampleFileIsWarnedAboutAndRestOfProjectStillLoads() {
        val missingPath = deletedSampleFile.toString()
        Files.delete(deletedSampleFile)

        val samplerSnapshot = ModuleSnapshot(
            instanceId = "sampler-1",
            typeId = "sampler",
            moduleData = Json.encodeToJsonElement(
                SamplerData(
                    zones = listOf(
                        SampleZoneSnapshot(
                            sourcePath = missingPath,
                            sampleId = "kick",
                            rootNote = 60,
                            lowNote = 0,
                            highNote = 127,
                            gain = 1.0,
                            loopMode = LoopMode.OneShot.name,
                        ),
                    ),
                ),
            ),
        )
        val delaySnapshot = defaultProjectPersistenceCatalog()
            .codecFor("delay")!!
            .capture(DelayModule(instanceId = "delay-1"))

        val snapshot = ProjectSnapshot(
            schemaVersion = ProjectSnapshot.CURRENT_SCHEMA_VERSION,
            modules = listOf(delaySnapshot, samplerSnapshot),
            connections = emptyList(),
            transport = TransportSnapshot(120.0, 0.0, false, null, null),
        )
        val content = Json.encodeToString(ProjectSnapshot.serializer(), snapshot)

        val registry = ModuleRegistry()
        val reader = ProjectReader(registry, RoutingGraph(registry), Transport(), defaultProjectPersistenceCatalog())
        val result = reader.loadFromContent(content)

        assertEquals(1, result.warnings.size)
        val warning = assertIs<LoadWarning.MissingSampleFile>(result.warnings.single())
        assertEquals("sampler-1", warning.instanceId)
        assertEquals(missingPath, warning.sourcePath)

        assertEquals(2, registry.all().size)
        val restoredSampler = registry.get("sampler-1")!!.module as SamplerModule
        assertEquals(0, restoredSampler.zones.size)
        assertEquals("delay-1", registry.get("delay-1")!!.instanceId)
    }
}
