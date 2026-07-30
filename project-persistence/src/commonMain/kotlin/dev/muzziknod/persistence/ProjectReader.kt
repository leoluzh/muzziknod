package dev.muzziknod.persistence

import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.host.transport.Transport
import dev.muzziknod.host.transport.TransportState
import dev.muzziknod.persistence.codec.SamplerCodec
import dev.muzziknod.persistence.model.ProjectSnapshot
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SchemaVersionOnly(val schemaVersion: Int)

/**
 * Rebuilds live host state from a project file (FR-007). Never aborts on one bad
 * reference — unresolvable module types or sample files are collected into the
 * returned [ProjectLoadResult.warnings] instead (FR-009, FR-010).
 */
class ProjectReader(
    private val registry: ModuleRegistry,
    private val graph: RoutingGraph,
    private val transport: Transport,
    private val catalog: ProjectPersistenceCatalog,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(path: String): ProjectLoadResult = loadFromContent(readProjectFile(path))

    fun loadFromContent(content: String): ProjectLoadResult {
        val schemaVersion = try {
            json.decodeFromString(SchemaVersionOnly.serializer(), content).schemaVersion
        } catch (e: SerializationException) {
            throw ProjectFileCorruptException(e)
        } catch (e: IllegalArgumentException) {
            throw ProjectFileCorruptException(e)
        }
        if (schemaVersion > ProjectSnapshot.CURRENT_SCHEMA_VERSION) {
            throw UnsupportedProjectFileVersionException(schemaVersion, ProjectSnapshot.CURRENT_SCHEMA_VERSION)
        }

        val snapshot = try {
            json.decodeFromString(ProjectSnapshot.serializer(), content)
        } catch (e: SerializationException) {
            throw ProjectFileCorruptException(e)
        } catch (e: IllegalArgumentException) {
            throw ProjectFileCorruptException(e)
        }

        val warnings = mutableListOf<LoadWarning>()

        for (moduleSnapshot in snapshot.modules) {
            val codec = catalog.codecFor(moduleSnapshot.typeId)
            if (codec == null) {
                warnings += LoadWarning.MissingModuleType(moduleSnapshot.typeId, moduleSnapshot.instanceId)
                continue
            }
            val module = codec.restore(moduleSnapshot.instanceId, moduleSnapshot)
            registry.load(module)
            if (codec is SamplerCodec) {
                warnings += codec.lastRestoreMissingPaths.map { path ->
                    LoadWarning.MissingSampleFile(moduleSnapshot.instanceId, path)
                }
            }
        }

        for (connection in snapshot.connections) {
            graph.connect(
                connection.sourceInstanceId,
                connection.sourcePortId,
                connection.targetInstanceId,
                connection.targetPortId,
            )
        }

        transport.restore(
            TransportState(
                tempoBpm = snapshot.transport.tempoBpm,
                positionBeats = snapshot.transport.positionBeats,
                isPlaying = snapshot.transport.isPlaying,
                loopStart = snapshot.transport.loopStart,
                loopEnd = snapshot.transport.loopEnd,
            ),
        )

        return ProjectLoadResult(warnings)
    }
}
