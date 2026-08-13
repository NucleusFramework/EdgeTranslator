package dev.nucleusframework.offlinetranslator.engine

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
// litertlm-jvm 0.14.0 has no ThinkingConfig / maxOutputToken (added in 0.15+).
// import com.google.ai.edge.litertlm.ThinkingConfig
import dev.nucleusframework.nativehttp.ktor.installNativeSsl
import dev.nucleusframework.offlinetranslator.domain.LlmBackend
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

internal actual class NativeLlm actual constructor() {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var conversationSystem: String? = null

    actual fun load(modelPath: String, cacheDir: String, threads: Int, backend: LlmBackend): LlmAccelerator {
        close()
        loadGpuNativeLibs()
        val pick = pickBackend(backend, LlmRuntime.gpuAvailable.value) {
            val gpu = runCatching { openEngine(modelPath, cacheSubdir(cacheDir, "gpu"), Backend.GPU()) }.getOrNull()
            if (gpu != null) {
                engine = gpu
                true
            } else {
                false
            }
        }
        if (engine == null) {
            engine = openEngine(
                modelPath,
                cacheSubdir(cacheDir, "cpu"),
                Backend.CPU(threadCount = threads.takeIf { it > 0 }),
            )
        }
        pick.gpuAvailable?.let(LlmRuntime::reportGpuAvailable)
        return pick.accelerator
    }

    actual suspend fun generate(
        systemInstruction: String,
        userMessage: String,
        audioWav: ByteArray?,
        image: ByteArray?,
        onPartial: (String) -> Unit,
    ): String {
        val e = engine ?: error("Gemma 4 E2B n'est pas chargé.")
        val conv = conversation?.takeIf { conversationSystem == systemInstruction }
            ?: openConversation(e, systemInstruction)
        val acc = StringBuilder()
        val contents = Contents.of(
            buildList {
                if (image != null && image.isNotEmpty()) add(Content.ImageBytes(image))
                add(Content.Text(userMessage))
                if (audioWav != null && audioWav.isNotEmpty()) add(Content.AudioBytes(audioWav))
            },
        )
        try {
            conv.sendMessageAsync(contents).collect { chunk ->
                acc.append(chunk.toString())
                onPartial(acc.toString())
            }
        } catch (t: Throwable) {
            if (!linuxNativeTeardownUnsafe()) runCatching { conv.cancelProcess() }
            throw t
        }
        return acc.toString()
    }

    actual fun close() {
        releaseConversation()
        if (!linuxNativeTeardownUnsafe()) {
            try {
                engine?.close()
            } catch (_: Exception) {
            }
        }
        engine = null
    }

    private fun openConversation(engine: Engine, systemInstruction: String): Conversation {
        releaseConversation()
        val next = engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemInstruction),
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.2),
                // 0.14.0 ConversationConfig: thinkingConfig / maxOutputToken do not exist yet.
                // thinkingConfig = ThinkingConfig(enableThinking = false),
                channels = emptyList(),
                // maxOutputToken = 1024,
            ),
        )
        conversation = next
        conversationSystem = systemInstruction
        return next
    }

    private fun releaseConversation() {
        val current = conversation
        conversation = null
        conversationSystem = null
        // Official Kotlin samples keep the conversation open. nativeDeleteConversation
        // after a GPU turn SIGILL's in NVIDIA's shader compiler (libnvidia-gpucomp);
        // same "prints then dies" shape as LiteRT-LM#2570.
        if (current != null && !linuxNativeTeardownUnsafe()) {
            runCatching { current.close() }
        }
    }
}

private fun openEngine(modelPath: String, cacheDir: String, backend: Backend): Engine {
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
        if (!linuxNativeTeardownUnsafe()) runCatching { created.close() }
        throw t
    }
}

private fun cacheSubdir(cacheDir: String, name: String): String = java.io.File(cacheDir, name).apply { mkdirs() }.absolutePath

/**
 * Companion libs next to the app, same idea as Windows DXC.
 * Linux: libOpenCL.so (dlopen by name) and a stub WebGPU sampler so LiteRT
 * does not call the statically linked sampler Create (nvidia-gpucomp SIGILL).
 */
private fun loadGpuNativeLibs() {
    val os = System.getProperty("os.name").orEmpty()
    val names = when {
        os.contains("win", ignoreCase = true) -> listOf("dxil.dll", "dxcompiler.dll")
        os.contains("linux", ignoreCase = true) -> listOf(
            "libOpenCL.so",
            "libLiteRtTopKWebGpuSampler.so",
        )
        else -> return
    }
    val dir = appResourcesDir() ?: return
    for (name in names) {
        val lib = dir.resolve(name)
        if (lib.isFile) runCatching { System.load(lib.absolutePath) }
    }
}

private fun linuxNativeTeardownUnsafe(): Boolean =
    linuxGpuTeardownUnsafe(System.getProperty("os.name").orEmpty())

private fun appResourcesDir(): java.io.File? =
    System.getProperty("compose.application.resources.dir")?.let { java.io.File(it) }

internal actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    followRedirects = true
    installNativeSsl()
    install(HttpTimeout) {
        requestTimeoutMillis = Long.MAX_VALUE
        socketTimeoutMillis = Long.MAX_VALUE
        connectTimeoutMillis = 30_000
    }
}
