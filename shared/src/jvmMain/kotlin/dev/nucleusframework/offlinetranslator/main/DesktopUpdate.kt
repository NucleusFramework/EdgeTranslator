package dev.nucleusframework.offlinetranslator.main

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nucleusframework.nativehttp.NativeHttpClient
import dev.nucleusframework.offlinetranslator.ui.FilledPill
import dev.nucleusframework.offlinetranslator.ui.OutlinedPill
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateResult
import dev.nucleusframework.updater.provider.GitHubProvider
import offlinetranslator.shared.generated.resources.Res
import offlinetranslator.shared.generated.resources.action_cancel
import offlinetranslator.shared.generated.resources.action_restart_now
import offlinetranslator.shared.generated.resources.update_available
import offlinetranslator.shared.generated.resources.update_restart_body
import offlinetranslator.shared.generated.resources.update_restart_title
import org.jetbrains.compose.resources.stringResource
import java.io.File

private const val UPDATE_OWNER = "NucleusFramework"
private const val UPDATE_REPO = "EdgeTranslator"

class DesktopUpdate internal constructor(
    val ready: Boolean,
    val showDialog: Boolean,
    val onIconClick: () -> Unit,
    val onDismissDialog: () -> Unit,
    val onRestartNow: () -> Unit,
    val installOnExit: () -> Unit,
)

@Composable
fun rememberDesktopUpdate(): DesktopUpdate {
    val updater = remember {
        NucleusUpdater {
            provider = GitHubProvider(owner = UPDATE_OWNER, repo = UPDATE_REPO)
            httpClient = NativeHttpClient.create()
        }
    }
    var file by remember { mutableStateOf<File?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(updater) {
        if (!updater.isUpdateSupported()) return@LaunchedEffect
        when (val result = updater.checkForUpdates()) {
            is UpdateResult.Available -> updater.downloadUpdate(result.info).collect { progress ->
                progress.file?.let { file = it }
            }

            else -> Unit
        }
    }

    val ready = file != null
    return DesktopUpdate(
        ready = ready,
        showDialog = showDialog,
        onIconClick = { if (ready) showDialog = true },
        onDismissDialog = { showDialog = false },
        onRestartNow = { file?.let(updater::installAndRestart) },
        installOnExit = { file?.let(updater::installAndQuit) },
    )
}

@Composable
fun UpdateButton(update: DesktopUpdate, modifier: Modifier = Modifier) {
    if (!update.ready) return
    val c = MaterialTheme.colorScheme
    val label = stringResource(Res.string.update_available)
    val tooltipState = rememberTooltipState(isPersistent = true)
    LaunchedEffect(Unit) { tooltipState.show() }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = tooltipState,
        modifier = modifier,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(onClick = update.onIconClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.SystemUpdate, label, Modifier.size(18.dp), tint = c.primary)
        }
    }
}

@Composable
fun UpdateRestartDialog(update: DesktopUpdate, modifier: Modifier = Modifier) {
    if (!update.showDialog) return
    val c = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxSize()
            .background(c.scrim.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = update.onDismissDialog,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = c.surface,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.pointerInput(Unit) { detectTapGestures {} },
        ) {
            Column(Modifier.width(480.dp).padding(24.dp)) {
                Text(stringResource(Res.string.update_restart_title), fontSize = 20.sp, color = c.onSurface)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(Res.string.update_restart_body), color = c.onSurface, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    OutlinedPill(stringResource(Res.string.action_cancel), onClick = update.onDismissDialog)
                    FilledPill(stringResource(Res.string.action_restart_now), onClick = update.onRestartNow)
                }
            }
        }
    }
}
