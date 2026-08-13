package dev.nucleusframework.offlinetranslator.engine

import dev.nucleusframework.offlinetranslator.domain.Languages
import dev.nucleusframework.offlinetranslator.domain.LlmBackend
import dev.nucleusframework.offlinetranslator.platform.IoDispatcher
import dev.nucleusframework.offlinetranslator.platform.Platform
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class)
@Inject
class GemmaTranslator(
    private val exists: (String) -> Boolean = { Platform.exists(it) },
    private val now: () -> Long = { Platform.now() },
    private val threads: () -> Int = { Platform.cpuCount() },
    private val cacheDir: () -> String = { GemmaModel.cacheDir() },
) : Translator {
    private val sessionFactory: () -> NativeLlm = { NativeLlm() }
    private val mutex = Mutex()
    private var session: NativeLlm? = null
    private var loadedPath: String? = null
    private var loadedBackend: LlmBackend? = null

    override suspend fun translate(request: TranslationRequest): TranslationResult {
        val path = request.modelPath
        if (path.isBlank() || !exists(path)) return TranslationResult.Unavailable
        return mutex.withLock {
            try {
                val llm = ensureLoaded(path)
                val start = now()
                val audio = request.audioWav
                val image = request.image
                if (image != null && image.isNotEmpty()) {
                    val prompt = buildImagePrompt(request)
                    val raw = llm.generate(prompt.system, prompt.user, image = image)
                    val targetName = Languages.get(request.targetLang)?.nameEn ?: request.targetLang
                    val (src, tgt) = parseImageOutput(raw, targetName)
                    TranslationResult.Ok(
                        text = tgt,
                        transcription = src,
                        latencyMs = now() - start,
                    )
                } else if (audio != null && audio.isNotEmpty()) {
                    val prompt = buildAudioPrompt(request)
                    val raw = llm.generate(prompt.system, prompt.user, audioWav = audio)
                    val targetName = Languages.get(request.targetLang)?.nameEn ?: request.targetLang
                    val (src, tgt) = parseSpeechOutput(raw, targetName)
                    TranslationResult.Ok(
                        text = tgt,
                        transcription = src,
                        latencyMs = now() - start,
                    )
                } else {
                    val prompt = buildTranslationPrompt(request)
                    val raw = llm.generate(prompt.system, prompt.user) { partial ->
                        request.onPartial(prompt.restore(cleanModelOutput(partial)))
                    }
                    TranslationResult.Ok(
                        text = prompt.restore(cleanModelOutput(raw)),
                        latencyMs = now() - start,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TranslationResult.Error(e.message)
            }
        }
    }

    override suspend fun preload(path: String) {
        if (path.isBlank()) return
        withContext(IoDispatcher) {
            if (!exists(path)) return@withContext
            mutex.withLock { ensureLoaded(path) }
        }
    }

    override suspend fun release() {
        mutex.withLock { clearSession() }
    }

    override fun close() {
        clearSession()
    }

    private fun clearSession() {
        session?.close()
        session = null
        loadedPath = null
        loadedBackend = null
        LlmRuntime.report(LlmAccelerator.None)
    }

    private fun ensureLoaded(path: String): NativeLlm {
        val pref = LlmRuntime.preference
        val current = session
        if (current != null && loadedPath == path && loadedBackend == pref) return current
        current?.close()
        session = null
        loadedPath = null
        loadedBackend = null
        val dir = cacheDir()
        Platform.mkdir(dir)
        val next = sessionFactory()
        try {
            val used = next.load(path, dir, threads().coerceAtLeast(1), pref)
            session = next
            loadedPath = path
            loadedBackend = pref
            LlmRuntime.report(used)
            return next
        } catch (t: Throwable) {
            next.close()
            LlmRuntime.report(LlmAccelerator.None)
            throw t
        }
    }
}
