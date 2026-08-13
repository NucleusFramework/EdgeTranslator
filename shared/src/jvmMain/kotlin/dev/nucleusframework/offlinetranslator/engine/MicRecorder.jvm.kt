package dev.nucleusframework.offlinetranslator.engine

import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

actual fun createMicRecorder(): MicRecorder = JavaSoundMic()

private class JavaSoundMic : MicRecorder {
    private val format = AudioFormat(MIC_SAMPLE_RATE.toFloat(), 16, 1, true, false)
    private val _levels = MutableStateFlow(List(MIC_BARS) { 0.04f })
    override val levels: StateFlow<List<Float>> = _levels
    override val available: Boolean
        get() = runCatching {
            AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, format))
        }.getOrDefault(false)

    @StructuredScope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var line: TargetDataLine? = null
    private var job: Job? = null
    private val pcm = ByteArrayOutputStream()

    override suspend fun start() {
        stop()
        pcm.reset()
        _levels.value = List(MIC_BARS) { 0.04f }
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val opened = AudioSystem.getLine(info) as TargetDataLine
        opened.open(format)
        opened.start()
        line = opened
        job = scope.launch {
            val chunk = ByteArray(MIC_SAMPLE_RATE / 20 * 2)
            val maxBytes = MIC_SAMPLE_RATE * 2 * (MIC_MAX_MS / 1000)
            while (isActive && pcm.size() < maxBytes) {
                val n = opened.read(chunk, 0, chunk.size)
                if (n <= 0) break
                synchronized(pcm) { pcm.write(chunk, 0, n) }
                val rms = pcm16leRms(chunk, n)
                _levels.value = _levels.value.drop(1) + rms.coerceAtLeast(0.03f)
            }
        }
    }

    override suspend fun stop(): ByteArray = withContext(Dispatchers.IO) {
        val current = line
        line = null
        job?.cancel()
        job = null
        try {
            current?.stop()
            current?.flush()
            current?.close()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        val raw = synchronized(pcm) { pcm.toByteArray() }
        pcm.reset()
        _levels.value = List(MIC_BARS) { 0.04f }
        if (raw.size < MIC_SAMPLE_RATE / 5) ByteArray(0) else pcm16leToWav(raw)
    }
}
