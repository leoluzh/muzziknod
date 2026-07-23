package dev.muzziknod.host.graph

data class Connection(
    val id: String,
    val sourceInstanceId: String,
    val sourcePortId: String,
    val targetInstanceId: String,
    val targetPortId: String,
)