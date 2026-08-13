package dev.nucleusframework.offlinetranslator.engine

import dev.nucleusframework.offlinetranslator.domain.LlmBackend
import io.ktor.client.HttpClient

internal expect class NativeLlm() {
    fun load(modelPath: String, cacheDir: String, threads: Int, backend: LlmBackend): LlmAccelerator
    suspend fun generate(
        systemInstruction: String,
        userMessage: String,
        audioWav: ByteArray? = null,
        image: ByteArray? = null,
        onPartial: (String) -> Unit = {},
    ): String
    fun close()
}

internal expect fun createHttpClient(): HttpClient
