package dev.nucleusframework.offlinetranslator.translation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.compose.stability.runtime.TraceRecomposition
import dev.nucleusframework.offlinetranslator.app.AppIntent
import dev.nucleusframework.offlinetranslator.domain.LangRole
import dev.nucleusframework.offlinetranslator.domain.Languages
import dev.nucleusframework.offlinetranslator.domain.UserSettings
import dev.nucleusframework.offlinetranslator.domain.VoiceDownloadState
import dev.nucleusframework.offlinetranslator.domain.formatLatency
import dev.nucleusframework.offlinetranslator.engine.PiperVoices
import dev.nucleusframework.offlinetranslator.translation.MicPhase
import dev.nucleusframework.offlinetranslator.translation.TranslationStatus
import dev.nucleusframework.offlinetranslator.ui.Chip
import dev.nucleusframework.offlinetranslator.ui.FilledPill
import dev.nucleusframework.offlinetranslator.ui.OutlinedPill
import dev.nucleusframework.offlinetranslator.ui.SectionLabel
import dev.nucleusframework.offlinetranslator.ui.VerticalContentScrollbar
import dev.nucleusframework.offlinetranslator.ui.languageLabel
import offlinetranslator.sharedui.generated.resources.Res
import offlinetranslator.sharedui.generated.resources.action_cancel
import offlinetranslator.sharedui.generated.resources.action_copied
import offlinetranslator.sharedui.generated.resources.action_copy
import offlinetranslator.sharedui.generated.resources.action_save
import offlinetranslator.sharedui.generated.resources.action_saved
import offlinetranslator.sharedui.generated.resources.alternatives_header
import offlinetranslator.sharedui.generated.resources.cd_dictate
import offlinetranslator.sharedui.generated.resources.cd_speak
import offlinetranslator.sharedui.generated.resources.cd_speak_loading
import offlinetranslator.sharedui.generated.resources.cd_speak_stop
import offlinetranslator.sharedui.generated.resources.cd_swap_languages
import offlinetranslator.sharedui.generated.resources.char_count
import offlinetranslator.sharedui.generated.resources.latency_local
import offlinetranslator.sharedui.generated.resources.mic_listening
import offlinetranslator.sharedui.generated.resources.mic_speak_now
import offlinetranslator.sharedui.generated.resources.mic_tap_stop
import offlinetranslator.sharedui.generated.resources.mic_time
import offlinetranslator.sharedui.generated.resources.mic_transcribing
import offlinetranslator.sharedui.generated.resources.paragraph_count
import offlinetranslator.sharedui.generated.resources.source_header
import offlinetranslator.sharedui.generated.resources.source_placeholder
import offlinetranslator.sharedui.generated.resources.target_header
import offlinetranslator.sharedui.generated.resources.target_install_model
import offlinetranslator.sharedui.generated.resources.target_placeholder
import offlinetranslator.sharedui.generated.resources.translation_error
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Content of design B1 — "Traduction" (expanded). Rendered inside the app shell:
 * two panels whose headers double as language pickers, swap button between them.
 */
@TraceRecomposition(tag = "translate", threshold = 3, traceStates = true)
@Composable
fun TranslationContent(
    translation: TranslationState,
    settings: UserSettings,
    modelInstalled: Boolean,
    voiceDownload: VoiceDownloadState,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourcePanel(translation, settings, voiceDownload, onIntent, Modifier.weight(1f))
        Surface(
            onClick = { onIntent(AppIntent.SwapLanguages) },
            color = c.primaryContainer,
            contentColor = c.onPrimaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.SwapHoriz, stringResource(Res.string.cd_swap_languages), Modifier.size(20.dp))
            }
        }
        TargetPanel(translation, settings, modelInstalled, voiceDownload, onIntent, Modifier.weight(1f))
    }
}

/** Panel header: the language name itself is the dropdown that picks it. */
@Composable
private fun LanguageHeader(settings: UserSettings, code: String, role: LangRole, onIntent: (AppIntent) -> Unit) {
    val c = MaterialTheme.colorScheme
    val source = role == LangRole.Source
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(
                stringResource(
                    if (source) Res.string.source_header else Res.string.target_header,
                    languageLabel(code, settings.langNames),
                ),
            )
            Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp), tint = c.onSurfaceVariant)
        }
        // ponytail: plain scrolling list, 36 languages fits — add a search field if the catalog grows.
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            Languages.search("", settings.uiLanguage, includeAuto = source, style = settings.langNames).forEach { lang ->
                DropdownMenuItem(
                    text = { Text(languageLabel(lang.code, settings.langNames), fontSize = 14.sp) },
                    onClick = {
                        open = false
                        onIntent(AppIntent.ChooseLanguage(lang.code, role))
                    },
                    trailingIcon = if (lang.audio || lang.tts) {
                        {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (lang.audio) {
                                    Icon(Icons.Outlined.Mic, null, Modifier.size(16.dp), tint = c.onSurfaceVariant)
                                }
                                if (lang.tts) {
                                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, Modifier.size(16.dp), tint = c.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun SourcePanel(
    state: TranslationState,
    settings: UserSettings,
    voiceDownload: VoiceDownloadState,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxHeight().clip(RoundedCornerShape(20.dp)).border(1.dp, c.outlineVariant, RoundedCornerShape(20.dp))) {
        Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp), Alignment.CenterStart) {
            LanguageHeader(settings, state.sourceLang, LangRole.Source, onIntent)
        }
        HorizontalDivider(color = c.surfaceContainerHighest)
        if (state.micPhase != MicPhase.Idle) {
            ListeningPane(state, onIntent, Modifier.weight(1f).fillMaxWidth())
        } else {
            val focusRequester = remember { FocusRequester() }
            val scroll = rememberScrollState()
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                BasicTextField(
                    value = state.sourceText,
                    onValueChange = { onIntent(AppIntent.SetSourceText(it)) },
                    textStyle = TextStyle(color = c.onSurface, fontSize = 18.sp, lineHeight = 28.sp),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(20.dp)
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box {
                            if (state.sourceText.isEmpty()) {
                                Text(
                                    stringResource(Res.string.source_placeholder),
                                    color = c.onSurfaceVariant,
                                    fontSize = 18.sp,
                                    lineHeight = 28.sp,
                                )
                            }
                            inner()
                        }
                    },
                )
                VerticalContentScrollbar(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
        HorizontalDivider(color = c.surfaceContainerHighest)
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.micPhase == MicPhase.Idle) {
                Text(
                    pluralStringResource(Res.plurals.char_count, state.sourceChars, state.sourceChars),
                    color = c.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Text(
                    pluralStringResource(Res.plurals.paragraph_count, state.sourceParagraphs, state.sourceParagraphs),
                    color = c.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            } else {
                val sec = (state.micElapsedMs / 1000).toInt()
                val clock = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
                Text(stringResource(Res.string.mic_time, clock), color = c.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            SpeakIcon(state, voiceDownload, target = false, onIntent = onIntent)
            val listening = state.micPhase == MicPhase.Listening
            val googleRed = Color(0xFFEA4335)
            Icon(
                Icons.Outlined.Mic,
                stringResource(Res.string.cd_dictate),
                Modifier.size(22.dp).clip(CircleShape).clickable { onIntent(AppIntent.ToggleMic) },
                tint = when {
                    listening -> googleRed
                    Languages.hasAudio(state.sourceLang) -> c.primary
                    else -> c.outline
                },
            )
        }
    }
}

private val GoogleMicRed = Color(0xFFEA4335)

@Composable
private fun ListeningPane(state: TranslationState, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val listening = state.micPhase == MicPhase.Listening
    Column(
        modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            val bars = state.micLevels.ifEmpty { List(24) { 0.06f } }
            bars.forEach { level ->
                val h = animateFloatAsState(4f + level * 40f, label = "bar").value
                Box(
                    Modifier.padding(horizontal = 1.5.dp).width(3.dp).height(h.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (listening) GoogleMicRed else c.outline),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(
                when (state.micPhase) {
                    MicPhase.Listening -> Res.string.mic_speak_now
                    MicPhase.Starting -> Res.string.mic_listening
                    else -> Res.string.mic_transcribing
                },
            ),
            color = if (listening) GoogleMicRed else c.onSurfaceVariant,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(20.dp))
        val pulse = animateFloatAsState(if (listening) 1f + (state.micLevels.lastOrNull() ?: 0f) * 0.25f else 1f, label = "pulse").value
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size((88 * pulse).dp).clip(CircleShape).background(GoogleMicRed.copy(alpha = 0.16f)))
            Surface(
                onClick = { onIntent(AppIntent.ToggleMic) },
                color = if (listening) GoogleMicRed else c.surfaceContainerHighest,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Mic, stringResource(Res.string.cd_dictate), Modifier.size(32.dp))
                    if (state.micPhase == MicPhase.Starting) {
                        CircularProgressIndicator(Modifier.size(52.dp), strokeWidth = 2.dp, color = c.primary)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (listening) {
            Text(stringResource(Res.string.mic_tap_stop), color = c.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Icon(
                Icons.Outlined.Close,
                stringResource(Res.string.action_cancel),
                Modifier.size(20.dp).clip(CircleShape).clickable { onIntent(AppIntent.CancelMic) },
                tint = c.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TargetPanel(
    state: TranslationState,
    settings: UserSettings,
    modelInstalled: Boolean,
    voiceDownload: VoiceDownloadState,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val ui = settings.uiLanguage
    Column(modifier.fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            LanguageHeader(settings, state.targetLang, LangRole.Target, onIntent)
            Spacer(Modifier.weight(1f))
            val latency = formatLatency(state.latencyMs, ui)
            if (latency.isNotEmpty()) {
                Text(
                    stringResource(Res.string.latency_local, latency),
                    Modifier.padding(end = 6.dp),
                    color = c.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
        HorizontalDivider(color = c.surfaceContainerHighest)

        val streaming = state.status == TranslationStatus.WaitingEngine && modelInstalled
        val installModel = stringResource(Res.string.target_install_model)
        val body = when {
            state.sourceText.isBlank() -> stringResource(Res.string.target_placeholder)
            state.status == TranslationStatus.Error -> state.error ?: stringResource(Res.string.translation_error)
            !modelInstalled && state.targetText.isBlank() -> installModel
            else -> null
        }
        if (body != null) {
            Text(body, Modifier.weight(1f).fillMaxWidth().padding(20.dp), color = c.onSurfaceVariant, fontSize = 18.sp, lineHeight = 28.sp)
        } else {
            val scroll = rememberScrollState()
            val shown = if (streaming) state.targetText + "▍" else state.targetText
            LaunchedEffect(shown) { scroll.animateScrollTo(scroll.maxValue) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    highlighted(shown, state.highlightTerm, c.primaryContainer),
                    Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
                    color = c.onSurface,
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                )
                VerticalContentScrollbar(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }

        if (state.alternatives.isNotEmpty()) {
            HorizontalDivider(color = c.surfaceContainerHighest)
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(Res.string.alternatives_header, state.alternativesFor))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.alternatives.forEach { alt ->
                        Chip(
                            label = alt.term,
                            selected = alt.term == state.selectedAlternative,
                            onClick = { onIntent(AppIntent.SelectAlternative(alt.term)) },
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledPill(
                stringResource(if (state.copied) Res.string.action_copied else Res.string.action_copy),
                onClick = { onIntent(AppIntent.CopyTranslation) },
                icon = Icons.Outlined.ContentCopy,
                enabled = !state.copied,
            )
            OutlinedPill(
                stringResource(if (state.saved) Res.string.action_saved else Res.string.action_save),
                onClick = { onIntent(AppIntent.SaveToHistory) },
                enabled = !state.saved,
            )
            Spacer(Modifier.weight(1f))
            SpeakIcon(state, voiceDownload, target = true, onIntent = onIntent)
        }
    }
}

@Composable
private fun SpeakIcon(state: TranslationState, voiceDownload: VoiceDownloadState, target: Boolean, onIntent: (AppIntent) -> Unit) {
    val lang = if (target) state.targetLang else state.sourceLang
    if (!state.ttsReady || !Languages.hasTts(lang)) return
    val installed = lang in state.installedVoices
    val text = if (target) state.targetText else state.sourceText
    val active = state.speakTarget == target
    val downloading = voiceDownload.running && PiperVoices.covers(voiceDownload.lang, lang)
    val loading = (state.speakBusy && active) || downloading
    val c = MaterialTheme.colorScheme
    val indicator = Modifier.size(22.dp)
    when {
        loading && downloading -> CircularProgressIndicator(
            progress = { voiceDownload.fraction.coerceIn(0f, 1f) },
            modifier = indicator,
            strokeWidth = 2.dp,
            color = c.primary,
            trackColor = c.outlineVariant,
        )

        loading -> CircularProgressIndicator(
            modifier = indicator.clickable(
                onClickLabel = stringResource(Res.string.cd_speak_loading),
            ) { onIntent(AppIntent.ToggleSpeak(target)) },
            strokeWidth = 2.dp,
            color = c.primary,
        )

        else -> Icon(
            Icons.AutoMirrored.Outlined.VolumeUp,
            stringResource(if (active) Res.string.cd_speak_stop else Res.string.cd_speak),
            Modifier.size(22.dp).clip(CircleShape).clickable(enabled = !installed || text.isNotBlank() || active) {
                onIntent(AppIntent.ToggleSpeak(target))
            },
            tint = when {
                !installed -> c.outline
                active -> c.primary
                text.isBlank() -> c.outline
                else -> c.onSurfaceVariant
            },
        )
    }
}

private fun highlighted(text: String, term: String, bg: Color) = buildAnnotatedString {
    val i = text.indexOf(term)
    if (i < 0) {
        append(text)
        return@buildAnnotatedString
    }
    append(text.substring(0, i))
    withStyle(SpanStyle(background = bg)) { append(term) }
    append(text.substring(i + term.length))
}
