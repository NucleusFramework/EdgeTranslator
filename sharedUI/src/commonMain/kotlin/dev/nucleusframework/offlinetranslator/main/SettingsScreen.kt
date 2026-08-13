package dev.nucleusframework.offlinetranslator.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.compose.stability.runtime.TraceRecomposition
import dev.nucleusframework.offlinetranslator.app.AppIntent
import dev.nucleusframework.offlinetranslator.domain.DownloadPhase
import dev.nucleusframework.offlinetranslator.domain.DownloadState
import dev.nucleusframework.offlinetranslator.domain.LangNameStyle
import dev.nucleusframework.offlinetranslator.domain.Languages
import dev.nucleusframework.offlinetranslator.domain.LlmBackend
import dev.nucleusframework.offlinetranslator.domain.LlmModel
import dev.nucleusframework.offlinetranslator.domain.ModelInfo
import dev.nucleusframework.offlinetranslator.domain.UiLanguage
import dev.nucleusframework.offlinetranslator.domain.UserSettings
import dev.nucleusframework.offlinetranslator.domain.VoiceDownloadState
import dev.nucleusframework.offlinetranslator.domain.formatEta
import dev.nucleusframework.offlinetranslator.domain.formatPercent
import dev.nucleusframework.offlinetranslator.engine.CatalogModel
import dev.nucleusframework.offlinetranslator.engine.GemmaModels
import dev.nucleusframework.offlinetranslator.engine.LlmRuntime
import dev.nucleusframework.offlinetranslator.engine.PiperVoices
import dev.nucleusframework.offlinetranslator.platform.systemUiLanguage
import dev.nucleusframework.offlinetranslator.ui.Chip
import dev.nucleusframework.offlinetranslator.ui.SectionLabel
import dev.nucleusframework.offlinetranslator.ui.VerticalContentScrollbar
import dev.nucleusframework.offlinetranslator.ui.formatBytesUi
import dev.nucleusframework.offlinetranslator.ui.languageLabel
import dev.nucleusframework.offlinetranslator.ui.text
import offlinetranslator.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@TraceRecomposition(tag = "settings")
@Composable
fun SettingsScreen(
    settings: UserSettings,
    model: ModelInfo,
    download: DownloadState,
    voiceDownload: VoiceDownloadState,
    ttsReady: Boolean,
    sourceLang: String,
    targetLang: String,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 920.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                DisplaySection(settings, onIntent)
                ModelSection(settings, model, download, onIntent)
                if (ttsReady) VoicesSection(settings, voiceDownload, sourceLang, targetLang, onIntent)
                StorageSection(ttsReady)
                ResetSection(onIntent)
            }
        }
        VerticalContentScrollbar(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

// ---------------------------------------------------------------- sections

@Composable
private fun DisplaySection(settings: UserSettings, onIntent: (AppIntent) -> Unit) {
    SettingsSection(stringResource(Res.string.settings_display)) {
        val auto = settings.uiLanguageAuto
        val nativeName = { lang: UiLanguage -> Languages.get(lang.code)?.native ?: lang.code }
        PickerRow(
            title = stringResource(Res.string.settings_ui_language),
            value = if (auto) stringResource(Res.string.settings_ui_language_system) else nativeName(settings.uiLanguage),
        ) { dismiss ->
            MenuChoice(
                label = stringResource(Res.string.settings_ui_language_system),
                detail = nativeName(systemUiLanguage()),
                checked = auto,
            ) {
                dismiss()
                onIntent(AppIntent.SetUiLanguage(null))
            }
            Divider()
            UiLanguage.entries.forEach { lang ->
                MenuChoice(label = nativeName(lang), detail = null, checked = !auto && lang == settings.uiLanguage) {
                    dismiss()
                    onIntent(AppIntent.SetUiLanguage(lang))
                }
            }
        }
        Divider()
        ChipsRow(stringResource(Res.string.settings_lang_names)) {
            Chip(
                stringResource(Res.string.lang_names_system),
                selected = settings.langNames == LangNameStyle.System,
                onClick = { onIntent(AppIntent.SetLangNameStyle(LangNameStyle.System)) },
            )
            Chip(
                stringResource(Res.string.lang_names_native),
                selected = settings.langNames == LangNameStyle.Native,
                onClick = { onIntent(AppIntent.SetLangNameStyle(LangNameStyle.Native)) },
            )
        }
    }
}

@Composable
private fun ModelSection(settings: UserSettings, model: ModelInfo, download: DownloadState, onIntent: (AppIntent) -> Unit) {
    val ui = settings.uiLanguage
    val selected = settings.selectedModel
    val gpuAvailable by LlmRuntime.gpuAvailable.collectAsState()
    SettingsSection(stringResource(Res.string.settings_model)) {
        ChipsRow(stringResource(Res.string.settings_backend)) {
            Chip(
                stringResource(Res.string.engine_auto),
                selected = settings.backend == LlmBackend.Auto,
                onClick = { onIntent(AppIntent.SetLlmBackend(LlmBackend.Auto)) },
            )
            Chip(
                stringResource(Res.string.engine_gpu),
                selected = settings.backend == LlmBackend.Gpu,
                onClick = { onIntent(AppIntent.SetLlmBackend(LlmBackend.Gpu)) },
                enabled = gpuAvailable != false,
            )
            Chip(
                stringResource(Res.string.engine_cpu),
                selected = settings.backend == LlmBackend.Cpu,
                onClick = { onIntent(AppIntent.SetLlmBackend(LlmBackend.Cpu)) },
            )
        }
        Divider()
        Divided(GemmaModels.all) { catalog ->
            val installed = catalog.isOnDisk() || (model.installed && model.id == catalog.id)
            val mine = catalog.id == selected
            val failed = mine && download.phase == DownloadPhase.Failed
            val paused = mine && download.paused
            val running = mine && download.running
            val inFlight = running || paused || failed
            ChoiceRow(
                title = catalog.title(),
                body = catalog.body(formatBytesUi(catalog.bytes, ui)),
                installed = installed,
                selected = installed && model.installed && model.id == catalog.id,
                muted = !installed && !inFlight,
                progress = if (inFlight) download.fraction else null,
                progressLabel = if (running || paused) {
                    downloadStats(download.fraction, download.bytesDownloaded, download.totalBytes, download.speedBps, ui)
                } else {
                    null
                },
                error = if (failed) download.error?.text(ui) else null,
                onClick = if (installed) {
                    { onIntent(AppIntent.SelectModel(catalog.id)) }
                } else {
                    null
                },
                onDelete = if (installed && !inFlight) {
                    { onIntent(AppIntent.DeleteModel(catalog.id)) }
                } else {
                    null
                },
                onDownload = if (!installed && !inFlight) {
                    { onIntent(AppIntent.DownloadModel(catalog.id)) }
                } else {
                    null
                },
                onPause = if (running) {
                    { onIntent(AppIntent.PauseDownload) }
                } else {
                    null
                },
                onResume = if (paused || failed) {
                    {
                        onIntent(if (failed) AppIntent.DownloadModel(catalog.id) else AppIntent.ResumeDownload)
                    }
                } else {
                    null
                },
                onCancel = if (running || paused) {
                    { onIntent(AppIntent.CancelDownload) }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * Lists the voices you actually have, not the 30-language catalog: installed languages, the two
 * languages currently being translated, and whatever is downloading. Everything else lives behind
 * the "add a voice" picker at the bottom.
 */
@Composable
private fun VoicesSection(
    settings: UserSettings,
    voiceDownload: VoiceDownloadState,
    sourceLang: String,
    targetLang: String,
    onIntent: (AppIntent) -> Unit,
) {
    val ui = settings.uiLanguage
    var openCode by rememberSaveable { mutableStateOf<String?>(null) }
    val openLang = openCode?.let { Languages.get(it) }

    if (openLang != null) {
        SettingsSection(languageLabel(openLang.code, settings.langNames), onBack = { openCode = null }) {
            Divided(PiperVoices.forLang(openLang.code)) { spec ->
                val installed = spec.isOnDisk()
                val mine = voiceDownload.lang == spec.id || voiceDownload.lang == spec.lang
                val failed = mine && voiceDownload.error != null
                val paused = mine && voiceDownload.paused
                val running = mine && voiceDownload.running
                val inFlight = running || paused || failed
                val active = settings.selectedVoices[spec.lang] == spec.id ||
                    (installed && settings.selectedVoices[spec.lang] == null && spec.id == PiperVoices.defaultFor(spec.lang)?.id)
                ChoiceRow(
                    title = spec.displayName,
                    body = formatBytesUi(spec.bytes, ui),
                    installed = installed,
                    selected = installed && active,
                    muted = !installed && !inFlight,
                    progress = if (inFlight) voiceDownload.fraction else null,
                    progressLabel = if (running || paused) {
                        downloadStats(
                            voiceDownload.fraction,
                            voiceDownload.bytesDownloaded,
                            voiceDownload.totalBytes,
                            voiceDownload.speedBps,
                            ui,
                        )
                    } else {
                        null
                    },
                    error = if (failed) voiceDownload.error.text(ui) else null,
                    onClick = if (installed) {
                        { onIntent(AppIntent.SelectVoice(spec.id)) }
                    } else {
                        null
                    },
                    onDelete = if (installed && !inFlight) {
                        { onIntent(AppIntent.DeleteVoice(spec.id)) }
                    } else {
                        null
                    },
                    onDownload = if (!installed && !inFlight) {
                        { onIntent(AppIntent.DownloadVoices(listOf(spec.id))) }
                    } else {
                        null
                    },
                    onPause = if (running) {
                        { onIntent(AppIntent.PauseVoiceDownload) }
                    } else {
                        null
                    },
                    onResume = if (paused || failed) {
                        {
                            onIntent(
                                if (failed) AppIntent.DownloadVoices(listOf(spec.id)) else AppIntent.ResumeVoiceDownload,
                            )
                        }
                    } else {
                        null
                    },
                    onCancel = if (running || paused) {
                        { onIntent(AppIntent.CancelVoiceDownload) }
                    } else {
                        null
                    },
                )
            }
        }
        return
    }

    val busy = voiceDownload.lang.takeIf { voiceDownload.busy }
    val active = setOf(sourceLang, targetLang)
    val mine = PiperVoices.visibleLangs(active, busy, PiperVoices.installed())
    val rest = PiperVoices.langs.filterNot { it in mine }

    SettingsSection(stringResource(Res.string.settings_voices)) {
        Divided(mine) { code ->
            val voices = PiperVoices.forLang(code)
            val downloading = PiperVoices.covers(busy, code)
            LinkRow(
                title = languageLabel(code, settings.langNames),
                body = stringResource(Res.string.settings_voices_summary, voices.size, voices.count { it.isOnDisk() }),
                progress = if (downloading) voiceDownload.fraction else null,
                onClick = { openCode = code },
            )
        }
        if (rest.isNotEmpty()) {
            if (mine.isNotEmpty()) Divider()
            AddRow(stringResource(Res.string.settings_voices_add)) { dismiss ->
                rest.forEach { code ->
                    val size = PiperVoices.defaultFor(code)?.bytes ?: 0L
                    DropdownMenuItem(
                        text = { Text("${languageLabel(code, settings.langNames)} · ${formatBytesUi(size, ui)}", fontSize = 14.sp) },
                        onClick = {
                            dismiss()
                            onIntent(AppIntent.DownloadVoices(listOf(code)))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageSection(tts: Boolean) {
    SettingsSection(stringResource(Res.string.settings_storage)) {
        InfoRow(stringResource(Res.string.settings_model_location), GemmaModels.dir())
        if (tts) {
            Divider()
            InfoRow(stringResource(Res.string.settings_voices_location), PiperVoices.dir())
        }
    }
}

@Composable
private fun ResetSection(onIntent: (AppIntent) -> Unit) {
    val c = MaterialTheme.colorScheme
    SettingsSection(stringResource(Res.string.settings_reset)) {
        Column(
            Modifier.fillMaxWidth().clickable { onIntent(AppIntent.ResetApp) }.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(stringResource(Res.string.settings_reset_title), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.error)
            Text(stringResource(Res.string.settings_reset_body), fontSize = 13.sp, color = c.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- rows

@Composable
private fun SettingsSection(title: String, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onBack == null) {
            SectionLabel(title)
        } else {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(Res.string.action_back), Modifier.size(16.dp), tint = c.primary)
                SectionLabel(title, color = c.primary)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainer), content = content)
    }
}

@Composable
private fun downloadStats(fraction: Float, downloaded: Long, total: Long, speedBps: Long, ui: UiLanguage): String {
    val speed = if (speedBps > 0) {
        stringResource(Res.string.download_speed_per_s, formatBytesUi(speedBps, ui))
    } else {
        stringResource(Res.string.em_dash)
    }
    return listOf(
        formatPercent(fraction, ui),
        "${formatBytesUi(downloaded, ui)} / ${formatBytesUi(total, ui)}",
        speed,
        formatEta((total - downloaded).coerceAtLeast(0), speedBps),
    ).joinToString(" · ")
}

/** Title + description + one status line, with delete behind an icon instead of a standing red link. */
@Composable
private fun ChoiceRow(
    title: String,
    body: String,
    installed: Boolean,
    selected: Boolean,
    progress: Float?,
    progressLabel: String?,
    error: String?,
    onClick: (() -> Unit)?,
    onDelete: (() -> Unit)? = null,
    muted: Boolean = false,
    onDownload: (() -> Unit)? = null,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    val status = error ?: progressLabel
    val titleColor = if (muted) c.onSurface.copy(alpha = 0.45f) else c.onSurface
    val bodyColor = when {
        error != null -> c.error
        muted -> c.onSurfaceVariant.copy(alpha = 0.45f)
        else -> c.onSurfaceVariant
    }
    val row = Modifier.fillMaxWidth().then(
        if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
    Column(row.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = titleColor)
                Text(
                    if (status != null) "$body · $status" else body,
                    fontSize = 13.sp,
                    color = bodyColor,
                )
            }
            if (onDownload != null) {
                RowAction(Icons.Outlined.Download, stringResource(Res.string.action_download), onDownload, c.primary)
            }
            if (onPause != null) {
                RowAction(Icons.Outlined.Pause, stringResource(Res.string.action_pause), onPause, c.onSurfaceVariant)
            }
            if (onResume != null) {
                RowAction(Icons.Outlined.PlayArrow, stringResource(Res.string.action_resume), onResume, c.primary)
            }
            if (onCancel != null) {
                RowAction(Icons.Outlined.Close, stringResource(Res.string.action_cancel), onCancel, c.onSurfaceVariant)
            }
            if (onDelete != null) {
                RowAction(Icons.Outlined.Delete, stringResource(Res.string.settings_model_delete), onDelete, c.onSurfaceVariant)
            }
            if (selected) {
                Icon(Icons.Outlined.Check, null, Modifier.size(20.dp), tint = c.primary)
            } else if (!installed && onDownload == null && progress == null) {
                Text(stringResource(Res.string.settings_model_missing), fontSize = 12.sp, color = c.onSurfaceVariant)
            }
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun RowAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color) {
    Icon(
        icon,
        label,
        Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        tint = tint,
    )
}

/** Drill-in row: taps through to a sub-list. */
@Composable
private fun LinkRow(title: String, body: String, progress: Float?, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
                Text(body, fontSize = 13.sp, color = c.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = c.onSurfaceVariant)
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

/**
 * Label on the left, current value + dropdown on the right.
 * The [Box] hugs the value, not the whole row — anchoring the menu to a full-width row would drop
 * it at the row's left edge, far from the control you clicked.
 */
@Composable
private fun PickerRow(title: String, value: String, menu: @Composable (dismiss: () -> Unit) -> Unit) {
    val c = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontSize = 15.sp, color = c.onSurface, modifier = Modifier.weight(1f))
        Box {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, fontSize = 15.sp, color = c.primary)
                Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(20.dp), tint = c.onSurfaceVariant)
            }
            DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                menu { open = false }
            }
        }
    }
}

/** One dropdown entry, with an optional greyed detail and a check when it is the current value. */
@Composable
private fun MenuChoice(label: String, detail: String?, checked: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, fontSize = 14.sp, color = c.onSurface)
                if (detail != null) Text(detail, fontSize = 13.sp, color = c.onSurfaceVariant)
            }
        },
        onClick = onClick,
        trailingIcon = if (checked) {
            { Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = c.primary) }
        } else {
            null
        },
    )
}

/** "+ Add …" row that opens a picker of everything not already listed. */
@Composable
private fun AddRow(title: String, menu: @Composable (dismiss: () -> Unit) -> Unit) {
    val c = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(20.dp), tint = c.primary)
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.primary)
            }
            DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                menu { open = false }
            }
        }
    }
}

/** Read-only label + value. */
@Composable
private fun InfoRow(title: String, value: String) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(title, fontSize = 15.sp, color = c.onSurface)
        Text(value, fontSize = 13.sp, color = c.onSurfaceVariant)
    }
}

/** Label on the left, chips on the right — for two- or three-way choices. */
@Composable
private fun ChipsRow(title: String, chips: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        chips()
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)

@Composable
private fun <T> Divided(items: List<T>, row: @Composable (T) -> Unit) {
    items.forEachIndexed { i, item ->
        if (i > 0) Divider()
        row(item)
    }
}

@Composable
private fun CatalogModel.title(): String = stringResource(
    if (id == LlmModel.Precise) Res.string.model_precise_title else Res.string.model_fast_title,
)

@Composable
private fun CatalogModel.body(size: String): String = stringResource(
    if (id == LlmModel.Precise) Res.string.model_precise_body else Res.string.model_fast_body,
    size,
)
