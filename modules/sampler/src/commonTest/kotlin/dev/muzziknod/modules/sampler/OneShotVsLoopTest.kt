package dev.muzziknod.modules.sampler

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

private const val LONG_SAMPLE_LENGTH = 1000 // longer than SamplerModule's 128-sample process() buffer

class OneShotVsLoopTest {

    private fun oneShotModule(
        rootNote: Int = 60,
        lowNote: Int = 0,
        highNote: Int = 127,
        sampleLength: Int = LONG_SAMPLE_LENGTH,
    ): SamplerModule {
        val module = SamplerModule(instanceId = "sampler-1", sampleRate = 48_000, maxVoices = 4)
        module.onLoad()
        val zone = SampleZone(
            sample = Sample("s1", rampData(sampleLength), 48_000),
            rootNote = rootNote,
            lowNote = lowNote,
            highNote = highNote,
            loopMode = LoopMode.OneShot,
        )
        module.addZoneForTesting(zone)
        return module
    }

    @Test
    fun noteOnAtRootPlaysAtUnityGain() {
        val module = oneShotModule(sampleLength = 10)
        val context = FakeProcessContext(listOf(noteOn(60, 127)))
        module.process(context)

        val out = context.lastWritten!!.samples
        val expected = rampData(10)
        for (i in expected.indices) {
            assertTrue(abs(expected[i] - out[i]) <= 1e-4f, "expected ${expected[i]} but was ${out[i]} at index $i")
        }
    }

    @Test
    fun noteOnForUnmappedNoteTriggersNothing() {
        val module = oneShotModule(rootNote = 60, lowNote = 60, highNote = 60)
        val context = FakeProcessContext(listOf(noteOn(61, 127)))
        module.process(context)

        assertTrue(context.lastWritten!!.samples.all { it == 0f })
    }

    @Test
    fun velocityZeroNoteOnIsTreatedAsNoteOff() {
        val module = oneShotModule()
        // First trigger normally, then immediately "note-on vel 0" for the same note in a later cycle.
        module.process(FakeProcessContext(listOf(noteOn(60, 127))))
        module.process(FakeProcessContext(listOf(noteOn(60, 0))))

        // OneShot ignores any note-off (including velocity-0 note-on treated as note-off),
        // so the voice should still be actively playing (not immediately silenced).
        assertTrue(module.voicePoolForTesting().activeVoices.isNotEmpty())
    }

    @Test
    fun oneShotIgnoresNoteOffAndPlaysToCompletion() {
        val module = oneShotModule()
        module.process(FakeProcessContext(listOf(noteOn(60, 127))))
        module.process(FakeProcessContext(listOf(noteOff(60))))

        // Still playing — one-shot voices ignore note-off entirely (US1 AC3).
        assertTrue(module.voicePoolForTesting().activeVoices.isNotEmpty())
    }

    private fun loopModule(rootNote: Int = 60): SamplerModule {
        val module = SamplerModule(instanceId = "sampler-1", sampleRate = 48_000, maxVoices = 4)
        module.onLoad()
        module.addZoneForTesting(
            SampleZone(
                sample = Sample("loop", rampData(50), 48_000),
                rootNote = rootNote,
                loopMode = LoopMode.Loop,
            ),
        )
        return module
    }

    @Test
    fun loopModeSustainsWhileHeldThenReleasesOnNoteOff() {
        val module = loopModule()
        module.process(FakeProcessContext(listOf(noteOn(60, 127))))
        // Still held (no note-off yet) — should be Playing, not fading, after a few cycles.
        module.process(FakeProcessContext())
        assertTrue(
            module.voicePoolForTesting().activeVoices.any { it.state == VoiceState.Playing },
            "loop voice should still be Playing (sustaining) while the note is held",
        )

        module.process(FakeProcessContext(listOf(noteOff(60))))
        assertTrue(
            module.voicePoolForTesting().activeVoices.any { it.state == VoiceState.Releasing } ||
                module.voicePoolForTesting().activeVoices.isEmpty(),
            "loop voice should transition to Releasing (or have already finished fading) on note-off",
        )

        // Render enough cycles for the (short) fade to fully complete.
        repeat(10) { module.process(FakeProcessContext()) }
        assertTrue(
            module.voicePoolForTesting().activeVoices.isEmpty(),
            "loop voice should reach Free after its release fade completes, not sustain indefinitely (US3 AC3)",
        )
    }
}
