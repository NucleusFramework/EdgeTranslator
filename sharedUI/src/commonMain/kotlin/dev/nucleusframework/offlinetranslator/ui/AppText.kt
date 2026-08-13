package dev.nucleusframework.offlinetranslator.ui

import androidx.compose.runtime.Composable
import dev.nucleusframework.offlinetranslator.app.AppMessage
import dev.nucleusframework.offlinetranslator.domain.DownloadError
import dev.nucleusframework.offlinetranslator.domain.DownloadLog
import dev.nucleusframework.offlinetranslator.domain.UiLanguage
import offlinetranslator.sharedui.generated.resources.Res
import offlinetranslator.sharedui.generated.resources.download_error_airplane
import offlinetranslator.sharedui.generated.resources.download_error_disk
import offlinetranslator.sharedui.generated.resources.download_error_hf_denied
import offlinetranslator.sharedui.generated.resources.download_error_hf_missing
import offlinetranslator.sharedui.generated.resources.download_error_http
import offlinetranslator.sharedui.generated.resources.download_error_install
import offlinetranslator.sharedui.generated.resources.download_error_interrupted
import offlinetranslator.sharedui.generated.resources.download_error_sha_compute
import offlinetranslator.sharedui.generated.resources.download_error_sha_mismatch
import offlinetranslator.sharedui.generated.resources.download_log_already
import offlinetranslator.sharedui.generated.resources.download_log_disk_ok
import offlinetranslator.sharedui.generated.resources.download_log_huggingface
import offlinetranslator.sharedui.generated.resources.download_log_ready
import offlinetranslator.sharedui.generated.resources.download_log_received
import offlinetranslator.sharedui.generated.resources.download_log_transfer
import offlinetranslator.sharedui.generated.resources.msg_history_disabled
import offlinetranslator.sharedui.generated.resources.msg_mic_failed
import offlinetranslator.sharedui.generated.resources.msg_mic_unavailable
import offlinetranslator.sharedui.generated.resources.msg_nothing_to_save
import offlinetranslator.sharedui.generated.resources.msg_drop_unsupported
import offlinetranslator.sharedui.generated.resources.msg_tts_failed
import offlinetranslator.sharedui.generated.resources.msg_tts_unavailable
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppMessage.text(): String = when (this) {
    AppMessage.NothingToSave -> stringResource(Res.string.msg_nothing_to_save)
    AppMessage.HistoryDisabled -> stringResource(Res.string.msg_history_disabled)
    AppMessage.MicUnavailable -> stringResource(Res.string.msg_mic_unavailable)
    AppMessage.MicFailed -> stringResource(Res.string.msg_mic_failed)
    AppMessage.TtsUnavailable -> stringResource(Res.string.msg_tts_unavailable)
    AppMessage.TtsFailed -> stringResource(Res.string.msg_tts_failed)
    AppMessage.DropUnsupported -> stringResource(Res.string.msg_drop_unsupported)
}

@Composable
fun DownloadError.text(ui: UiLanguage): String = when (this) {
    DownloadError.Airplane -> stringResource(Res.string.download_error_airplane)
    is DownloadError.DiskFull -> stringResource(Res.string.download_error_disk, formatBytesUi(freeBytes, ui))
    DownloadError.Interrupted -> stringResource(Res.string.download_error_interrupted)
    is DownloadError.HttpDenied -> stringResource(Res.string.download_error_hf_denied, status)
    DownloadError.NotFound -> stringResource(Res.string.download_error_hf_missing)
    is DownloadError.Http -> stringResource(Res.string.download_error_http, status)
    DownloadError.ShaCompute -> stringResource(Res.string.download_error_sha_compute)
    DownloadError.ShaMismatch -> stringResource(Res.string.download_error_sha_mismatch)
    DownloadError.InstallFailed -> stringResource(Res.string.download_error_install)
}

@Composable
fun DownloadLog.text(): String = when (this) {
    DownloadLog.DiskOk -> stringResource(Res.string.download_log_disk_ok)
    is DownloadLog.Mirror -> stringResource(Res.string.download_log_huggingface, repo)
    DownloadLog.Ready -> stringResource(Res.string.download_log_ready)
    DownloadLog.AlreadyPresent -> stringResource(Res.string.download_log_already)
    DownloadLog.Transfer -> stringResource(Res.string.download_log_transfer)
    is DownloadLog.ReceivedMb -> stringResource(Res.string.download_log_received, downloaded, total)
}
