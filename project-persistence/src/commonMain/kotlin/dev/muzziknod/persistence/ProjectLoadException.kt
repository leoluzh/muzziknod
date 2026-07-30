package dev.muzziknod.persistence

/** A project file could not be loaded at all (distinct from [LoadWarning], which is per-reference). */
sealed class ProjectLoadException(message: String) : Exception(message)

/** `schemaVersion` in the file is newer than this build understands (FR-011). */
class UnsupportedProjectFileVersionException(val fileVersion: Int, val supportedVersion: Int) :
    ProjectLoadException(
        "Unsupported project file version $fileVersion (this build supports up to $supportedVersion)",
    )

/** The file is not valid JSON, or doesn't match the project file shape (FR-008). */
class ProjectFileCorruptException(cause: Throwable) :
    ProjectLoadException("Project file is corrupt or unreadable: ${cause.message}")
