package dev.nucleusframework.offlinetranslator.engine

import dev.nucleusframework.offlinetranslator.translation.Alternative

/** On-device translation engine. Production uses Gemma 4 E2B via LiteRT-LM. */
fun interface Translator {
    suspend fun translate(request: TranslationRequest): TranslationResult

    suspend fun preload(path: String) {}

    suspend fun release() {}

    fun close() {}
}

enum class TranslationMode { Translate, Proofread }

data class TranslationRequest(
    val text: String,
    val sourceLang: String,
    val targetLang: String,
    val modelPath: String = "",
    val audioWav: ByteArray? = null,
    /** Encoded image (JPEG/PNG/BMP) — the vision tower reads the text in it. */
    val image: ByteArray? = null,
    // ponytail: même moteur, autre prompt — un second Translator dupliquerait mutex + chargement du modèle.
    val mode: TranslationMode = TranslationMode.Translate,
    val onPartial: (String) -> Unit = {},
)

sealed interface TranslationResult {
    data class Ok(
        val text: String,
        val transcription: String = "",
        val alternatives: List<Alternative> = emptyList(),
        val highlight: String = "",
        val latencyMs: Long = 0,
    ) : TranslationResult

    data object Unavailable : TranslationResult

    data class Error(val message: String? = null) : TranslationResult
}

object UnavailableTranslator : Translator {
    override suspend fun translate(request: TranslationRequest): TranslationResult = TranslationResult.Unavailable
}
