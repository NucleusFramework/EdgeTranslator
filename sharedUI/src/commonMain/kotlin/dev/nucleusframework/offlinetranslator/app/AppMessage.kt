package dev.nucleusframework.offlinetranslator.app

import androidx.compose.runtime.Immutable

@Immutable
sealed interface AppMessage {
    data object NothingToSave : AppMessage
    data object HistoryDisabled : AppMessage
    data object MicUnavailable : AppMessage
    data object MicFailed : AppMessage
    data object TtsUnavailable : AppMessage
    data object TtsFailed : AppMessage
}
