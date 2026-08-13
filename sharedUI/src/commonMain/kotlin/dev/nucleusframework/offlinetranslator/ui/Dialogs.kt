package dev.nucleusframework.offlinetranslator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nucleusframework.offlinetranslator.app.AppDialog
import dev.nucleusframework.offlinetranslator.app.AppIntent
import dev.nucleusframework.offlinetranslator.app.AppMessage
import dev.nucleusframework.offlinetranslator.app.ConfirmAction
import dev.nucleusframework.offlinetranslator.domain.Languages
import dev.nucleusframework.offlinetranslator.domain.LlmModel
import dev.nucleusframework.offlinetranslator.domain.UserSettings
import dev.nucleusframework.offlinetranslator.engine.PiperVoices
import dev.nucleusframework.offlinetranslator.ui.formatBytesUi
import offlinetranslator.sharedui.generated.resources.Res
import offlinetranslator.sharedui.generated.resources.action_cancel
import offlinetranslator.sharedui.generated.resources.action_confirm
import offlinetranslator.sharedui.generated.resources.action_download
import offlinetranslator.sharedui.generated.resources.action_ok
import offlinetranslator.sharedui.generated.resources.cd_speak_loading
import offlinetranslator.sharedui.generated.resources.confirm_delete_model
import offlinetranslator.sharedui.generated.resources.confirm_delete_voice
import offlinetranslator.sharedui.generated.resources.confirm_purge_history
import offlinetranslator.sharedui.generated.resources.confirm_reset_app
import offlinetranslator.sharedui.generated.resources.dialog_confirm
import offlinetranslator.sharedui.generated.resources.dialog_install_voice
import offlinetranslator.sharedui.generated.resources.dialog_install_voice_body
import offlinetranslator.sharedui.generated.resources.model_fast_title
import offlinetranslator.sharedui.generated.resources.model_precise_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppDialogHost(dialog: AppDialog, speakLoading: Boolean, speakTarget: Boolean?, settings: UserSettings, onIntent: (AppIntent) -> Unit) {
    when (val d = dialog) {
        AppDialog.Hidden -> Unit
        is AppDialog.Confirm -> ConfirmDialog(d, onIntent)
        is AppDialog.InstallVoice -> InstallVoiceDialog(settings, d, onIntent)
    }
    if (speakLoading) speakTarget?.let { SpeakLoadingDialog(it, onIntent) }
}

/** Cold voice model: seconds of load + synthesis before any sound. Cancel = the speak toggle. */
@Composable
private fun SpeakLoadingDialog(target: Boolean, onIntent: (AppIntent) -> Unit) {
    val cancel = { onIntent(AppIntent.ToggleSpeak(target)) }
    Sheet(onDismiss = cancel, title = stringResource(Res.string.cd_speak_loading)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(
                Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            OutlinedPill(stringResource(Res.string.action_cancel), onClick = cancel)
        }
    }
}

@Composable
private fun ConfirmDialog(d: AppDialog.Confirm, onIntent: (AppIntent) -> Unit) {
    val message = when (val action = d.action) {
        ConfirmAction.PurgeHistory -> stringResource(Res.string.confirm_purge_history)

        is ConfirmAction.DeleteModel -> stringResource(
            Res.string.confirm_delete_model,
            stringResource(if (action.id == LlmModel.Precise) Res.string.model_precise_title else Res.string.model_fast_title),
        )

        is ConfirmAction.DeleteVoice -> stringResource(
            Res.string.confirm_delete_voice,
            PiperVoices.of(action.lang)?.displayName ?: Languages.get(action.lang)?.native ?: action.lang,
        )

        ConfirmAction.ResetApp -> stringResource(Res.string.confirm_reset_app)
    }
    Sheet(onDismiss = { onIntent(AppIntent.DismissDialog) }, title = stringResource(Res.string.dialog_confirm)) {
        Text(message, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            OutlinedPill(stringResource(Res.string.action_cancel), onClick = { onIntent(AppIntent.DismissDialog) })
            FilledPill(stringResource(Res.string.action_confirm), onClick = { onIntent(AppIntent.ConfirmDialog) })
        }
    }
}

@Composable
private fun InstallVoiceDialog(settings: UserSettings, d: AppDialog.InstallVoice, onIntent: (AppIntent) -> Unit) {
    val ui = settings.uiLanguage
    val name = Languages.label(d.lang, settings)
    val size = formatBytesUi(PiperVoices.of(d.lang)?.bytes ?: 0L, ui)
    Sheet(onDismiss = { onIntent(AppIntent.DismissDialog) }, title = stringResource(Res.string.dialog_install_voice)) {
        Text(
            stringResource(Res.string.dialog_install_voice_body, name, size),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            OutlinedPill(stringResource(Res.string.action_cancel), onClick = { onIntent(AppIntent.DismissDialog) })
            FilledPill(
                stringResource(Res.string.action_download),
                onClick = { onIntent(AppIntent.DownloadVoices(listOf(d.lang))) },
            )
        }
    }
}

@Composable
private fun Sheet(onDismiss: () -> Unit, title: String, content: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    // ponytail: overlay in-window plutôt qu'un Dialog — la couche plateforme du Dialog
    // coûte un temps visible au premier affichage (cold), ressenti comme une latence
    // avant l'apparition du popup. Contrepartie : plus de dismiss à la touche Échap.
    Box(
        Modifier.fillMaxSize()
            .background(c.scrim.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = c.surface,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
            // un clic sur la carte ne doit pas remonter au scrim
            modifier = Modifier.pointerInput(Unit) { detectTapGestures {} },
        ) {
            Column(Modifier.width(480.dp).padding(24.dp)) {
                Text(title, fontSize = 20.sp, color = c.onSurface)
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
fun MessageBar(message: AppMessage?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (message == null) return
    val c = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp)).background(c.inverseSurface)
            .clickable(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message.text(), color = c.inverseOnSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(stringResource(Res.string.action_ok), color = c.inversePrimary, fontSize = 13.sp)
    }
}
