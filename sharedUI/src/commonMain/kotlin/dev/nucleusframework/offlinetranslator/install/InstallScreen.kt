package dev.nucleusframework.offlinetranslator.install

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nucleusframework.offlinetranslator.app.AppIntent
import dev.nucleusframework.offlinetranslator.app.AppState
import dev.nucleusframework.offlinetranslator.app.InstallStep
import dev.nucleusframework.offlinetranslator.domain.DownloadPhase
import dev.nucleusframework.offlinetranslator.domain.LangNameStyle
import dev.nucleusframework.offlinetranslator.domain.Languages
import dev.nucleusframework.offlinetranslator.domain.LlmModel
import dev.nucleusframework.offlinetranslator.domain.UiLanguage
import dev.nucleusframework.offlinetranslator.domain.formatEta
import dev.nucleusframework.offlinetranslator.domain.formatPercent
import dev.nucleusframework.offlinetranslator.engine.GemmaModel
import dev.nucleusframework.offlinetranslator.engine.GemmaModels
import dev.nucleusframework.offlinetranslator.engine.PiperVoices
import dev.nucleusframework.offlinetranslator.platform.Platform
import dev.nucleusframework.offlinetranslator.ui.FilledPill
import dev.nucleusframework.offlinetranslator.ui.OutlinedPill
import dev.nucleusframework.offlinetranslator.ui.SectionLabel
import dev.nucleusframework.offlinetranslator.ui.formatBytesUi
import dev.nucleusframework.offlinetranslator.ui.languageLabel
import dev.nucleusframework.offlinetranslator.ui.text
import offlinetranslator.sharedui.generated.resources.Res
import offlinetranslator.sharedui.generated.resources.action_back
import offlinetranslator.sharedui.generated.resources.action_cancel
import offlinetranslator.sharedui.generated.resources.action_continue
import offlinetranslator.sharedui.generated.resources.action_download
import offlinetranslator.sharedui.generated.resources.action_finish
import offlinetranslator.sharedui.generated.resources.action_pause
import offlinetranslator.sharedui.generated.resources.action_quit
import offlinetranslator.sharedui.generated.resources.action_resume
import offlinetranslator.sharedui.generated.resources.action_skip
import offlinetranslator.sharedui.generated.resources.action_start
import offlinetranslator.sharedui.generated.resources.app_name
import offlinetranslator.sharedui.generated.resources.app_version
import offlinetranslator.sharedui.generated.resources.download_auto_resume
import offlinetranslator.sharedui.generated.resources.download_speed_per_s
import offlinetranslator.sharedui.generated.resources.download_stat_ratio
import offlinetranslator.sharedui.generated.resources.download_stat_remaining
import offlinetranslator.sharedui.generated.resources.download_stat_segments
import offlinetranslator.sharedui.generated.resources.download_stat_speed
import offlinetranslator.sharedui.generated.resources.download_status_done
import offlinetranslator.sharedui.generated.resources.download_status_pending
import offlinetranslator.sharedui.generated.resources.download_status_running
import offlinetranslator.sharedui.generated.resources.download_step_connect
import offlinetranslator.sharedui.generated.resources.download_step_disk
import offlinetranslator.sharedui.generated.resources.download_step_index
import offlinetranslator.sharedui.generated.resources.download_step_transfer
import offlinetranslator.sharedui.generated.resources.download_step_verify
import offlinetranslator.sharedui.generated.resources.download_steps
import offlinetranslator.sharedui.generated.resources.download_title
import offlinetranslator.sharedui.generated.resources.em_dash
import offlinetranslator.sharedui.generated.resources.install_feature_history_body
import offlinetranslator.sharedui.generated.resources.install_feature_history_title
import offlinetranslator.sharedui.generated.resources.install_feature_local_body
import offlinetranslator.sharedui.generated.resources.install_feature_local_title
import offlinetranslator.sharedui.generated.resources.install_feature_model_body
import offlinetranslator.sharedui.generated.resources.install_feature_model_title
import offlinetranslator.sharedui.generated.resources.install_feature_voices_body
import offlinetranslator.sharedui.generated.resources.install_feature_voices_title
import offlinetranslator.sharedui.generated.resources.install_network_note
import offlinetranslator.sharedui.generated.resources.install_spec_disk
import offlinetranslator.sharedui.generated.resources.install_spec_langs
import offlinetranslator.sharedui.generated.resources.install_spec_langs_value
import offlinetranslator.sharedui.generated.resources.install_spec_model
import offlinetranslator.sharedui.generated.resources.install_spec_quant
import offlinetranslator.sharedui.generated.resources.install_spec_runtime
import offlinetranslator.sharedui.generated.resources.install_spec_runtime_value
import offlinetranslator.sharedui.generated.resources.install_spec_sheet
import offlinetranslator.sharedui.generated.resources.install_spec_voices
import offlinetranslator.sharedui.generated.resources.install_spec_voices_value
import offlinetranslator.sharedui.generated.resources.install_spec_weight
import offlinetranslator.sharedui.generated.resources.install_step
import offlinetranslator.sharedui.generated.resources.install_welcome_body
import offlinetranslator.sharedui.generated.resources.install_welcome_title
import offlinetranslator.sharedui.generated.resources.model_fast_body
import offlinetranslator.sharedui.generated.resources.model_fast_title
import offlinetranslator.sharedui.generated.resources.model_pick_title
import offlinetranslator.sharedui.generated.resources.model_precise_body
import offlinetranslator.sharedui.generated.resources.model_precise_title
import offlinetranslator.sharedui.generated.resources.settings_model_downloading
import offlinetranslator.sharedui.generated.resources.settings_model_installed
import offlinetranslator.sharedui.generated.resources.settings_ui_language
import offlinetranslator.sharedui.generated.resources.settings_voices_summary
import offlinetranslator.sharedui.generated.resources.voices_body
import offlinetranslator.sharedui.generated.resources.voices_download_progress
import offlinetranslator.sharedui.generated.resources.voices_download_title
import offlinetranslator.sharedui.generated.resources.voices_select_all
import offlinetranslator.sharedui.generated.resources.voices_select_none
import offlinetranslator.sharedui.generated.resources.voices_selected_size
import offlinetranslator.sharedui.generated.resources.voices_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun InstallScreen(step: InstallStep, state: AppState, onIntent: (AppIntent) -> Unit) {
    when (step) {
        InstallStep.Welcome -> WelcomeStep(state, onIntent)
        InstallStep.Download -> DownloadStep(state, onIntent)
        InstallStep.Voices -> VoicesStep(state, onIntent)
    }
}

// ── A1 · Bienvenue ────────────────────────────────────────────────────────────

@Composable
private fun WelcomeStep(state: AppState, onIntent: (AppIntent) -> Unit) {
    val c = MaterialTheme.colorScheme
    val ui = state.data.settings.uiLanguage
    val selected = state.data.settings.selectedModel
    val catalog = GemmaModels.of(selected)
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(56.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = c.primary, shape = RoundedCornerShape(16.dp)) {
                    Box(Modifier.size(56.dp), Alignment.Center) {
                        Icon(Icons.Outlined.Translate, null, Modifier.size(30.dp), tint = c.onPrimary)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.app_name), fontSize = 22.sp, color = c.onSurface)
                    Text(stringResource(Res.string.app_version, "1.0", Platform.osLabel), fontSize = 12.sp, color = c.onSurfaceVariant)
                }
                UiLanguagePicker(ui, state.data.settings.langNames, onIntent)
            }
            Spacer(Modifier.height(28.dp))
            StepLabel(stringResource(Res.string.install_step, 1, state.installSteps))
            Spacer(Modifier.height(10.dp))
            Text(stringResource(Res.string.install_welcome_title), fontSize = 36.sp, lineHeight = 44.sp, color = c.onSurface)
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(Res.string.install_welcome_body),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = c.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 460.dp),
            )
            Spacer(Modifier.height(24.dp))
            FeatureCard(
                Icons.Outlined.Lock,
                stringResource(Res.string.install_feature_local_title),
                stringResource(Res.string.install_feature_local_body),
            )
            Spacer(Modifier.height(2.dp))
            FeatureCard(
                Icons.Outlined.Memory,
                stringResource(Res.string.install_feature_model_title),
                stringResource(Res.string.install_feature_model_body, Languages.all.size, Languages.audioCount),
            )
            Spacer(Modifier.height(2.dp))
            FeatureCard(
                Icons.Outlined.History,
                stringResource(Res.string.install_feature_history_title),
                stringResource(Res.string.install_feature_history_body),
            )
            if (state.translation.ttsReady) {
                Spacer(Modifier.height(2.dp))
                FeatureCard(
                    Icons.Outlined.RecordVoiceOver,
                    stringResource(Res.string.install_feature_voices_title),
                    stringResource(Res.string.install_feature_voices_body, Languages.ttsCount, Languages.all.size),
                )
            }
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(Res.string.model_pick_title))
            Spacer(Modifier.height(8.dp))
            ModelPickCard(
                title = stringResource(Res.string.model_fast_title),
                body = stringResource(Res.string.model_fast_body, formatBytesUi(GemmaModels.Fast.bytes, ui)),
                selected = selected == LlmModel.Fast,
            ) { onIntent(AppIntent.SelectModel(LlmModel.Fast)) }
            Spacer(Modifier.height(8.dp))
            ModelPickCard(
                title = stringResource(Res.string.model_precise_title),
                body = stringResource(Res.string.model_precise_body, formatBytesUi(GemmaModels.Precise.bytes, ui)),
                selected = selected == LlmModel.Precise,
            ) { onIntent(AppIntent.SelectModel(LlmModel.Precise)) }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledPill(stringResource(Res.string.action_start), onClick = { onIntent(AppIntent.StartInstall) })
                OutlinedPill(stringResource(Res.string.action_quit), onClick = { onIntent(AppIntent.Quit) })
            }
        }
        Column(
            Modifier.width(380.dp).fillMaxHeight().background(c.surfaceContainer).padding(horizontal = 36.dp, vertical = 56.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SectionLabel(stringResource(Res.string.install_spec_sheet))
            Column {
                SpecRow(stringResource(Res.string.install_spec_model), catalog.name)
                SpecRow(stringResource(Res.string.install_spec_quant), catalog.quantization)
                SpecRow(stringResource(Res.string.install_spec_weight), formatBytesUi(catalog.bytes, ui))
                SpecRow(
                    stringResource(Res.string.install_spec_langs),
                    stringResource(Res.string.install_spec_langs_value, Languages.all.size, Languages.audioCount),
                )
                if (state.translation.ttsReady) {
                    SpecRow(
                        stringResource(Res.string.install_spec_voices),
                        stringResource(Res.string.install_spec_voices_value, Languages.ttsCount),
                    )
                }
                SpecRow(stringResource(Res.string.install_spec_disk), formatBytesUi(catalog.bytes + GemmaModel.DISK_BUFFER_BYTES, ui))
                SpecRow(stringResource(Res.string.install_spec_runtime), stringResource(Res.string.install_spec_runtime_value), last = true)
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(Res.string.install_network_note), fontSize = 12.sp, lineHeight = 16.sp, color = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModelPickCard(title: String, body: String, selected: Boolean, trailing: ImageVector? = null, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.primaryContainer else c.surfaceContainer)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            null,
            tint = if (selected) c.onPrimaryContainer else c.outline,
        )
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = if (selected) c.onPrimaryContainer else c.onSurface)
            Text(body, fontSize = 14.sp, color = if (selected) c.onPrimaryContainer else c.onSurfaceVariant)
        }
        if (trailing != null) {
            Icon(trailing, null, tint = if (selected) c.onPrimaryContainer else c.outline)
        }
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, subtitle: String) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surfaceContainer).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = c.primary)
        Column {
            Text(title, fontSize = 16.sp, color = c.onSurface)
            Text(subtitle, fontSize = 14.sp, color = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String, last: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(label, Modifier.weight(1f), color = c.onSurfaceVariant, fontSize = 14.sp)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
        }
        if (!last) HorizontalDivider(color = c.outlineVariant)
    }
}

// ── A2 · Téléchargement ───────────────────────────────────────────────────────

private data class DownloadStepItem(
    val label: String,
    val status: String,
    val icon: ImageVector,
    val active: Boolean = false,
    val done: Boolean = false,
)

@Composable
private fun DownloadStep(state: AppState, onIntent: (AppIntent) -> Unit) {
    val c = MaterialTheme.colorScheme
    val ui = state.data.settings.uiLanguage
    val d = state.download
    val phase = d.phase
    val dash = stringResource(Res.string.em_dash)
    val statusDone = stringResource(Res.string.download_status_done)
    val statusRunning = stringResource(Res.string.download_status_running)
    val statusPending = stringResource(Res.string.download_status_pending)
    fun statusOf(p: DownloadPhase): Pair<String, Boolean> = when {
        phase == DownloadPhase.Cancelled || phase == DownloadPhase.Failed -> dash to false
        phase.ordinal > p.ordinal || phase == DownloadPhase.Done -> statusDone to true
        phase == p -> statusRunning to true
        else -> statusPending to false
    }
    val steps = listOf(
        DownloadStepItem(
            stringResource(Res.string.download_step_disk),
            statusOf(DownloadPhase.DiskCheck).first,
            if (statusOf(DownloadPhase.DiskCheck).second &&
                phase != DownloadPhase.DiskCheck
            ) {
                Icons.Outlined.CheckCircle
            } else {
                Icons.Outlined.RadioButtonUnchecked
            },
            active =
            phase == DownloadPhase.DiskCheck,
            done = phase.ordinal > DownloadPhase.DiskCheck.ordinal || phase == DownloadPhase.Done,
        ),
        DownloadStepItem(
            stringResource(Res.string.download_step_connect),
            statusOf(DownloadPhase.Connect).first,
            if (statusOf(DownloadPhase.Connect).second &&
                phase != DownloadPhase.Connect
            ) {
                Icons.Outlined.CheckCircle
            } else {
                Icons.Outlined.RadioButtonUnchecked
            },
            active =
            phase == DownloadPhase.Connect,
            done = phase.ordinal > DownloadPhase.Connect.ordinal || phase == DownloadPhase.Done,
        ),
        DownloadStepItem(
            stringResource(Res.string.download_step_transfer),
            if (phase ==
                DownloadPhase.Transfer
            ) {
                formatPercent(d.fraction, ui)
            } else {
                statusOf(DownloadPhase.Transfer).first
            },
            if (phase ==
                DownloadPhase.Transfer
            ) {
                Icons.Outlined.Downloading
            } else if (phase.ordinal > DownloadPhase.Transfer.ordinal ||
                phase == DownloadPhase.Done
            ) {
                Icons.Outlined.CheckCircle
            } else {
                Icons.Outlined.RadioButtonUnchecked
            },
            active =
            phase == DownloadPhase.Transfer,
            done =
            phase.ordinal > DownloadPhase.Transfer.ordinal || phase == DownloadPhase.Done,
        ),
        DownloadStepItem(
            stringResource(Res.string.download_step_verify),
            statusOf(DownloadPhase.Verify).first,
            if (statusOf(DownloadPhase.Verify).second &&
                phase != DownloadPhase.Verify
            ) {
                Icons.Outlined.CheckCircle
            } else {
                Icons.Outlined.RadioButtonUnchecked
            },
            active =
            phase == DownloadPhase.Verify,
            done = phase.ordinal > DownloadPhase.Verify.ordinal || phase == DownloadPhase.Done,
        ),
        DownloadStepItem(
            stringResource(Res.string.download_step_index),
            statusOf(DownloadPhase.Index).first,
            if (statusOf(DownloadPhase.Index).second &&
                phase != DownloadPhase.Index
            ) {
                Icons.Outlined.CheckCircle
            } else {
                Icons.Outlined.RadioButtonUnchecked
            },
            active =
            phase == DownloadPhase.Index,
            done = phase == DownloadPhase.Done,
        ),
    )
    val remaining = (d.totalBytes - d.bytesDownloaded).coerceAtLeast(0)
    val filled = d.fraction.coerceIn(0.01f, 0.99f)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 56.dp, end = 56.dp, top = 32.dp)) {
            StepLabel(stringResource(Res.string.install_step, 2, state.installSteps))
            Spacer(Modifier.height(10.dp))
            val catalog = GemmaModels.of(state.data.settings.selectedModel)
            Text(stringResource(Res.string.download_title), fontSize = 32.sp, color = c.onSurface)
            Text(
                "${catalog.fileName} · ${catalog.quantization} · ${formatBytesUi(catalog.bytes, ui)}",
                fontSize = 13.sp,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            Column(
                Modifier.fillMaxWidth().clip(
                    RoundedCornerShape(28.dp),
                ).background(c.primaryContainer).padding(horizontal = 32.dp, vertical = 24.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(formatPercent(d.fraction, ui), fontSize = 45.sp, color = c.onPrimaryContainer)
                        Text(
                            "${formatBytesUi(d.bytesDownloaded, ui)} / ${formatBytesUi(d.totalBytes, ui)}",
                            fontSize = 16.sp,
                            color = c.onPrimaryContainer,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                        MiniStat(
                            stringResource(Res.string.download_stat_speed),
                            if (d.speedBps >
                                0
                            ) {
                                stringResource(Res.string.download_speed_per_s, formatBytesUi(d.speedBps, ui))
                            } else {
                                dash
                            },
                        )
                        MiniStat(stringResource(Res.string.download_stat_remaining), formatEta(remaining, d.speedBps))
                        MiniStat(
                            stringResource(Res.string.download_stat_segments),
                            stringResource(
                                Res.string.download_stat_ratio,
                                ((d.bytesDownloaded + 16_777_215) / 16_777_216).toInt(),
                                ((d.totalBytes + 16_777_215) / 16_777_216).toInt().coerceAtLeast(1),
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
                    Box(Modifier.weight(filled).fillMaxHeight().background(c.primary))
                    Box(Modifier.weight((1f - filled).coerceAtLeast(0.01f)).fillMaxHeight().background(c.inversePrimary))
                }
                Spacer(Modifier.height(16.dp))
                if (!d.done) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            d.paused || phase == DownloadPhase.Cancelled || phase == DownloadPhase.Failed ->
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
                            d.error?.text(ui) ?: stringResource(Res.string.download_auto_resume),
                            fontSize = 12.sp,
                            color = c.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            Column {
                SectionLabel(stringResource(Res.string.download_steps))
                Spacer(Modifier.height(8.dp))
                steps.forEachIndexed { i, s ->
                    DownloadStepRow(s)
                    if (i < steps.lastIndex) HorizontalDivider(color = c.surfaceContainerHighest)
                }
            }
        }
        WizardFooter(stepsFilled = 2, stepCount = state.installSteps, onBack = { onIntent(AppIntent.InstallBack) }) {
            if (state.translation.ttsReady) {
                FilledPill(
                    stringResource(Res.string.action_continue),
                    onClick = { onIntent(AppIntent.GoToStep(InstallStep.Voices)) },
                    enabled = d.done,
                )
            } else {
                FilledPill(
                    stringResource(Res.string.action_finish),
                    onClick = { onIntent(AppIntent.OpenApp) },
                    enabled = d.done,
                )
            }
        }
    }
}

@Composable
private fun VoicesStep(state: AppState, onIntent: (AppIntent) -> Unit) {
    val c = MaterialTheme.colorScheme
    val ui = state.data.settings.uiLanguage
    val style = state.data.settings.langNames
    val download = state.voiceDownload
    var voiceLang by rememberSaveable { mutableStateOf<String?>(null) }
    val openLang = voiceLang?.let { Languages.get(it) }
    val missingPicks = state.voicePicks.filter { PiperVoices.of(it)?.isOnDisk() != true }
    val selectedBytes = missingPicks.sumOf { PiperVoices.of(it)?.bytes ?: 0L }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 56.dp, end = 56.dp, top = 32.dp)) {
            StepLabel(stringResource(Res.string.install_step, 3, state.installSteps))
            Spacer(Modifier.height(10.dp))
            Text(
                openLang?.let { languageLabel(it.code, style) } ?: stringResource(Res.string.voices_title),
                fontSize = 32.sp,
                color = c.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            if (openLang == null) {
                Text(
                    stringResource(Res.string.voices_body),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (openLang == null) {
                    OutlinedPill(
                        stringResource(Res.string.voices_select_all),
                        onClick = { onIntent(AppIntent.SelectAllVoices()) },
                    )
                    OutlinedPill(
                        stringResource(Res.string.voices_select_none),
                        onClick = { onIntent(AppIntent.ClearVoicePicks()) },
                    )
                } else {
                    OutlinedPill(
                        stringResource(Res.string.action_back),
                        onClick = { voiceLang = null },
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    )
                    OutlinedPill(
                        stringResource(Res.string.voices_select_all),
                        onClick = { onIntent(AppIntent.SelectAllVoices(openLang.code)) },
                    )
                    OutlinedPill(
                        stringResource(Res.string.voices_select_none),
                        onClick = { onIntent(AppIntent.ClearVoicePicks(openLang.code)) },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(Res.string.voices_selected_size, state.voicePicks.size, formatBytesUi(selectedBytes, ui)),
                    fontSize = 13.sp,
                    color = c.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            if (download.running || download.error != null || download.finished.isNotEmpty()) {
                val current = download.lang?.let { PiperVoices.of(it) }
                val currentLabel = current?.let { "${languageLabel(it.lang, state.data.settings.langNames)} · ${it.displayName}" }
                    ?: download.lang?.let { languageLabel(it, state.data.settings.langNames) }.orEmpty()
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.primaryContainer).padding(24.dp)) {
                    Text(
                        if (download.lang != null) {
                            stringResource(Res.string.voices_download_title, currentLabel)
                        } else {
                            stringResource(Res.string.voices_title)
                        },
                        fontSize = 18.sp,
                        color = c.onPrimaryContainer,
                    )
                    if (download.total > 0) {
                        Text(
                            stringResource(Res.string.voices_download_progress, download.index, download.total),
                            fontSize = 13.sp,
                            color = c.onPrimaryContainer,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (download.running) {
                        LinearProgressIndicator(
                            progress = { download.fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        )
                        Text(
                            stringResource(Res.string.settings_model_downloading, formatPercent(download.fraction, ui)),
                            fontSize = 13.sp,
                            color = c.onPrimaryContainer,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    download.error?.let { err ->
                        Text(err.text(ui), fontSize = 13.sp, color = c.error, modifier = Modifier.padding(top = 8.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            download.running -> OutlinedPill(
                                stringResource(Res.string.action_cancel),
                                onClick = { onIntent(AppIntent.CancelVoiceDownload) },
                            )

                            download.error != null -> FilledPill(
                                stringResource(Res.string.action_resume),
                                onClick = { onIntent(AppIntent.RetryVoiceDownload) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            if (openLang == null) {
                Languages.all.filter { it.tts && PiperVoices.forLang(it.code).isNotEmpty() }.forEach { lang ->
                    val voices = PiperVoices.forLang(lang.code)
                    val picked = voices.count { it.id in state.voicePicks || it.isOnDisk() }
                    val installed = lang.code in state.translation.installedVoices
                    ModelPickCard(
                        title = languageLabel(lang.code, style),
                        body = stringResource(Res.string.settings_voices_summary, voices.size, picked),
                        selected = picked > 0 || installed,
                        trailing = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    ) { voiceLang = lang.code }
                    Spacer(Modifier.height(8.dp))
                }
            } else {
                PiperVoices.forLang(openLang.code).forEach { spec ->
                    val installed = spec.isOnDisk()
                    val selected = spec.id in state.voicePicks || installed
                    ModelPickCard(
                        title = spec.displayName,
                        body = buildString {
                            append(formatBytesUi(spec.bytes, ui))
                            if (installed) append(" · ").append(stringResource(Res.string.settings_model_installed))
                        },
                        selected = selected,
                    ) {
                        if (!installed && !download.running) onIntent(AppIntent.ToggleVoicePick(spec.id))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        WizardFooter(
            stepsFilled = 3,
            stepCount = state.installSteps,
            onBack = {
                if (voiceLang != null) {
                    voiceLang = null
                } else {
                    onIntent(AppIntent.InstallBack)
                }
            },
        ) {
            OutlinedPill(stringResource(Res.string.action_skip), onClick = { onIntent(AppIntent.OpenApp) })
            val canDownload = missingPicks.isNotEmpty() && !download.running
            if (canDownload) {
                FilledPill(stringResource(Res.string.action_download), onClick = { onIntent(AppIntent.DownloadVoices()) })
            } else {
                FilledPill(
                    stringResource(Res.string.action_finish),
                    onClick = { onIntent(AppIntent.OpenApp) },
                    enabled = !download.running,
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    val c = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.End) {
        Text(label, fontSize = 12.sp, color = c.onSurfaceVariant)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.onPrimaryContainer)
    }
}

@Composable
private fun DownloadStepRow(s: DownloadStepItem) {
    val c = MaterialTheme.colorScheme
    val tint = if (s.done || s.active) c.primary else c.outline
    val labelColor = if (s.done || s.active) c.onSurface else c.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(s.icon, null, Modifier.size(22.dp), tint = tint)
        Text(
            s.label,
            Modifier.weight(1f),
            fontSize = 16.sp,
            fontWeight = if (s.active) FontWeight.Medium else FontWeight.Normal,
            color = labelColor,
        )
        Text(s.status, fontSize = 12.sp, color = if (s.active) c.primary else c.onSurfaceVariant)
    }
}

// ── shared wizard chrome ──────────────────────────────────────────────────────

@Composable
private fun StepLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun WizardFooter(stepsFilled: Int, onBack: (() -> Unit)?, stepCount: Int = 2, primary: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 56.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(stepCount) { i ->
                Box(
                    Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(
                        if (i <
                            stepsFilled
                        ) {
                            c.primary
                        } else {
                            c.outlineVariant
                        },
                    ),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (onBack != null) OutlinedPill(stringResource(Res.string.action_back), onClick = onBack)
        primary()
    }
}

@Composable
private fun UiLanguagePicker(ui: UiLanguage, style: LangNameStyle, onIntent: (AppIntent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(languageLabel(ui.code, style), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Icon(
                Icons.Outlined.ArrowDropDown,
                stringResource(Res.string.settings_ui_language),
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            UiLanguage.entries.forEach { lang ->
                DropdownMenuItem(
                    text = { Text("${Languages.get(lang.code)?.native} · ${languageLabel(lang.code, style)}", fontSize = 14.sp) },
                    onClick = {
                        open = false
                        onIntent(AppIntent.SetUiLanguage(lang))
                    },
                )
            }
        }
    }
}
