package dev.muzziknod.persistence.model

import kotlinx.serialization.Serializable

/**
 * `ModuleSnapshot.moduleData` payload for `typeId = "sampler"` (data-model.md
 * "SamplerData"; FR-005).
 */
@Serializable
data class SamplerData(
    val zones: List<SampleZoneSnapshot>,
)

/**
 * One sampler zone (data-model.md "SampleZoneSnapshot"). [sourcePath] is the external
 * reference this feature persists instead of embedding audio (FR-006); it is `null`
 * only for zones loaded without a path (e.g. programmatically, in tests).
 */
@Serializable
data class SampleZoneSnapshot(
    val sourcePath: String?,
    val sampleId: String,
    val rootNote: Int,
    val lowNote: Int,
    val highNote: Int,
    val gain: Double,
    val loopMode: String,
)
