package dev.nucleusframework.offlinetranslator.engine

import io.ktor.client.HttpClient

interface TtsSpeaker : AutoCloseable {
    val available: Boolean get() = false
    fun canSpeak(lang: String): Boolean

    /**
     * [onReady] fires when audio actually starts. Everything before it is the cold
     * cost — engine init, voice model load, synthesis — which is what the UI puts a
     * loader on.
     */
    suspend fun speak(text: String, lang: String, voiceId: String? = null, onReady: () -> Unit = {})
    fun pause() {}
    fun resume() {}
    fun stop()
    fun unload() {}
    override fun close() {}
}

object SilentTts : TtsSpeaker {
    override val available: Boolean = false
    override fun canSpeak(lang: String): Boolean = false
    override suspend fun speak(text: String, lang: String, voiceId: String?, onReady: () -> Unit) {}
    override fun stop() {}
}

expect fun createTtsSpeaker(http: HttpClient): TtsSpeaker
