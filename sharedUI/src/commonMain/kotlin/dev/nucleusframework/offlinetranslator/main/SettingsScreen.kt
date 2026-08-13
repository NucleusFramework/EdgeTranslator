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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nucleusframework.offlinetranslator.app.AppIntent
import dev.nucleusframework.offlinetranslator.app.AppState
import dev.nucleusframework.offlinetranslator.domain.LangNameStyle
import dev.nucleusframework.offlinetranslator.domain.Languages
import dev.nucleusframework.offlinetranslator.domain.LlmModel
import dev.nucleusframework.offlinetranslator.domain.UiLanguage
import dev.nucleusframework.offlinetranslator.domain.formatPercent
import dev.nucleusframework.offlinetranslator.engine.CatalogModel
import dev.nucleusframework.offlinetranslator.engine.GemmaModels
import dev.nucleusframework.offlinetranslator.engine.PiperVoices
import dev.nucleusframework.offlinetranslator.platform.systemUiLanguage
import dev.nucleusframework.offlinetranslator.ui.Chip
import dev.nucleusframework.offlinetranslator.ui.SectionLabel
import dev.nucleusframework.offlinetranslator.ui.formatBytesUi
import dev.nucleusframework.offlinetranslator.ui.languageLabel
import dev.nucleusframework.offlinetranslator.ui.text
import offlinetranslator.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(state: AppState, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 920.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            DisplaySection(state, onIntent)
            ModelSection(state, onIntent)
            if (state.translation.ttsReady) VoicesSection(state, onIntent)
            StorageSection(state.translation.ttsReady)
            ResetSection(onIntent)
        }
    }
}

// ---------------------------------------------------------------- sections

@Composable
private fun DisplaySection(state: AppState, onIntent: (AppIntent) -> Unit) {
    val settings = state.data.settings
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
private fun ModelSection(state: AppState, onIntent: (AppIntent) -> Unit) {
    val ui = state.data.settings.uiLanguage
    val selected = state.data.settings.selectedModel
    SettingsSection(stringResource(Res.string.settings_model)) {
        Divided(GemmaModels.all) { catalog ->
            val installed = catalog.isOnDisk() || (state.data.model.installed && state.data.model.id == catalog.id)
            val downloading = catalog.id == selected && state.download.running
            ChoiceRow(
                title = catalog.title(),
                body = catalog.body(formatBytesUi(catalog.bytes, ui)),
                installed = installed,
                selected = installed && state.data.model.installed && state.data.model.id == catalog.id,
                progress = if (downloading) state.download.fraction else null,
                progressLabel = if (downloading) {
                    stringResource(Res.string.settings_model_downloading, formatPercent(state.download.fraction, ui))
                } else {
                    null
                },
                error = if (catalog.id == selected) state.download.error?.text(ui) else null,
                onClick = { onIntent(AppIntent.SelectModel(catalog.id)) },
                onDelete = if (installed && !downloading) {
                    { onIntent(AppIntent.DeleteModel(catalog.id)) }
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
private fun VoicesSection(state: AppState, onIntent: (AppIntent) -> Unit) {
    val settings = state.data.settings
    val ui = settings.uiLanguage
    var openCode by rememberSaveable { mutableStateOf<String?>(null) }
    val openLang = openCode?.let { Languages.get(it) }

    if (openLang != null) {
        SettingsSection(languageLabel(openLang.code, settings.langNames), onBack = { openCode = null }) {
            Divided(PiperVoices.forLang(openLang.code)) { spec ->
                val installed = spec.isOnDisk()
                val downloading = state.voiceDownload.running &&
                    (state.voiceDownload.lang == spec.id || state.voiceDownload.lang == spec.lang)
                val active = settings.selectedVoices[spec.lang] == spec.id ||
                    (installed && settings.selectedVoices[spec.lang] == null && spec.id == PiperVoices.defaultFor(spec.lang)?.id)
                ChoiceRow(
                    title = spec.displayName,
                    body = formatBytesUi(spec.bytes, ui),
                    installed = installed,
                    selected = installed && active,
                    progress = if (downloading) state.voiceDownload.fraction else null,
                    progressLabel = if (downloading) {
                        stringResource(Res.string.settings_model_downloading, formatPercent(state.voiceDownload.fraction, ui))
                    } else {
                        null
                    },
                    error = if (downloading) state.voiceDownload.error?.text(ui) else null,
                    onClick = {
                        if (installed) {
                            onIntent(AppIntent.SelectVoice(spec.id))
                        } else {
                            onIntent(AppIntent.DownloadVoices(listOf(spec.id)))
                        }
                    },
                    onDelete = if (installed && !downloading) {
                        { onIntent(AppIntent.DeleteVoice(spec.id)) }
                    } else {
                        null
                    },
                )
            }
        }
        return
    }

    val busy = state.voiceDownload.lang.takeIf { state.voiceDownload.running }
    val active = setOf(state.translation.sourceLang, state.translation.targetLang)
    val mine = PiperVoices.visibleLangs(active, busy, PiperVoices.installed())
    val rest = PiperVoices.langs.filterNot { it in mine }

    SettingsSection(stringResource(Res.string.settings_voices)) {
        Divided(mine) { code ->
            val voices = PiperVoices.forLang(code)
            val downloading = PiperVoices.covers(busy, code)
            LinkRow(
                title = languageLabel(code, settings.langNames),
                body = stringResource(Res.string.settings_voices_summary, voices.size, voices.count { it.isOnDisk() }),
                progress = if (downloading) state.voiceDownload.fraction else null,
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
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    val status = error ?: progressLabel
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
                Text(
                    if (status != null) "$body · $status" else body,
                    fontSize = 13.sp,
                    color = if (error != null) c.error else c.onSurfaceVariant,
                )
            }
            if (onDelete != null) {
                Icon(
                    Icons.Outlined.Delete,
                    stringResource(Res.string.settings_model_delete),
                    Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onDelete),
                    tint = c.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(Icons.Outlined.Check, null, Modifier.size(20.dp), tint = c.primary)
            } else if (!installed) {
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
