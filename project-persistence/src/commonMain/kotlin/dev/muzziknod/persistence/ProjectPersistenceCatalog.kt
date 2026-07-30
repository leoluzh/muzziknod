package dev.muzziknod.persistence

import dev.muzziknod.persistence.codec.DelayCodec
import dev.muzziknod.persistence.codec.DistortionCodec
import dev.muzziknod.persistence.codec.EqCodec
import dev.muzziknod.persistence.codec.MidiSequencerCodec
import dev.muzziknod.persistence.codec.ModuleStateCodec
import dev.muzziknod.persistence.codec.ReverbCodec
import dev.muzziknod.persistence.codec.SamplerCodec

/**
 * `typeId -> ModuleStateCodec` lookup (contracts/module-state-codec.md), mirroring
 * `ui-desktop`'s `ModuleCatalog` shape but for codecs instead of instance factories.
 * [defaultProjectPersistenceCatalog] is the production entry point; this constructor
 * stays open for tests that only need a subset of codecs.
 */
class ProjectPersistenceCatalog(private val codecs: Map<String, ModuleStateCodec>) {
    fun codecFor(typeId: String): ModuleStateCodec? = codecs[typeId]
}

/** Every shipped product-module codec (contracts/module-state-codec.md "Shipped codecs"). */
fun defaultProjectPersistenceCatalog(): ProjectPersistenceCatalog {
    val codecs = listOf(
        DelayCodec(),
        ReverbCodec(),
        DistortionCodec(),
        EqCodec(),
        MidiSequencerCodec(),
        SamplerCodec(),
    )
    return ProjectPersistenceCatalog(codecs.associateBy { it.typeId })
}
