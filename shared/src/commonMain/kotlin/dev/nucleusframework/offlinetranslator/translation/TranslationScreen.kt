package dev.nucleusframework.offlinetranslator.translation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
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
import dev.nucleusframework.offlinetranslator.domain.paragraphCount
import dev.nucleusframework.offlinetranslator.engine.GemmaModel
import dev.nucleusframework.offlinetranslator.engine.MIC_BARS
import dev.nucleusframework.offlinetranslator.engine.PiperVoices
import dev.nucleusframework.offlinetranslator.platform.readDropPayload
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import dev.nucleusframework.offlinetranslator.ui.Chip
import dev.nucleusframework.offlinetranslator.ui.FilledPill
import dev.nucleusframework.offlinetranslator.ui.OutlinedPill
import dev.nucleusframework.offlinetranslator.ui.SectionLabel
import dev.nucleusframework.offlinetranslator.ui.TwoPane
import dev.nucleusframework.offlinetranslator.ui.VerticalContentScrollbar
import dev.nucleusframework.offlinetranslator.ui.languageLabel
import offlinetranslator.shared.generated.resources.Res
import offlinetranslator.shared.generated.resources.action_cancel
import offlinetranslator.shared.generated.resources.action_copied
import offlinetranslator.shared.generated.resources.action_copy
import offlinetranslator.shared.generated.resources.action_pause
import offlinetranslator.shared.generated.resources.action_resume
import offlinetranslator.shared.generated.resources.action_save
import offlinetranslator.shared.generated.resources.action_saved
import offlinetranslator.shared.generated.resources.alternatives_header
import offlinetranslator.shared.generated.resources.cd_dictate
import offlinetranslator.shared.generated.resources.cd_pick_image
import offlinetranslator.shared.generated.resources.drop_text_or_image
import offlinetranslator.shared.generated.resources.image_reading
import offlinetranslator.shared.generated.resources.cd_speak
import offlinetranslator.shared.generated.resources.cd_speak_stop
import offlinetranslator.shared.generated.resources.cd_swap_languages
import offlinetranslator.shared.generated.resources.char_count
import offlinetranslator.shared.generated.resources.latency_local
import offlinetranslator.shared.generated.resources.mic_listening
import offlinetranslator.shared.generated.resources.mic_speak_now
import offlinetranslator.shared.generated.resources.mic_tap_stop
import offlinetranslator.shared.generated.resources.mic_time
import offlinetranslator.shared.generated.resources.mic_transcribing
import offlinetranslator.shared.generated.resources.paragraph_count
import offlinetranslator.shared.generated.resources.source_header
import offlinetranslator.shared.generated.resources.source_placeholder
import offlinetranslator.shared.generated.resources.target_header
import offlinetranslator.shared.generated.resources.target_install_model
import offlinetranslator.shared.generated.resources.target_placeholder
import offlinetranslator.shared.generated.resources.translation_error
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

val LocalMicLevels = staticCompositionLocalOf<StateFlow<List<Float>>> { MutableStateFlow(emptyList()) }

private val IdleMicBars = List(MIC_BARS) { 0.06f }

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
    TwoPane(
        first = { SourcePanel(translation.toSourcePanel(), settings, voiceDownload, onIntent, it) },
        second = { TargetPanel(translation.toTargetPanel(), settings, modelInstalled, voiceDownload, onIntent, it) },
        modifier = modifier,
        between = { stacked ->
            Surface(
                onClick = { onIntent(AppIntent.SwapLanguages) },
                color = c.primaryContainer,
                contentColor = c.onPrimaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (stacked) Icons.Outlined.SwapVert else Icons.Outlined.SwapHoriz,
                        stringResource(Res.string.cd_swap_languages),
                        Modifier.size(20.dp),
                    )
                }
            }
        },
    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourcePanel(
    state: SourcePanelState,
    settings: UserSettings,
    voiceDownload: VoiceDownloadState,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val chars = state.text.length
    val paragraphs = paragraphCount(state.text)
    val shape = RoundedCornerShape(20.dp)
    var hovering by remember { mutableStateOf(false) }
    val acceptDrop = !state.imageBusy && state.micPhase == MicPhase.Idle
    val dropTarget = remember(onIntent) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                hovering = true
            }

            override fun onExited(event: DragAndDropEvent) {
                hovering = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                hovering = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering = false
                val payload = readDropPayload(event)
                val image = payload.image
                val text = payload.text
                return when {
                    payload.unsupported -> {
                        onIntent(AppIntent.DropUnsupported)
                        true
                    }
                    image != null && image.isNotEmpty() -> {
                        onIntent(AppIntent.TranslateDroppedImage(image))
                        true
                    }
                    !text.isNullOrBlank() -> {
                        onIntent(AppIntent.SetSourceText(text))
                        true
                    }
                    else -> false
                }
            }
        }
    }
    val stroke by animateColorAsState(if (hovering) c.primary else c.outlineVariant)
    Box(
        modifier
            .fillMaxSize()
            .clip(shape)
            .border(if (hovering) 2.dp else 1.dp, stroke, shape)
            .dragAndDropTarget(shouldStartDragAndDrop = { acceptDrop }, target = dropTarget),
    ) {
        Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp), Alignment.CenterStart) {
            LanguageHeader(settings, state.lang, LangRole.Source, onIntent)
        }
        HorizontalDivider(color = c.surfaceContainerHighest)
        if (state.imageBusy) {
            ScanningPane(Modifier.weight(1f).fillMaxWidth())
        } else if (state.micPhase != MicPhase.Idle) {
            ListeningPane(state.micPhase, onIntent, Modifier.weight(1f).fillMaxWidth())
        } else {
            val focusRequester = remember { FocusRequester() }
            val scroll = rememberScrollState()
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                BasicTextField(
                    value = state.text,
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
                            if (state.text.isEmpty()) {
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
            if (!state.imageBusy && state.micPhase == MicPhase.Idle) {
                Text(
                    "${pluralStringResource(Res.plurals.char_count, chars, chars)} / ${GemmaModel.MAX_INPUT_CHARS}",
                    color = if (chars >= GemmaModel.MAX_INPUT_CHARS) c.error else c.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Text(
                    pluralStringResource(Res.plurals.paragraph_count, paragraphs, paragraphs),
                    color = c.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            } else if (!state.imageBusy) {
                MicClock()
            }
            Spacer(Modifier.weight(1f))
            SpeakControls(
                lang = state.lang,
                textBlank = state.text.isBlank(),
                ttsReady = state.ttsReady,
                installed = state.voiceInstalled,
                active = state.speakActive,
                loading = state.speakLoading,
                playing = state.speakPlaying,
                paused = state.speakPaused,
                voiceDownload = voiceDownload,
                target = false,
                onIntent = onIntent,
            )
            Icon(
                Icons.Outlined.Image,
                stringResource(Res.string.cd_pick_image),
                Modifier.size(22.dp).clip(CircleShape).clickable { onIntent(AppIntent.TranslateImage) },
                tint = c.primary,
            )
            val listening = state.micPhase == MicPhase.Listening
            Icon(
                Icons.Outlined.Mic,
                stringResource(Res.string.cd_dictate),
                Modifier.size(22.dp).clip(CircleShape).clickable { onIntent(AppIntent.ToggleMic) },
                tint = when {
                    listening -> GoogleMicRed
                    Languages.hasAudio(state.lang) -> c.primary
                    else -> c.outline
                },
            )
        }
        }
        if (hovering) {
            Box(
                Modifier.fillMaxSize().background(c.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(Res.string.drop_text_or_image),
                    color = c.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ScanningPane(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(72.dp), strokeWidth = 2.dp, color = c.primary)
            Icon(Icons.Outlined.Description, null, Modifier.size(32.dp), tint = c.primary)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(Res.string.image_reading),
            color = c.onSurfaceVariant,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private val GoogleMicRed = Color(0xFFEA4335)

@Composable
private fun MicClock() {
    var sec by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            sec++
        }
    }
    val clock = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
    Text(stringResource(Res.string.mic_time, clock), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
}

@Composable
private fun ListeningPane(phase: MicPhase, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val listening = phase == MicPhase.Listening
    Column(
        modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MicLevelBars(listening)
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(
                when (phase) {
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
        MicPulseButton(phase, onIntent)
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
private fun MicLevelBars(listening: Boolean) {
    val levels by LocalMicLevels.current.collectAsState()
    val bars = levels.ifEmpty { IdleMicBars }
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        bars.forEach { level ->
            val h = animateFloatAsState(4f + level * 40f, label = "bar").value
            Box(
                Modifier.padding(horizontal = 1.5.dp).width(3.dp).height(h.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (listening) GoogleMicRed else c.outline),
            )
        }
    }
}

@Composable
private fun MicPulseButton(phase: MicPhase, onIntent: (AppIntent) -> Unit) {
    val listening = phase == MicPhase.Listening
    val levels by LocalMicLevels.current.collectAsState()
    val pulse = animateFloatAsState(if (listening) 1f + (levels.lastOrNull() ?: 0f) * 0.25f else 1f, label = "pulse").value
    val c = MaterialTheme.colorScheme
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
                if (phase == MicPhase.Starting) {
                    CircularProgressIndicator(Modifier.size(52.dp), strokeWidth = 2.dp, color = c.primary)
                }
            }
        }
    }
}

@Composable
private fun TargetPanel(
    state: TargetPanelState,
    settings: UserSettings,
    modelInstalled: Boolean,
    voiceDownload: VoiceDownloadState,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val ui = settings.uiLanguage
    val copy = remember(onIntent) { { onIntent(AppIntent.CopyTranslation) } }
    val save = remember(onIntent) { { onIntent(AppIntent.SaveToHistory) } }
    Column(modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            LanguageHeader(settings, state.lang, LangRole.Target, onIntent)
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
            state.sourceBlank -> stringResource(Res.string.target_placeholder)
            state.status == TranslationStatus.Error -> state.error ?: stringResource(Res.string.translation_error)
            !modelInstalled && state.text.isBlank() -> installModel
            else -> null
        }
        if (body != null) {
            Text(body, Modifier.weight(1f).fillMaxWidth().padding(20.dp), color = c.onSurfaceVariant, fontSize = 18.sp, lineHeight = 28.sp)
        } else {
            val scroll = rememberScrollState()
            val shown = if (streaming) state.text + "▍" else state.text
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
                onClick = copy,
                icon = Icons.Outlined.ContentCopy,
                enabled = !state.copied,
            )
            OutlinedPill(
                stringResource(if (state.saved) Res.string.action_saved else Res.string.action_save),
                onClick = save,
                enabled = !state.saved,
            )
            Spacer(Modifier.weight(1f))
            SpeakControls(
                lang = state.lang,
                textBlank = state.text.isBlank(),
                ttsReady = state.ttsReady,
                installed = state.voiceInstalled,
                active = state.speakActive,
                loading = state.speakLoading,
                playing = state.speakPlaying,
                paused = state.speakPaused,
                voiceDownload = voiceDownload,
                target = true,
                onIntent = onIntent,
            )
        }
    }
}

@Composable
private fun SpeakControls(
    lang: String,
    textBlank: Boolean,
    ttsReady: Boolean,
    installed: Boolean,
    active: Boolean,
    loading: Boolean,
    playing: Boolean,
    paused: Boolean,
    voiceDownload: VoiceDownloadState,
    target: Boolean,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!ttsReady || !Languages.hasTts(lang)) return
    val downloading = voiceDownload.busy && PiperVoices.covers(voiceDownload.lang, lang)
    val preparing = downloading || loading || (active && !playing && !paused)
    val c = MaterialTheme.colorScheme
    val iconMod = Modifier.size(22.dp).clip(CircleShape)
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (active) {
            Icon(
                Icons.Outlined.Stop,
                stringResource(Res.string.cd_speak_stop),
                iconMod.clickable { onIntent(AppIntent.StopSpeak) },
                tint = c.primary,
            )
        }
        when {
            preparing && downloading -> CircularProgressIndicator(
                progress = { voiceDownload.fraction.coerceIn(0f, 1f) },
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = c.primary,
                trackColor = c.outlineVariant,
            )

            preparing -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = c.primary,
            )

            else -> {
                val live = playing && !paused
                Icon(
                    imageVector = when {
                        live -> Icons.Outlined.Pause
                        paused -> Icons.Outlined.PlayArrow
                        else -> Icons.AutoMirrored.Outlined.VolumeUp
                    },
                    contentDescription = stringResource(
                        when {
                            live -> Res.string.action_pause
                            paused -> Res.string.action_resume
                            else -> Res.string.cd_speak
                        },
                    ),
                    modifier = iconMod.clickable(enabled = !installed || !textBlank || active) {
                        onIntent(AppIntent.ToggleSpeak(target))
                    },
                    tint = when {
                        !installed -> c.outline
                        active -> c.primary
                        textBlank -> c.outline
                        else -> c.onSurfaceVariant
                    },
                )
            }
        }
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
