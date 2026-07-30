package dev.muzziknod.persistence

import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.host.transport.Transport
import dev.muzziknod.modules.audioeffects.DelayModule
import dev.muzziknod.persistence.model.ModuleSnapshot
import dev.muzziknod.persistence.model.ProjectSnapshot
import dev.muzziknod.persistence.model.TransportSnapshot
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A `ModuleSnapshot` with an unrecognized `typeId` is reported as a warning; every
 * other module in the file still loads (FR-009; US3 AC2).
 */
class MissingModuleWarningTest {

    @Test
    fun unrecognizedTypeIdIsWarnedAboutAndRestOfProjectStillLoads() {
        val delaySnapshot = defaultProjectPersistenceCatalog()
            .codecFor("delay")!!
            .capture(DelayModule(instanceId = "delay-1"))
        val unknownSnapshot = ModuleSnapshot(instanceId = "mystery-1", typeId = "quantum-flux-capacitor")

        val snapshot = ProjectSnapshot(
            schemaVersion = ProjectSnapshot.CURRENT_SCHEMA_VERSION,
            modules = listOf(delaySnapshot, unknownSnapshot),
            connections = emptyList(),
            transport = TransportSnapshot(120.0, 0.0, false, null, null),
        )
        val content = Json.encodeToString(ProjectSnapshot.serializer(), snapshot)

        val registry = ModuleRegistry()
        val reader = ProjectReader(registry, RoutingGraph(registry), Transport(), defaultProjectPersistenceCatalog())
        val result = reader.loadFromContent(content)

        assertEquals(1, result.warnings.size)
        val warning = assertIs<LoadWarning.MissingModuleType>(result.warnings.single())
        assertEquals("quantum-flux-capacitor", warning.typeId)
        assertEquals("mystery-1", warning.instanceId)

        assertEquals(1, registry.all().size)
        assertEquals("delay-1", registry.all().single().instanceId)
    }
}
