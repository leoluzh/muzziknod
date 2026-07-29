package dev.muzziknod.modules.sampler

enum class LoopMode { OneShot, Loop }

/** Decoder output: mono PCM, normalized to -1.0..1.0, already resampled to the host's operating sample rate. */
data class DecodedAudio(val samples: FloatArray)

data class Sample(
    val id: String,
    val data: FloatArray,
    val sourceSampleRate: Int,
)
