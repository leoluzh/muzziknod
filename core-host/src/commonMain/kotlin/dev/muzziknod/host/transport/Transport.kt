package dev.muzziknod.host.transport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Project-wide playback state (spec 006-project-persistence FR-004). */
data class TransportState(
    val tempoBpm: Double = 120.0,
    val positionBeats: Double = 0.0,
    val isPlaying: Boolean = false,
    val loopStart: Double? = null,
    val loopEnd: Double? = null,
)

/**
 * Host-owned, control-plane-only playback state — tempo, position, loop range, and
 * play/pause/stop — shared by the whole project rather than any single module
 * (research.md "Transport"). Never touched from a module's `process()`; alongside
 * [dev.muzziknod.host.lifecycle.ModuleRegistry] and [dev.muzziknod.host.graph.RoutingGraph]
 * as the third piece of host-owned state.
 */
class Transport {
    private var current = TransportState()

    private val _state = MutableStateFlow(current)
    val state: StateFlow<TransportState> = _state.asStateFlow()

    fun play() {
        update { it.copy(isPlaying = true) }
    }

    fun pause() {
        update { it.copy(isPlaying = false) }
    }

    fun stop() {
        update { it.copy(isPlaying = false, positionBeats = 0.0) }
    }

    fun setTempo(bpm: Double) {
        require(bpm > 0.0) { "Tempo must be positive, got $bpm" }
        update { it.copy(tempoBpm = bpm) }
    }

    fun setPosition(beats: Double) {
        require(beats >= 0.0) { "Position must be non-negative, got $beats" }
        update { it.copy(positionBeats = beats) }
    }

    /** Both null (no loop) or both non-null — a half-specified range is rejected. */
    fun setLoopRange(start: Double?, end: Double?) {
        require((start == null) == (end == null)) {
            "loopStart and loopEnd must both be null or both be set"
        }
        if (start != null && end != null) {
            require(end > start) { "loopEnd ($end) must be after loopStart ($start)" }
        }
        update { it.copy(loopStart = start, loopEnd = end) }
    }

    private inline fun update(transform: (TransportState) -> TransportState) {
        current = transform(current)
        _state.value = current
    }
}
