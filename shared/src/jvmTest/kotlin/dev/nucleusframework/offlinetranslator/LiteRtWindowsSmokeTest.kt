package dev.nucleusframework.offlinetranslator

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the pin we just lifted: LiteRT-LM 0.15.0/0.16.x aborted the JVM on
 * Windows CPU generate (0xC0000409 in litertlm_jni.dll), see
 * https://github.com/google-ai-edge/LiteRT-LM/issues/3230.
 *
 * Needs a real .litertlm on disk, so it skips unless `litertlm.test.model`
 * (or `LITERTLM_TEST_MODEL`) points at one. A native abort takes the test JVM
 * with it, which Gradle reports as a crashed worker — that is the signal.
 */
class LiteRtWindowsSmokeTest {
    private val model: File? =
        (System.getProperty("litertlm.test.model") ?: System.getenv("LITERTLM_TEST_MODEL"))
            ?.let(::File)
            ?.takeIf { it.isFile }

    @Test
    fun cpuGenerate() {
        val model = model ?: return skip()
        val out = generate(model, Backend.CPU(threadCount = 4), "cpu", mtp = false)
        assertTrue(out.isNotEmpty(), "empty CPU generation")
    }

    @Test
    fun gpuGenerate() {
        val model = model ?: return skip()
        loadWindowsDxc()
        val out = generate(model, Backend.GPU(), "gpu", mtp = false)
        assertTrue(out.isNotEmpty(), "empty GPU generation")
    }

    @Test
    fun gpuGenerateWithMtp() {
        val model = model ?: return skip()
        loadWindowsDxc()
        val out = generate(model, Backend.GPU(), "mtp", mtp = true)
        assertTrue(out.isNotEmpty(), "empty MTP generation")
    }

    private fun skip() = println("SKIP: set -Dlitertlm.test.model=<path to .litertlm>")

    @OptIn(ExperimentalApi::class)
    private fun generate(model: File, backend: Backend, cacheName: String, mtp: Boolean): String {
        ExperimentalFlags.enableSpeculativeDecoding = mtp
        val cacheDir = File(System.getProperty("java.io.tmpdir"), "litert-smoke-$cacheName").apply { mkdirs() }
        val engine = Engine(
            EngineConfig(
                modelPath = model.absolutePath,
                backend = backend,
                visionBackend = Backend.CPU(),
                audioBackend = Backend.CPU(),
                cacheDir = cacheDir.absolutePath,
                maxNumTokens = 2048,
            ),
        )
        try {
            engine.initialize()
            val conversation = engine.createConversation(translationConfig())
            try {
                val out = StringBuilder()
                runBlocking {
                    conversation.sendMessageAsync(Contents.of(PROMPT)).collect { out.append(it.toString()) }
                }
                println("$cacheName -> $out")
                return out.toString()
            } finally {
                conversation.close()
            }
        } finally {
            engine.close()
            ExperimentalFlags.enableSpeculativeDecoding = false
        }
    }

    private fun translationConfig() = ConversationConfig(
        systemInstruction = Contents.of("You are a translator. Answer with the translation only."),
        samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.2),
        thinkingConfig = ThinkingConfig(enableThinking = false),
        channels = emptyList(),
        maxOutputToken = 1024,
    )

    /** LiteRT's Windows GPU path dlopens the DXC pair the desktop app ships. */
    private fun loadWindowsDxc() {
        val dir = File("../desktopApp/resources/windows-x64").absoluteFile
        listOf("dxil.dll", "dxcompiler.dll").forEach { name ->
            val lib = dir.resolve(name)
            if (lib.isFile) runCatching { System.load(lib.absolutePath) }
        }
    }

    private companion object {
        const val PROMPT = "Translate to French: Hello, how are you?"
    }
}
