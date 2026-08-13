package dev.nucleusframework.offlinetranslator.app

import dev.nucleusframework.offlinetranslator.domain.AppData
import dev.nucleusframework.offlinetranslator.domain.DownloadState
import dev.nucleusframework.offlinetranslator.domain.HistoryFilter
import dev.nucleusframework.offlinetranslator.domain.LlmModel
import dev.nucleusframework.offlinetranslator.domain.VoiceDownloadState
import dev.nucleusframework.offlinetranslator.translation.ProofreadState
import dev.nucleusframework.offlinetranslator.translation.TranslationState

enum class InstallStep { Welcome, Download, Voices }

sealed interface AppDialog {
    data object Hidden : AppDialog
    data class Confirm(val action: ConfirmAction) : AppDialog
    data class InstallVoice(val lang: String) : AppDialog
}

sealed interface ConfirmAction {
    data object PurgeHistory : ConfirmAction
    data class DeleteModel(val id: LlmModel) : ConfirmAction
    data class DeleteVoice(val lang: String) : ConfirmAction
    data object ResetApp : ConfirmAction
}

data class AppState(
    val data: AppData = AppData(),
    val translation: TranslationState = TranslationState.Empty,
    val proofread: ProofreadState = ProofreadState(),
    val download: DownloadState = DownloadState(),
    val voicePicks: Set<String> = emptySet(),
    val voiceDownload: VoiceDownloadState = VoiceDownloadState(),
    val historyQuery: String = "",
    val historyFilter: HistoryFilter = HistoryFilter.All,
    val dialog: AppDialog = AppDialog.Hidden,
    val message: AppMessage? = null,
) {
    val offline: Boolean get() = data.settings.airplane
    val installed: Boolean get() = data.installed
    val installSteps: Int get() = if (translation.ttsReady) 3 else 2
}

fun AppState.installStep(): InstallStep = parseInstallStep(data.installStep)

fun parseInstallStep(raw: String): InstallStep = when (raw) {
    "Languages" -> InstallStep.Download
    else -> runCatching { InstallStep.valueOf(raw) }.getOrDefault(InstallStep.Welcome)
}
