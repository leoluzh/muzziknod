package dev.muzziknod.persistence.model

import kotlinx.serialization.Serializable

/** Root of a project file (data-model.md "ProjectSnapshot"; contracts/project-file-schema.md). */
@Serializable
data class ProjectSnapshot(
    val schemaVersion: Int,
    val modules: List<ModuleSnapshot>,
    val connections: List<ConnectionSnapshot>,
    val transport: TransportSnapshot,
) {
    companion object {
        /** The only schema version this build writes and understands (FR-011). */
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}
