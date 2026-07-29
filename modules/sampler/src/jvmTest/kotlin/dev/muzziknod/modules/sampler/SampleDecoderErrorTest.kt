package dev.muzziknod.modules.sampler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleDecoderErrorTest {

    private val badInputs: List<ByteArray> = listOf(
        ByteArray(0), // "missing" — no usable bytes
        TestAudioFixtures.corruptBytes(), // truncated/corrupt
        "not an audio file at all, just plain text bytes".encodeToByteArray(), // unsupported format
    )

    @Test
    fun loadSampleReturnsFailedForBadInputsWithinOneSecondAndNeverThrows() {
        val module = SamplerModule(instanceId = "sampler-1")
        module.onLoad()

        for (bytes in badInputs) {
            repeat(20) {
                val start = System.nanoTime()
                val result = module.loadSample(bytes, id = "bad", rootNote = 60)
                val elapsedMs = (System.nanoTime() - start) / 1_000_000

                assertTrue(result is SampleLoadResult.Failed, "expected Failed for ${bytes.size}-byte input, got $result")
                assertTrue(elapsedMs < 1000, "loadSample took ${elapsedMs}ms, expected <1000ms")
            }
        }

        assertEquals(0, module.zones.size, "zones must stay unchanged after only failed loads")
    }
}
