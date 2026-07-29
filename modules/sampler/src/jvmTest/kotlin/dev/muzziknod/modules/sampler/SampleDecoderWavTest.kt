package dev.muzziknod.modules.sampler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleDecoderWavTest {

    @Test
    fun decodes16BitWavAtVariousSampleRates() {
        for (sampleRate in listOf(44_100, 48_000, 96_000)) {
            val bytes = TestAudioFixtures.sineWav(sampleRate, bitDepth = 16)
            val decoded = decodeSample(bytes, targetSampleRate = 48_000)
            assertTrue(decoded.samples.isNotEmpty(), "expected non-empty decode at source rate $sampleRate")
        }
    }

    @Test
    fun decodes24BitWav() {
        val bytes = TestAudioFixtures.sineWav(48_000, bitDepth = 24)
        val decoded = decodeSample(bytes, targetSampleRate = 48_000)
        assertTrue(decoded.samples.isNotEmpty())
        assertTrue(decoded.samples.all { it in -1.0f..1.0f })
    }

    @Test
    fun resamplesToTargetSampleRate() {
        val bytes = TestAudioFixtures.sineWav(44_100, bitDepth = 16, durationSamples = 4410)
        val decoded = decodeSample(bytes, targetSampleRate = 22_050)
        // Downsampled by half -> roughly half the frame count.
        assertEquals(2205, decoded.samples.size, absoluteDelta = 5)
    }
}

private fun assertEquals(expected: Int, actual: Int, absoluteDelta: Int) {
    assertTrue(kotlin.math.abs(expected - actual) <= absoluteDelta, "expected ~$expected but was $actual")
}
