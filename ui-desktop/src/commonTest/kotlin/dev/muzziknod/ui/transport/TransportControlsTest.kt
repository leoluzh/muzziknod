package dev.muzziknod.ui.transport

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.muzziknod.modules.midisequencer.TransportState
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TransportControlsTest {
    @Test
    fun stoppedStateShowsStoppedAndClickingPlayInvokesOnPlay() = runComposeUiTest {
        var playCalled = false

        setContent {
            TransportControls(
                transportState = TransportState(isPlaying = false, currentStep = 0),
                onPlay = { playCalled = true },
                onStop = { error("not used") },
            )
        }

        onNodeWithTag("transport-status").assertTextContains("Parado")
        onNodeWithTag("transport-play").performClick()

        assertTrue(playCalled)
    }

    @Test
    fun playingStateShowsCurrentStepAndClickingStopInvokesOnStop() = runComposeUiTest {
        var stopCalled = false

        setContent {
            TransportControls(
                transportState = TransportState(isPlaying = true, currentStep = 3),
                onPlay = { error("not used") },
                onStop = { stopCalled = true },
            )
        }

        onNodeWithTag("transport-status").assertTextContains("3", substring = true)
        onNodeWithTag("transport-stop").performClick()

        assertTrue(stopCalled)
    }
}
