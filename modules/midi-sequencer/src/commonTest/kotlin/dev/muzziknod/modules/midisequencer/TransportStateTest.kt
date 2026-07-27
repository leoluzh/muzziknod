package dev.muzziknod.modules.midisequencer

import kotlin.test.Test
import kotlin.test.assertEquals

class TransportStateTest {
    @Test
    fun transportStateMatchesIsPlayingAndCurrentStepAfterPlay() {
        val module = MidiSequencerModule("seq-1")

        module.play()

        assertEquals(module.isPlaying, module.transportState.value.isPlaying)
        assertEquals(module.currentStep, module.transportState.value.currentStep)
        assertEquals(true, module.transportState.value.isPlaying)
    }

    @Test
    fun transportStateMatchesIsPlayingAndCurrentStepAfterStop() {
        val module = MidiSequencerModule("seq-1")
        module.play()

        module.stop()

        assertEquals(module.isPlaying, module.transportState.value.isPlaying)
        assertEquals(false, module.transportState.value.isPlaying)
    }

    @Test
    fun transportStateMatchesCurrentStepAfterEachStepAdvance() {
        val module = MidiSequencerModule("seq-1")
        module.setLength(3)
        module.play()
        val context = CapturingProcessContext()

        module.process(context)
        assertEquals(module.currentStep, module.transportState.value.currentStep)
        assertEquals(1, module.transportState.value.currentStep)

        module.process(context)
        assertEquals(module.currentStep, module.transportState.value.currentStep)
        assertEquals(2, module.transportState.value.currentStep)
    }
}
