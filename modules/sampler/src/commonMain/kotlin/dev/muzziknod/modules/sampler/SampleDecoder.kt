package dev.muzziknod.modules.sampler

/**
 * Platform-specific WAV/AIFF decode boundary (Constitution IV). Decodes [bytes],
 * downmixes to mono, and resamples to [targetSampleRate]. Throws on a missing,
 * corrupt, or unsupported file — callers (SamplerModule.loadSample) turn that into
 * a reported [dev.muzziknod.modules.sampler.SampleLoadResult.Failed], never a crash.
 */
expect fun decodeSample(bytes: ByteArray, targetSampleRate: Int): DecodedAudio
