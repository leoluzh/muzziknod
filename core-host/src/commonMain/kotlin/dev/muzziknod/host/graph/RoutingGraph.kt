package dev.muzziknod.host.graph

import dev.muzziknod.host.contract.AudioBuffer
import dev.muzziknod.host.contract.MidiEvent
import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.contract.ProcessContext
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.host.lifecycle.ModuleState

sealed class ConnectResult {
    data class Connected(val connection: Connection) : ConnectResult()
    data class Rejected(val reason: String) : ConnectResult()
}

/**
 * Routes ports between loaded modules and processes them in dependency order each
 * cycle (FR-004–FR-011). Knows nothing about any module's internal behavior — only the
 * [ModuleRegistry]'s bookkeeping and the declared [PortSpec] contracts (Constitution I).
 */
class RoutingGraph(private val registry: ModuleRegistry) {
    private val connections = mutableMapOf<String, Connection>()
    private var nextConnectionId = 0

    // Reused across processCycle() calls instead of reallocated, per Constitution III.
    private val hostProcessContext = HostProcessContext()

    private var cycleInProgress = false
    private val pendingRemovals = mutableSetOf<String>()

    fun connect(
        sourceInstanceId: String,
        sourcePortId: String,
        targetInstanceId: String,
        targetPortId: String,
    ): ConnectResult {
        val sourcePort = resolvePort(sourceInstanceId, sourcePortId)
            ?: return ConnectResult.Rejected("Unknown source port '$sourceInstanceId.$sourcePortId'")
        val targetPort = resolvePort(targetInstanceId, targetPortId)
            ?: return ConnectResult.Rejected("Unknown target port '$targetInstanceId.$targetPortId'")

        if (sourcePort.direction != PortDirection.Output) {
            return ConnectResult.Rejected("Source port '$sourcePortId' is not an Output port")
        }
        if (targetPort.direction != PortDirection.Input) {
            return ConnectResult.Rejected("Target port '$targetPortId' is not an Input port")
        }
        if (sourcePort.type != targetPort.type) {
            return ConnectResult.Rejected(
                "Type mismatch: ${sourcePort.type} output cannot connect to ${targetPort.type} input",
            )
        }
        if (sourcePort.type == PortType.Audio &&
            (sourcePort.sampleRate != targetPort.sampleRate || sourcePort.bufferFormat != targetPort.bufferFormat)
        ) {
            return ConnectResult.Rejected(
                "Sample-rate/buffer-format mismatch between '$sourceInstanceId.$sourcePortId' " +
                    "and '$targetInstanceId.$targetPortId' — no automatic conversion (see research.md)",
            )
        }
        if (wouldCreateCycle(sourceInstanceId, targetInstanceId)) {
            return ConnectResult.Rejected(
                "Connecting '$sourceInstanceId' -> '$targetInstanceId' would create an unsupported feedback cycle",
            )
        }

        val connection = Connection(
            id = "conn-${nextConnectionId++}",
            sourceInstanceId = sourceInstanceId,
            sourcePortId = sourcePortId,
            targetInstanceId = targetInstanceId,
            targetPortId = targetPortId,
        )
        connections[connection.id] = connection
        return ConnectResult.Connected(connection)
    }

    fun disconnect(connectionId: String) {
        connections.remove(connectionId)
    }

    /** Removes every connection touching [instanceId] (FR-009). */
    fun disconnectAllForModule(instanceId: String) {
        connections.values
            .filter { it.sourceInstanceId == instanceId || it.targetInstanceId == instanceId }
            .forEach { connections.remove(it.id) }
    }

    fun connections(): Collection<Connection> = connections.values

    fun incomingConnectionTo(instanceId: String, portId: String): Connection? =
        connections.values.find { it.targetInstanceId == instanceId && it.targetPortId == portId }

    /**
     * Removes [instanceId]: tears down every connection touching it, then drops it from
     * the registry (`onRemove()` runs there) (FR-009). If a cycle is currently in
     * progress, the removal is queued and only takes effect once that `processCycle()`
     * call returns, so a module is never dropped out from under an in-flight cycle
     * (FR-010, US3 AC2).
     */
    fun removeModule(instanceId: String) {
        if (cycleInProgress) {
            pendingRemovals += instanceId
            return
        }
        disconnectAllForModule(instanceId)
        registry.removeImmediately(instanceId)
    }

    /**
     * Runs one processing cycle: topologically sorts active modules and invokes each
     * one's `process()` in dependency order (FR-007). A module whose `process()` throws
     * is marked `Faulted` and skipped in all future cycles — the rest of the graph keeps
     * running (FR-011, research.md resolution). Removals requested during the cycle
     * (FR-010) are applied only after the cycle finishes.
     */
    fun processCycle() {
        cycleInProgress = true
        hostProcessContext.beginCycle()
        try {
            for (instanceId in topologicalOrder()) {
                val managed = registry.get(instanceId) ?: continue
                if (managed.state != ModuleState.Active) continue

                hostProcessContext.currentInstanceId = instanceId
                try {
                    managed.module.process(hostProcessContext)
                } catch (t: Throwable) {
                    managed.state = ModuleState.Faulted
                }
            }
        } finally {
            cycleInProgress = false
        }

        if (pendingRemovals.isNotEmpty()) {
            val toRemove = pendingRemovals.toList()
            pendingRemovals.clear()
            toRemove.forEach { removeModule(it) }
        }
    }

    private fun resolvePort(instanceId: String, portId: String): PortSpec? =
        registry.get(instanceId)?.ports?.find { it.id == portId }

    /** True if adding sourceInstanceId -> targetInstanceId would close a cycle. */
    private fun wouldCreateCycle(sourceInstanceId: String, targetInstanceId: String): Boolean {
        if (sourceInstanceId == targetInstanceId) return true
        val visited = mutableSetOf<String>()
        fun canReach(from: String, goal: String): Boolean {
            if (from == goal) return true
            if (!visited.add(from)) return false
            return connections.values
                .filter { it.sourceInstanceId == from }
                .any { canReach(it.targetInstanceId, goal) }
        }
        // A cycle closes if the target of the new edge can already reach the source.
        return canReach(targetInstanceId, sourceInstanceId)
    }

    /** Kahn's algorithm over every registered module instance, active or not. */
    private fun topologicalOrder(): List<String> {
        val nodes = registry.all().map { it.instanceId }.toMutableSet()
        val inDegree = nodes.associateWith { 0 }.toMutableMap()
        for (connection in connections.values) {
            if (connection.sourceInstanceId in nodes && connection.targetInstanceId in nodes) {
                inDegree[connection.targetInstanceId] = inDegree.getValue(connection.targetInstanceId) + 1
            }
        }

        val ready = ArrayDeque(inDegree.filterValues { it == 0 }.keys.sorted())
        val order = mutableListOf<String>()
        val remainingInDegree = inDegree.toMutableMap()

        while (ready.isNotEmpty()) {
            val node = ready.removeFirst()
            order += node
            connections.values
                .filter { it.sourceInstanceId == node && it.targetInstanceId in nodes }
                .forEach { edge ->
                    val newDegree = remainingInDegree.getValue(edge.targetInstanceId) - 1
                    remainingInDegree[edge.targetInstanceId] = newDegree
                    if (newDegree == 0) ready.addLast(edge.targetInstanceId)
                }
        }
        return order
    }

    private inner class HostProcessContext : ProcessContext {
        var currentInstanceId: String = ""
        private val audioByOutputPort = mutableMapOf<Pair<String, String>, AudioBuffer>()
        private val midiByOutputPort = mutableMapOf<Pair<String, String>, List<MidiEvent>>()

        fun beginCycle() {
            audioByOutputPort.clear()
            midiByOutputPort.clear()
        }

        override fun readAudio(portId: String): AudioBuffer {
            val connection = incomingConnectionTo(currentInstanceId, portId)
                ?: return EMPTY_AUDIO
            return audioByOutputPort[connection.sourceInstanceId to connection.sourcePortId] ?: EMPTY_AUDIO
        }

        override fun writeAudio(portId: String, buffer: AudioBuffer) {
            audioByOutputPort[currentInstanceId to portId] = buffer
        }

        override fun readMidi(portId: String): List<MidiEvent> {
            val connection = incomingConnectionTo(currentInstanceId, portId)
                ?: return emptyList()
            return midiByOutputPort[connection.sourceInstanceId to connection.sourcePortId] ?: emptyList()
        }

        override fun writeMidi(portId: String, events: List<MidiEvent>) {
            midiByOutputPort[currentInstanceId to portId] = events
        }
    }

    private companion object {
        val EMPTY_AUDIO = AudioBuffer(FloatArray(0))
    }
}