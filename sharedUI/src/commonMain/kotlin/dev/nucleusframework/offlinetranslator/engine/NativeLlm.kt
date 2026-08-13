package dev.nucleusframework.offlinetranslator.engine

import io.ktor.client.HttpClient

internal expect class NativeLlm() {
    fun load(modelPath: String, cacheDir: String, threads: Int): LlmAccelerator
    suspend fun generate(
        systemInstruction: String,
        userMessage: String,
        audioWav: ByteArray? = null,
        onPartial: (String) -> Unit = {},
    ): String
    fun close()
}

internal expect fun createHttpClient(): HttpClient
