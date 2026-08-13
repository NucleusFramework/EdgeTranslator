package dev.nucleusframework.offlinetranslator.engine

import kotlinx.coroutines.flow.StateFlow

const val MIC_SAMPLE_RATE = 16_000
const val MIC_MAX_MS = 30_000
const val MIC_BARS = 28

interface MicRecorder {
    val available: Boolean
    val levels: StateFlow<List<Float>>
    suspend fun start()
    suspend fun stop(): ByteArray
}

object SilentMic : MicRecorder {
    override val available: Boolean = false
    override val levels: StateFlow<List<Float>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    override suspend fun start() {}
    override suspend fun stop(): ByteArray = ByteArray(0)
}

expect fun createMicRecorder(): MicRecorder

/** 16-bit PCM LE mono → WAV. */
fun pcm16leToWav(pcm: ByteArray, sampleRate: Int = MIC_SAMPLE_RATE, channels: Int = 1): ByteArray {
    val dataSize = pcm.size
    val out = ByteArray(44 + dataSize)
    fun ascii(at: Int, s: String) = s.forEachIndexed { i, c -> out[at + i] = c.code.toByte() }
    fun le16(at: Int, v: Int) {
        out[at] = (v and 0xFF).toByte()
        out[at + 1] = (v shr 8 and 0xFF).toByte()
    }
    fun le32(at: Int, v: Int) {
        out[at] = (v and 0xFF).toByte()
        out[at + 1] = (v shr 8 and 0xFF).toByte()
        out[at + 2] = (v shr 16 and 0xFF).toByte()
        out[at + 3] = (v shr 24 and 0xFF).toByte()
    }
    ascii(0, "RIFF")
    le32(4, 36 + dataSize)
    ascii(8, "WAVE")
    ascii(12, "fmt ")
    le32(16, 16)
    le16(20, 1)
    le16(22, channels)
    le32(24, sampleRate)
    le32(28, sampleRate * channels * 2)
    le16(32, channels * 2)
    le16(34, 16)
    ascii(36, "data")
    le32(40, dataSize)
    pcm.copyInto(out, 44)
    return out
}

fun pcm16leRms(buf: ByteArray, length: Int): Float {
    if (length < 2) return 0f
    var sum = 0.0
    var n = 0
    var i = 0
    while (i + 1 < length) {
        val s = (buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)
        val sample = s.toShort().toInt()
        sum += sample.toDouble() * sample
        n++
        i += 2
    }
    if (n == 0) return 0f
    return (kotlin.math.sqrt(sum / n) / 32768.0).toFloat().coerceIn(0f, 1f)
}
