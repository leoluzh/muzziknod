package dev.muzziknod.persistence

import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.host.transport.Transport
import dev.muzziknod.persistence.model.ConnectionSnapshot
import dev.muzziknod.persistence.model.ProjectSnapshot
import dev.muzziknod.persistence.model.TransportSnapshot
import kotlinx.serialization.json.Json

/**
 * Captures live host state into a project file (FR-001, FR-002, FR-003, FR-004,
 * FR-012). Module instances whose `typeId` has no registered codec (today: the
 * reference/scaffolding module types, which never shipped a codec — contracts/
 * module-state-codec.md) are skipped rather than failing the whole save.
 */
class ProjectWriter(
    private val registry: ModuleRegistry,
    private val graph: RoutingGraph,
    private val transport: Transport,
    private val catalog: ProjectPersistenceCatalog,
) {
    private val json = Json { prettyPrint = true }

    fun buildSnapshot(): ProjectSnapshot {
        val moduleSnapshots = registry.all().mapNotNull { managed ->
            catalog.codecFor(managed.contract.typeId)?.capture(managed.module)
        }
        val connectionSnapshots = graph.connections().map { connection ->
            ConnectionSnapshot(
                id = connection.id,
                sourceInstanceId = connection.sourceInstanceId,
                sourcePortId = connection.sourcePortId,
                targetInstanceId = connection.targetInstanceId,
                targetPortId = connection.targetPortId,
            )
        }
        val currentTransport = transport.state.value
        return ProjectSnapshot(
            schemaVersion = ProjectSnapshot.CURRENT_SCHEMA_VERSION,
            modules = moduleSnapshots,
            connections = connectionSnapshots.toList(),
            transport = TransportSnapshot(
                tempoBpm = currentTransport.tempoBpm,
                positionBeats = currentTransport.positionBeats,
                isPlaying = currentTransport.isPlaying,
                loopStart = currentTransport.loopStart,
                loopEnd = currentTransport.loopEnd,
            ),
        )
    }

    /** Writes to [path], overwriting it if it already exists (FR-001, FR-012). */
    fun save(path: String) {
        writeProjectFile(path, json.encodeToString(ProjectSnapshot.serializer(), buildSnapshot()))
    }

    /** Writes the current state to a new [path] without touching any previously-saved file (FR-012). */
    fun saveAs(path: String) = save(path)
}
