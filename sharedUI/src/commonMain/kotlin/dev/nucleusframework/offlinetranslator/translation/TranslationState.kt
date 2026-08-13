package dev.nucleusframework.offlinetranslator.translation

import dev.nucleusframework.offlinetranslator.domain.AUTO_LANG
import dev.nucleusframework.offlinetranslator.domain.paragraphCount

data class Alternative(val term: String)

enum class TranslationStatus { Idle, WaitingEngine, Ready, Error }

enum class MicPhase { Idle, Listening, Processing }

data class TranslationState(
    val sourceLang: String = AUTO_LANG,
    val targetLang: String = "en",
    val sourceText: String = "",
    val targetText: String = "",
    val highlightTerm: String = "",
    val alternativesFor: String = "",
    val alternatives: List<Alternative> = emptyList(),
    val selectedAlternative: String = "",
    val status: TranslationStatus = TranslationStatus.Idle,
    val latencyMs: Long? = null,
    val error: String? = null,
    val micPhase: MicPhase = MicPhase.Idle,
    val micLevels: List<Float> = emptyList(),
    val micElapsedMs: Long = 0,
    val speakTarget: Boolean? = null,
    val speakBusy: Boolean = false,
    /** Busy *before* audio starts: voice model load + synthesis. Drives the loader popup. */
    val speakLoading: Boolean = false,
    val ttsReady: Boolean = false,
    val installedVoices: Set<String> = emptySet(),
    val savedSource: String? = null,
    val savedTarget: String? = null,
    val copiedTarget: String? = null,
) {
    val sourceChars: Int get() = sourceText.length
    val sourceParagraphs: Int get() = paragraphCount(sourceText)
    val saved: Boolean get() = savedSource != null &&
        sourceText.trim() == savedSource &&
        targetText.trim() == savedTarget
    val copied: Boolean get() = copiedTarget != null && targetText.trim() == copiedTarget

    companion object {
        val Empty = TranslationState()
        val Default = Empty
    }
}
