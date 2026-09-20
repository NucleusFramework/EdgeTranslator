package dev.nucleusframework.offlinetranslator.engine

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import dev.nucleusframework.offlinetranslator.domain.LlmBackend
import dev.nucleusframework.offlinetranslator.platform.androidContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

internal actual class NativeLlm actual constructor() {
    private var engine: Engine? = null
    private var npuSampler: Boolean = false

    actual fun load(modelPath: String, cacheDir: String, threads: Int, backend: LlmBackend): LlmAccelerator {
        close()
        val nativeLibDir = androidContext().applicationInfo.nativeLibraryDir
        val pick = pickBackend(
            preference = backend,
            gpuKnown = LlmRuntime.gpuAvailable.value,
            npuKnown = LlmRuntime.npuAvailable.value,
            npuWorks = {
                tryOpen(modelPath, cacheSubdir(cacheDir, "npu"), Backend.NPU(nativeLibraryDir = nativeLibDir))
            },
            gpuWorks = {
                tryOpen(modelPath, cacheSubdir(cacheDir, "gpu"), Backend.GPU())
            },
        )
        if (engine == null) {
            engine = openEngine(
                modelPath,
                cacheSubdir(cacheDir, "cpu"),
                Backend.CPU(threadCount = threads.takeIf { it > 0 }),
            )
        }
        npuSampler = pick.accelerator == LlmAccelerator.Npu
        pick.gpuAvailable?.let(LlmRuntime::reportGpuAvailable)
        pick.npuAvailable?.let(LlmRuntime::reportNpuAvailable)
        return pick.accelerator
    }

    private fun tryOpen(modelPath: String, cacheDir: String, backend: Backend): Boolean {
        val opened = runCatching { openEngine(modelPath, cacheDir, backend) }.getOrNull()
        if (opened != null) {
            engine = opened
            return true
        }
        return false
    }

    actual suspend fun generate(
        systemInstruction: String,
        userMessage: String,
        audioWav: ByteArray?,
        image: ByteArray?,
        onPartial: (String) -> Unit,
    ): String {
        val e = engine ?: error("Gemma 4 E2B n'est pas chargé.")
        e.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemInstruction),
                // NPU rejects custom sampler configs (see Google AI Edge Gallery).
                samplerConfig = if (npuSampler) null else SamplerConfig(topK = 1, topP = 1.0, temperature = 0.2),
                thinkingConfig = ThinkingConfig(enableThinking = false),
                channels = emptyList(),
                maxOutputToken = GemmaModel.MAX_OUTPUT_TOKENS,
            ),
        ).use { conversation ->
            val acc = StringBuilder()
            val contents = Contents.of(
                buildList {
                    if (image != null && image.isNotEmpty()) add(Content.ImageBytes(image))
                    add(Content.Text(userMessage))
                    if (audioWav != null && audioWav.isNotEmpty()) add(Content.AudioBytes(audioWav))
                },
            )
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
        npuSampler = false
    }
}

private val nativeLogsConfigured = java.util.concurrent.atomic.AtomicBoolean(false)

/**
 * LiteRT narrates every model load and accelerator teardown at INFO, and warns
 * about an optional WebGPU sampler that ships in no artifact. Keep ERROR and
 * above; logcat already has its own filters for the rest.
 */
private fun quietNativeLogs() {
    if (!nativeLogsConfigured.compareAndSet(false, true)) return
    runCatching { Engine.setNativeMinLogSeverity(LogSeverity.ERROR) }
}

@OptIn(ExperimentalApi::class)
private fun openEngine(modelPath: String, cacheDir: String, backend: Backend): Engine {
    quietNativeLogs()
    ExperimentalFlags.enableSpeculativeDecoding = LlmRuntime.mtp
    val created = Engine(
        EngineConfig(
            modelPath = modelPath,
            backend = backend,
            visionBackend = backend,
            audioBackend = Backend.CPU(),
            cacheDir = cacheDir,
            maxNumTokens = GemmaModel.MAX_NUM_TOKENS,
        ),
    )
    try {
        created.initialize()
        return created
    } catch (t: Throwable) {
        runCatching { created.close() }
        throw t
    }
}

private fun cacheSubdir(cacheDir: String, name: String): String = java.io.File(cacheDir, name).apply { mkdirs() }.absolutePath

internal actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    followRedirects = true
    install(HttpTimeout) {
        requestTimeoutMillis = Long.MAX_VALUE
        socketTimeoutMillis = Long.MAX_VALUE
        connectTimeoutMillis = 30_000
    }
}
