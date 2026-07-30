package dev.muzziknod.persistence.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * One module instance (data-model.md "ModuleSnapshot"). [typeId] is used to look up a
 * `ModuleStateCodec` on load; [moduleData] carries module-type-specific payloads beyond
 * flat [parameters] (e.g. sampler zones) and is `null` for module types that don't need
 * one.
 */
@Serializable
data class ModuleSnapshot(
    val instanceId: String,
    val typeId: String,
    val parameters: Map<String, Double> = emptyMap(),
    val moduleData: JsonElement? = null,
)
