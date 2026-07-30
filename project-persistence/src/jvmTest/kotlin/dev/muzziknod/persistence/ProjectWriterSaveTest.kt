package dev.muzziknod.persistence

import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.host.transport.Transport
import dev.muzziknod.modules.audioeffects.DelayModule
import dev.muzziknod.modules.audioeffects.ReverbModule
import dev.muzziknod.persistence.model.ProjectSnapshot
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `ProjectWriter.save()` writes a JSON file whose decoded `ProjectSnapshot` contains the
 * graph, connections, parameters, and transport (FR-001, FR-002, FR-003, FR-004; US1 AC1).
 */
class ProjectWriterSaveTest {
    private val tempFile = Files.createTempFile("project-writer-save-test", ".json")

    @AfterTest
    fun cleanup() {
        Files.deleteIfExists(tempFile)
    }

    @Test
    fun saveWritesGraphParametersAndTransport() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)
        val transport = Transport()
        val catalog = defaultProjectPersistenceCatalog()

        val delay = DelayModule(instanceId = "delay-1")
        val reverb = ReverbModule(instanceId = "reverb-1")
        registry.load(delay)
        registry.load(reverb)
        delay.setMix(0.65)
        delay.setDelayTimeMs(300.0)
        delay.setFeedback(0.2)
        graph.connect("delay-1", "out", "reverb-1", "in")
        graph.processCycle()
        transport.setTempo(100.0)
        transport.play()

        val writer = ProjectWriter(registry, graph, transport, catalog)
        writer.save(tempFile.toString())

        val content = Files.readString(tempFile)
        val snapshot = Json.decodeFromString(ProjectSnapshot.serializer(), content)

        assertEquals(2, snapshot.modules.size)
        val delaySnapshot = snapshot.modules.single { it.instanceId == "delay-1" }
        assertEquals("delay", delaySnapshot.typeId)
        assertEquals(0.65, delaySnapshot.parameters.getValue("mix"), 1e-6)
        assertEquals(300.0, delaySnapshot.parameters.getValue("delayTimeMs"), 1e-6)
        assertEquals(0.2, delaySnapshot.parameters.getValue("feedback"), 1e-6)

        assertEquals(1, snapshot.connections.size)
        val connection = snapshot.connections.single()
        assertEquals("delay-1", connection.sourceInstanceId)
        assertEquals("reverb-1", connection.targetInstanceId)

        assertEquals(100.0, snapshot.transport.tempoBpm, 1e-6)
        assertTrue(snapshot.transport.isPlaying)
    }
}
