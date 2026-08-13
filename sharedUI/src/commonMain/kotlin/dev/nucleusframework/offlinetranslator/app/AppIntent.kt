package dev.nucleusframework.offlinetranslator.app

import dev.nucleusframework.offlinetranslator.domain.DownloadLog
import dev.nucleusframework.offlinetranslator.domain.HistoryFilter
import dev.nucleusframework.offlinetranslator.domain.LangNameStyle
import dev.nucleusframework.offlinetranslator.domain.LangRole
import dev.nucleusframework.offlinetranslator.domain.LlmModel
import dev.nucleusframework.offlinetranslator.domain.ThemeMode
import dev.nucleusframework.offlinetranslator.domain.UiLanguage

sealed interface AppIntent {
    data object StartInstall : AppIntent
    data object InstallBack : AppIntent
    data object OpenApp : AppIntent
    data class GoToStep(val step: InstallStep) : AppIntent
    data object Quit : AppIntent

    data class Navigate(val destination: AppKey) : AppIntent
    data object NewTranslation : AppIntent

    data object SwapLanguages : AppIntent
    data class SelectAlternative(val term: String) : AppIntent
    data object CopyTranslation : AppIntent
    data object SaveToHistory : AppIntent
    data class SetSourceText(val text: String) : AppIntent
    data class SetProofreadText(val text: String) : AppIntent
    data object CopyProofread : AppIntent
    data object ApplyProofread : AppIntent
    data object ToggleMic : AppIntent
    data object CancelMic : AppIntent
    data class ToggleSpeak(val target: Boolean) : AppIntent
    data class ToggleVoicePick(val code: String) : AppIntent
    data class SelectAllVoices(val lang: String? = null) : AppIntent
    data class ClearVoicePicks(val lang: String? = null) : AppIntent
    data class DownloadVoices(val langs: List<String>? = null) : AppIntent
    data object CancelVoiceDownload : AppIntent
    data object RetryVoiceDownload : AppIntent
    data class DeleteVoice(val lang: String) : AppIntent
    data class SelectVoice(val id: String) : AppIntent
    data class ChooseLanguage(val code: String, val role: LangRole) : AppIntent

    /** `null` follows the OS language. */
    data class SetUiLanguage(val language: UiLanguage?) : AppIntent
    data class SetLangNameStyle(val style: LangNameStyle) : AppIntent

    data object PauseDownload : AppIntent
    data object ResumeDownload : AppIntent
    data object CancelDownload : AppIntent
    data object RetryDownload : AppIntent
    data object CompleteDownload : AppIntent
    data class DownloadTick(val bytes: Long, val speedBps: Long, val log: DownloadLog? = null, val totalBytes: Long = 0) : AppIntent
    data class DownloadPhase(val phase: dev.nucleusframework.offlinetranslator.domain.DownloadPhase) : AppIntent

    data class SetHistoryQuery(val query: String) : AppIntent
    data class SetHistoryFilter(val filter: HistoryFilter) : AppIntent
    data class OpenHistory(val id: String) : AppIntent
    data class ToggleHistoryPin(val id: String) : AppIntent
    data class DeleteHistory(val id: String) : AppIntent
    data object ClearHistory : AppIntent

    data class SelectModel(val id: LlmModel) : AppIntent
    data class DeleteModel(val id: LlmModel) : AppIntent
    data class SetTheme(val mode: ThemeMode) : AppIntent
    data class SetAirplane(val on: Boolean) : AppIntent
    data class SetKeepHistory(val on: Boolean) : AppIntent
    data class SetAutoPurge(val on: Boolean) : AppIntent
    data class SetLaunchAtLogin(val on: Boolean) : AppIntent
    data object ResetApp : AppIntent
    data object ConfirmDialog : AppIntent
    data object DismissDialog : AppIntent
    data object DismissMessage : AppIntent
}
