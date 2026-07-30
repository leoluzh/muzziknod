package dev.muzziknod.persistence.model

import kotlinx.serialization.Serializable

/**
 * Mirrors `core-host`'s `Transport.TransportState` one-to-one (data-model.md
 * "TransportSnapshot"; FR-004). [loopStart]/[loopEnd] are both present or both `null`.
 */
@Serializable
data class TransportSnapshot(
    val tempoBpm: Double,
    val positionBeats: Double,
    val isPlaying: Boolean,
    val loopStart: Double?,
    val loopEnd: Double?,
)
