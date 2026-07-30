package dev.muzziknod.persistence

import dev.muzziknod.persistence.codec.ModuleStateCodec

/**
 * `typeId -> ModuleStateCodec` lookup (contracts/module-state-codec.md), mirroring
 * `ui-desktop`'s `ModuleCatalog` shape but for codecs instead of instance factories.
 * `defaultProjectPersistenceCatalog()` (added alongside the shipped codecs) is the
 * production entry point; this constructor stays open for tests that only need a
 * subset of codecs.
 */
class ProjectPersistenceCatalog(private val codecs: Map<String, ModuleStateCodec>) {
    fun codecFor(typeId: String): ModuleStateCodec? = codecs[typeId]
}
