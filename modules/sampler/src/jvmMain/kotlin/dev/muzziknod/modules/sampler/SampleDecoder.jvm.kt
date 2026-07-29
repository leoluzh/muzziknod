package dev.muzziknod.modules.sampler

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

actual fun decodeSample(bytes: ByteArray, targetSampleRate: Int): DecodedAudio {
    AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { stream ->
        val format = stream.format
        val frameBytes = stream.readAllBytes()
        val mono = decodeToMono(frameBytes, format)
        return DecodedAudio(resampleLinear(mono, format.sampleRate.toDouble(), targetSampleRate))
    }
}

private fun decodeToMono(frameBytes: ByteArray, format: AudioFormat): FloatArray {
    val channels = format.channels
    val bytesPerSample = format.sampleSizeInBits / 8
    val frameSize = bytesPerSample * channels
    val frameCount = frameBytes.size / frameSize
    val order = if (format.isBigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
    val buffer = ByteBuffer.wrap(frameBytes).order(order)

    val mono = FloatArray(frameCount)
    for (frame in 0 until frameCount) {
        var sum = 0f
        for (channel in 0 until channels) {
            val offset = (frame * channels + channel) * bytesPerSample
            sum += readNormalizedSample(buffer, offset, bytesPerSample, format.encoding)
        }
        mono[frame] = sum / channels
    }
    return mono
}

private fun readNormalizedSample(
    buffer: ByteBuffer,
    offset: Int,
    bytesPerSample: Int,
    encoding: AudioFormat.Encoding,
): Float {
    if (encoding == AudioFormat.Encoding.PCM_FLOAT) {
        return buffer.getFloat(offset)
    }

    val order = buffer.order()
    val raw: Long = when (bytesPerSample) {
        1 -> buffer.get(offset).toLong() and 0xFF
        2 -> buffer.getShort(offset).toLong()
        3 -> {
            val b0 = buffer.get(offset).toLong() and 0xFF
            val b1 = buffer.get(offset + 1).toLong() and 0xFF
            val b2 = buffer.get(offset + 2).toLong() and 0xFF
            val unsigned = if (order == ByteOrder.BIG_ENDIAN) (b0 shl 16) or (b1 shl 8) or b2
            else (b2 shl 16) or (b1 shl 8) or b0
            if (unsigned and 0x800000L != 0L) unsigned or -0x1000000L else unsigned
        }
        4 -> buffer.getInt(offset).toLong()
        else -> error("Unsupported sample width: $bytesPerSample bytes")
    }

    val isUnsigned = encoding == AudioFormat.Encoding.PCM_UNSIGNED
    val bits = bytesPerSample * 8
    val maxValue = (1L shl (bits - 1)).toFloat()
    val signedValue = if (isUnsigned) raw - (1L shl (bits - 1)) else raw
    return signedValue / maxValue
}

private fun resampleLinear(source: FloatArray, sourceRate: Double, targetRate: Int): FloatArray {
    if (source.isEmpty() || sourceRate == targetRate.toDouble()) return source
    val ratio = sourceRate / targetRate
    val outLength = (source.size / ratio).toInt().coerceAtLeast(1)
    val out = FloatArray(outLength)
    for (i in out.indices) {
        val position = i * ratio
        val index0 = position.toInt().coerceIn(0, source.size - 1)
        val index1 = (index0 + 1).coerceAtMost(source.size - 1)
        val frac = (position - index0).toFloat()
        out[i] = source[index0] + (source[index1] - source[index0]) * frac
    }
    return out
}
