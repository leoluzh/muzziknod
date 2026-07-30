package dev.muzziknod.persistence

import dev.muzziknod.host.transport.Transport
import dev.muzziknod.host.transport.TransportState
import dev.muzziknod.persistence.model.TransportSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

/** Capture/restore round trip of tempo/position/loop range/play state (FR-004; SC-002; US2 AC3). */
class TransportPersistenceTest {

    @Test
    fun roundTripsPlayingStateWithLoopRange() {
        val transport = Transport()
        transport.setTempo(140.0)
        transport.setPosition(32.5)
        transport.setLoopRange(8.0, 24.0)
        transport.play()

        val snapshot = transport.state.value.toSnapshot()

        val restored = Transport()
        restored.restore(snapshot.toTransportState())

        assertEquals(transport.state.value, restored.state.value)
    }

    @Test
    fun roundTripsPausedStateWithoutResettingPosition() {
        val transport = Transport()
        transport.setTempo(90.0)
        transport.setPosition(7.0)
        transport.play()
        transport.pause()

        val snapshot = transport.state.value.toSnapshot()
        val restored = Transport()
        restored.restore(snapshot.toTransportState())

        assertEquals(7.0, restored.state.value.positionBeats, 1e-9)
        assertEquals(false, restored.state.value.isPlaying)
    }

    @Test
    fun roundTripsNoLoopRange() {
        val transport = Transport()
        val snapshot = transport.state.value.toSnapshot()
        val restored = Transport()
        restored.restore(snapshot.toTransportState())

        assertEquals(null, restored.state.value.loopStart)
        assertEquals(null, restored.state.value.loopEnd)
    }

    private fun TransportState.toSnapshot() =
        TransportSnapshot(tempoBpm, positionBeats, isPlaying, loopStart, loopEnd)

    private fun TransportSnapshot.toTransportState() =
        TransportState(tempoBpm, positionBeats, isPlaying, loopStart, loopEnd)
}
