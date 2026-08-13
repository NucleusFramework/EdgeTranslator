package dev.nucleusframework.offlinetranslator.engine

import io.github.jvoiceproject.piperjni.PiperJNI
import io.github.jvoiceproject.piperjni.PiperVoice
import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineEvent
import javax.sound.sampled.SourceDataLine

actual fun createTtsSpeaker(http: HttpClient): TtsSpeaker = PiperTts()

private class PiperTts : TtsSpeaker {
    private val mutex = Mutex()
    private var piper: PiperJNI? = null
    private var voice: PiperVoice? = null
    private var voiceLang: String? = null

    @Volatile private var clip: Clip? = null

    @Volatile private var playing = false

    @Volatile private var paused = false

    @Volatile private var closed = false

    @Volatile private var primed = false

    override val available: Boolean = true

    init {
        // Ort::Env aborts in atexit if the native voice map is still populated.
        Runtime.getRuntime().addShutdownHook(Thread { close() })
    }

    override fun canSpeak(lang: String): Boolean = PiperVoices.forLang(lang).any { it.isOnDisk() }

    override suspend fun speak(text: String, lang: String, voiceId: String?, onReady: () -> Unit) {
        if (closed || text.isBlank()) return
        val spec = voiceId?.let { PiperVoices.of(it) }?.takeIf { it.isOnDisk() }
            ?: PiperVoices.defaultFor(lang)?.takeIf { it.isOnDisk() }
            ?: PiperVoices.forLang(lang).firstOrNull { it.isOnDisk() }
            ?: error("No Piper voice for $lang")
        stop()
        mutex.withLock {
            if (closed) return
            val engine = ensurePiper()
            val loaded = ensureVoice(engine, spec, spec.id)
            var samples = engine.textToAudio(loaded, text)
            if (samples.isEmpty()) samples = engine.textToAudio(loaded, text)
            play(samples, loaded.sampleRate, onReady)
        }
    }

    override fun pause() {
        paused = true
        try {
            clip?.stop()
        } catch (_: Exception) {
        }
    }

    override fun resume() {
        paused = false
        try {
            clip?.start()
        } catch (_: Exception) {
        }
    }

    override fun stop() {
        playing = false
        paused = false
        try {
            clip?.stop()
            clip?.flush()
            clip?.close()
        } catch (_: Exception) {
        }
        clip = null
    }

    override fun unload() {
        stop()
        try {
            voice?.close()
        } catch (_: Exception) {
        }
        voice = null
        voiceLang = null
        try {
            piper?.close()
        } catch (_: Exception) {
        }
        piper = null
    }

    override fun close() {
        if (closed) return
        closed = true
        unload()
    }

    private fun ensurePiper(): PiperJNI {
        val current = piper
        if (current != null) return current
        val next = PiperJNI()
        next.initialize(true)
        piper = next
        return next
    }

    private fun ensureVoice(engine: PiperJNI, spec: PiperVoiceSpec, lang: String): PiperVoice {
        if (voiceLang == lang) voice?.let { return it }
        voice?.close()
        voice = null
        voiceLang = null
        val loaded = engine.loadVoice(Path.of(spec.destOnnx()), Path.of(spec.destJson()))
        voice = loaded
        voiceLang = lang
        return loaded
    }

    private fun play(samples: ShortArray, sampleRate: Int, onReady: () -> Unit) {
        if (samples.isEmpty()) return
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        prime(format)
        val bytes = pcm(samples)
        val out = AudioSystem.getClip()
        out.open(format, bytes, 0, bytes.size)
        val done = CountDownLatch(1)
        out.addLineListener { ev ->
            if (ev.type == LineEvent.Type.STOP && !paused) done.countDown()
        }
        clip = out
        playing = true
        paused = false
        out.start()
        onReady()
        while (playing) {
            if (done.await(50, TimeUnit.MILLISECONDS)) break
        }
        playing = false
        try {
            out.close()
        } catch (_: Exception) {
        }
        if (clip === out) clip = null
    }

    /**
     * PulseAudio / PipeWire drops the first Java Sound stream. Burn it on silence
     * so the utterance opens a live sink.
     */
    private fun prime(format: AudioFormat) {
        if (primed) return
        primed = true
        val info = DataLine.Info(SourceDataLine::class.java, format)
        val out = AudioSystem.getLine(info) as SourceDataLine
        val silence = (format.sampleRate.toInt() * 2 / 10).coerceAtLeast(2048)
        out.open(format, silence.coerceAtLeast(8192))
        try {
            out.start()
            out.write(ByteArray(silence), 0, silence)
            out.drain()
        } finally {
            try {
                out.stop()
                out.flush()
                out.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun pcm(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        var i = 0
        var b = 0
        while (i < samples.size) {
            val s = samples[i++].toInt()
            bytes[b++] = (s and 0xFF).toByte()
            bytes[b++] = (s shr 8).toByte()
        }
        return bytes
    }
}
