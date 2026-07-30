package dev.muzziknod.persistence.codec

import dev.muzziknod.host.contract.Module
import dev.muzziknod.persistence.model.ModuleSnapshot

/**
 * Plugin point a product module implements to participate in project persistence
 * (contracts/module-state-codec.md). Lives here, not in `core-host`, so the `Module`/
 * `ModuleContract` core contract stays untouched (research.md "Parameter capture
 * strategy").
 */
interface ModuleStateCodec {
    val typeId: String

    /** Reads [module]'s already-public API into a [ModuleSnapshot] — never its internals. */
    fun capture(module: Module): ModuleSnapshot

    /** Builds a new instance with [instanceId], applying every field [snapshot] carries. */
    fun restore(instanceId: String, snapshot: ModuleSnapshot): Module
}
