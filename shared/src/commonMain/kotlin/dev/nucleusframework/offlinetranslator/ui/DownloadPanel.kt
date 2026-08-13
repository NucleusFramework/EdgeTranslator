package dev.nucleusframework.offlinetranslator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nucleusframework.offlinetranslator.app.AppIntent
import dev.nucleusframework.offlinetranslator.domain.DownloadPhase
import dev.nucleusframework.offlinetranslator.domain.DownloadState
import dev.nucleusframework.offlinetranslator.domain.UiLanguage
import dev.nucleusframework.offlinetranslator.domain.formatEta
import dev.nucleusframework.offlinetranslator.domain.formatPercent
import offlinetranslator.shared.generated.resources.Res
import offlinetranslator.shared.generated.resources.action_cancel
import offlinetranslator.shared.generated.resources.action_pause
import offlinetranslator.shared.generated.resources.action_resume
import offlinetranslator.shared.generated.resources.download_auto_resume
import offlinetranslator.shared.generated.resources.download_speed_per_s
import offlinetranslator.shared.generated.resources.download_stat_ratio
import offlinetranslator.shared.generated.resources.download_stat_remaining
import offlinetranslator.shared.generated.resources.download_stat_segments
import offlinetranslator.shared.generated.resources.download_stat_speed
import offlinetranslator.shared.generated.resources.em_dash
import org.jetbrains.compose.resources.stringResource

@Composable
fun DownloadPanel(
    download: DownloadState,
    ui: UiLanguage,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val c = MaterialTheme.colorScheme
    val dash = stringResource(Res.string.em_dash)
    val remaining = (download.totalBytes - download.bytesDownloaded).coerceAtLeast(0)
    val filled = download.fraction.coerceIn(0.01f, 0.99f)
    val phase = download.phase
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.primaryContainer)
            .padding(horizontal = 28.dp, vertical = 22.dp),
    ) {
        if (title != null) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.onPrimaryContainer)
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatPercent(download.fraction, ui), fontSize = 45.sp, color = c.onPrimaryContainer)
                Text(
                    "${formatBytesUi(download.bytesDownloaded, ui)} / ${formatBytesUi(download.totalBytes, ui)}",
                    fontSize = 16.sp,
                    color = c.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            MiniStat(
                stringResource(Res.string.download_stat_speed),
                if (download.speedBps > 0) {
                    stringResource(Res.string.download_speed_per_s, formatBytesUi(download.speedBps, ui))
                } else {
                    dash
                },
            )
            MiniStat(stringResource(Res.string.download_stat_remaining), formatEta(remaining, download.speedBps))
            MiniStat(
                stringResource(Res.string.download_stat_segments),
                stringResource(
                    Res.string.download_stat_ratio,
                    ((download.bytesDownloaded + 16_777_215) / 16_777_216).toInt(),
                    ((download.totalBytes + 16_777_215) / 16_777_216).toInt().coerceAtLeast(1),
                ),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
            Box(Modifier.weight(filled).fillMaxHeight().background(c.primary))
            Box(Modifier.weight((1f - filled).coerceAtLeast(0.01f)).fillMaxHeight().background(c.inversePrimary))
        }
        if (!download.done) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    download.paused || phase == DownloadPhase.Cancelled || phase == DownloadPhase.Failed ->
                        FilledPill(
                            stringResource(Res.string.action_resume),
                            onClick = {
                                onIntent(
                                    if (phase == DownloadPhase.Cancelled || phase == DownloadPhase.Failed) {
                                        AppIntent.RetryDownload
                                    } else {
                                        AppIntent.ResumeDownload
                                    },
                                )
                            },
                            icon = Icons.Outlined.PlayArrow,
                        )

                    else -> FilledPill(
                        stringResource(Res.string.action_pause),
                        onClick = { onIntent(AppIntent.PauseDownload) },
                        icon = Icons.Outlined.Pause,
                    )
                }
                OutlinedPill(stringResource(Res.string.action_cancel), onClick = { onIntent(AppIntent.CancelDownload) })
                Spacer(Modifier.weight(1f))
                Text(
                    download.error?.text(ui) ?: stringResource(Res.string.download_auto_resume),
                    fontSize = 12.sp,
                    color = c.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    val c = MaterialTheme.colorScheme
    Column {
        Text(label, fontSize = 12.sp, color = c.onSurfaceVariant)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.onPrimaryContainer)
    }
}
