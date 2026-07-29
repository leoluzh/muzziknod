package dev.muzziknod.modules.sampler

import kotlin.test.Test
import kotlin.test.assertTrue

class SampleDecoderAiffTest {

    @Test
    fun decodes16BitAiff() {
        val bytes = TestAudioFixtures.sineAiff(sampleRate = 44_100, bitDepth = 16)
        val decoded = decodeSample(bytes, targetSampleRate = 48_000)
        assertTrue(decoded.samples.isNotEmpty())
        assertTrue(decoded.samples.all { it in -1.0f..1.0f })
    }
}
