package dev.nucleusframework.offlinetranslator.translation

import androidx.compose.runtime.Immutable
import dev.nucleusframework.offlinetranslator.domain.AUTO_LANG
import dev.nucleusframework.offlinetranslator.domain.paragraphCount

@Immutable
data class Alternative(val term: String)

enum class TranslationStatus { Idle, WaitingEngine, Ready, Error }

/** [Starting] = the audio line is opening (cold: OS permission prompt, device wake-up). */
enum class MicPhase { Idle, Starting, Listening, Processing }

/** Correcteur d'orthographe : même moteur que la traduction, un seul texte. */
@Immutable
data class ProofreadState(
    val text: String = "",
    val result: String = "",
    val status: TranslationStatus = TranslationStatus.Idle,
    val latencyMs: Long? = null,
    val error: String? = null,
    val copiedResult: String? = null,
) {
    val chars: Int get() = text.length
    val paragraphs: Int get() = paragraphCount(text)
    val copied: Boolean get() = copiedResult != null && result.trim() == copiedResult
}

@Immutable
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
    val imageBusy: Boolean = false,
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

@Immutable
data class SourcePanelState(
    val lang: String,
    val text: String,
    val micPhase: MicPhase,
    val imageBusy: Boolean,
    val ttsReady: Boolean,
    val voiceInstalled: Boolean,
    val speakActive: Boolean,
    val speakBusy: Boolean,
)

@Immutable
data class TargetPanelState(
    val lang: String,
    val text: String,
    val status: TranslationStatus,
    val latencyMs: Long?,
    val highlightTerm: String,
    val alternatives: List<Alternative>,
    val alternativesFor: String,
    val selectedAlternative: String,
    val copied: Boolean,
    val saved: Boolean,
    val error: String?,
    val sourceBlank: Boolean,
    val ttsReady: Boolean,
    val voiceInstalled: Boolean,
    val speakActive: Boolean,
    val speakBusy: Boolean,
)

fun TranslationState.toSourcePanel() = SourcePanelState(
    lang = sourceLang,
    text = sourceText,
    micPhase = micPhase,
    imageBusy = imageBusy,
    ttsReady = ttsReady,
    voiceInstalled = sourceLang in installedVoices,
    speakActive = speakTarget == false,
    speakBusy = speakBusy && speakTarget == false,
)

fun TranslationState.toTargetPanel() = TargetPanelState(
    lang = targetLang,
    text = targetText,
    status = status,
    latencyMs = latencyMs,
    highlightTerm = highlightTerm,
    alternatives = alternatives,
    alternativesFor = alternativesFor,
    selectedAlternative = selectedAlternative,
    copied = copied,
    saved = saved,
    error = error,
    sourceBlank = sourceText.isBlank(),
    ttsReady = ttsReady,
    voiceInstalled = targetLang in installedVoices,
    speakActive = speakTarget == true,
    speakBusy = speakBusy && speakTarget == true,
)
