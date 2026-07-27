package dev.muzziknod.ui.transport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.muzziknod.modules.midisequencer.TransportState

/**
 * Play/stop controls for a module exposing transport state (e.g.
 * `MidiSequencerModule`) (contracts/ui-composables-contract.md; US2).
 */
@Composable
fun TransportControls(
    transportState: TransportState,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    enabled: Boolean = true,
) {
    Row(modifier = Modifier.testTag("transport-controls")) {
        Text(
            text = "Play",
            modifier = Modifier
                .testTag("transport-play")
                .clickable(enabled = enabled && !transportState.isPlaying, onClick = onPlay),
        )
        Text(
            text = "Stop",
            modifier = Modifier
                .testTag("transport-stop")
                .clickable(enabled = enabled && transportState.isPlaying, onClick = onStop),
        )
        Text(
            text = if (transportState.isPlaying) "Tocando — passo ${transportState.currentStep}" else "Parado",
            modifier = Modifier.testTag("transport-status"),
        )
    }
}
