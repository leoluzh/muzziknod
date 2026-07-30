package dev.muzziknod.persistence.model

import kotlinx.serialization.Serializable

/** Mirrors `core-host`'s `Connection` one-to-one (data-model.md "ConnectionSnapshot"). */
@Serializable
data class ConnectionSnapshot(
    val id: String,
    val sourceInstanceId: String,
    val sourcePortId: String,
    val targetInstanceId: String,
    val targetPortId: String,
)
