package dev.nucleusframework.offlinetranslator.engine

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import dev.nucleusframework.nativehttp.ktor.installNativeSsl
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

internal actual class NativeLlm actual constructor() {
    private var engine: Engine? = null

    actual fun load(modelPath: String, cacheDir: String, threads: Int): LlmAccelerator {
        close()
        val gpu = runCatching { openEngine(modelPath, cacheSubdir(cacheDir, "gpu"), Backend.GPU()) }.getOrNull()
        if (gpu != null) {
            engine = gpu
            return LlmAccelerator.Gpu
        }
        engine = openEngine(
            modelPath,
            cacheSubdir(cacheDir, "cpu"),
            Backend.CPU(threadCount = threads.takeIf { it > 0 }),
        )
        return LlmAccelerator.Cpu
    }

    actual suspend fun generate(
        systemInstruction: String,
        userMessage: String,
        audioWav: ByteArray?,
        onPartial: (String) -> Unit,
    ): String {
        val e = engine ?: error("Gemma 4 E2B n'est pas chargé.")
        e.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemInstruction),
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.2),
                thinkingConfig = ThinkingConfig(enableThinking = false),
                channels = emptyList(),
                maxOutputToken = 1024,
            )
        ).use { conversation ->
            val acc = StringBuilder()
            val contents = if (audioWav == null || audioWav.isEmpty()) {
                Contents.of(userMessage)
            } else {
                Contents.of(Content.Text(userMessage), Content.AudioBytes(audioWav))
            }
            try {
                conversation.sendMessageAsync(contents).collect { chunk ->
                    acc.append(chunk.toString())
                    onPartial(acc.toString())
                }
            } catch (t: Throwable) {
                runCatching { conversation.cancelProcess() }
                throw t
            }
            return acc.toString()
        }
    }

    actual fun close() {
        try {
            engine?.close()
        } catch (_: Exception) {
        }
        engine = null
    }
}

private fun openEngine(modelPath: String, cacheDir: String, backend: Backend): Engine {
    val created = Engine(
        EngineConfig(
            modelPath = modelPath,
            backend = backend,
            audioBackend = Backend.CPU(),
            cacheDir = cacheDir,
            maxNumTokens = GemmaModel.MAX_NUM_TOKENS,
        )
    )
    try {
        created.initialize()
        return created
    } catch (t: Throwable) {
        runCatching { created.close() }
        throw t
    }
}

private fun cacheSubdir(cacheDir: String, name: String): String =
    java.io.File(cacheDir, name).apply { mkdirs() }.absolutePath

internal actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    followRedirects = true
    installNativeSsl()
    install(HttpTimeout) {
        requestTimeoutMillis = Long.MAX_VALUE
        socketTimeoutMillis = Long.MAX_VALUE
        connectTimeoutMillis = 30_000
    }
}
