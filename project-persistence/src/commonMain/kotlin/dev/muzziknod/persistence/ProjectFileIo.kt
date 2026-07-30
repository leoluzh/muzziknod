package dev.muzziknod.persistence

/** Platform-specific raw file I/O boundary (Constitution IV), mirroring 005-sampler-module's `SampleDecoder` split. */
expect fun readProjectFile(path: String): String

/** Overwrites [path] if it already exists. */
expect fun writeProjectFile(path: String, content: String)

/** Reads a referenced sample's raw bytes (e.g. a `SampleZoneSnapshot.sourcePath`). Throws if unreadable. */
expect fun readFileBytes(path: String): ByteArray
