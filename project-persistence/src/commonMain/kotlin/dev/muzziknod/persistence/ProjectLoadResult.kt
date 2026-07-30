package dev.muzziknod.persistence

/** One reference `ProjectReader.load()` could not restore, but did not abort for (FR-009, FR-010). */
sealed class LoadWarning {
    data class MissingModuleType(val typeId: String, val instanceId: String) : LoadWarning()
    data class MissingSampleFile(val instanceId: String, val sourcePath: String) : LoadWarning()
}

/**
 * Result of `ProjectReader.load()` — always finishes building whatever it could resolve;
 * [warnings] reports what it couldn't (data-model.md "LoadWarning").
 */
data class ProjectLoadResult(
    val warnings: List<LoadWarning> = emptyList(),
)
