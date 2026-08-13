package dev.nucleusframework.offlinetranslator.engine

import io.github.jvoiceproject.piperjni.PiperJNI
import io.github.jvoiceproject.piperjni.PiperVoice
import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.withLock

actual fun createTtsSpeaker(http: HttpClient): TtsSpeaker = PiperTts()

private class PiperTts : TtsSpeaker {
    private val mutex = Mutex()
    private var piper: PiperJNI? = null
    private var voice: PiperVoice? = null
    private var voiceLang: String? = null

    @Volatile private var line: SourceDataLine? = null

    @Volatile private var playing = false

    @Volatile private var paused = false

    @Volatile private var closed = false

    private val gate = ReentrantLock()
    private val unpaused = gate.newCondition()

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
            val samples = engine.textToAudio(loaded, text)
            onReady()
            play(samples, loaded.sampleRate)
        }
    }

    override fun pause() {
        paused = true
        try {
            line?.stop()
        } catch (_: Exception) {
        }
    }

    override fun resume() {
        paused = false
        try {
            line?.start()
        } catch (_: Exception) {
        }
        gate.withLock { unpaused.signalAll() }
    }

    override fun stop() {
        playing = false
        paused = false
        gate.withLock { unpaused.signalAll() }
        try {
            line?.stop()
            line?.flush()
            line?.close()
        } catch (_: Exception) {
        }
        line = null
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

    private fun play(samples: ShortArray, sampleRate: Int) {
        if (samples.isEmpty()) return
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val info = DataLine.Info(SourceDataLine::class.java, format)
        val out = AudioSystem.getLine(info) as SourceDataLine
        out.open(format)
        out.start()
        line = out
        playing = true
        val buf = ByteArray(4096)
        var i = 0
        while (playing && i < samples.size) {
            gate.withLock {
                while (paused && playing) {
                    try {
                        unpaused.await()
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return
                    }
                }
            }
            if (!playing) break
            var b = 0
            while (b + 1 < buf.size && i < samples.size) {
                val s = samples[i].toInt()
                buf[b++] = (s and 0xFF).toByte()
                buf[b++] = (s shr 8).toByte()
                i++
            }
            try {
                out.write(buf, 0, b)
            } catch (err: Exception) {
                if (playing) throw err
                break
            }
        }
        if (playing) out.drain()
        playing = false
        out.stop()
        out.close()
        if (line === out) line = null
    }
}
