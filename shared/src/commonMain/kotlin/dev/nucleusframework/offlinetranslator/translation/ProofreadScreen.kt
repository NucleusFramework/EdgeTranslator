package dev.nucleusframework.offlinetranslator.translation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.compose.stability.runtime.TraceRecomposition
import dev.nucleusframework.offlinetranslator.app.AppIntent
import dev.nucleusframework.offlinetranslator.engine.GemmaModel
import dev.nucleusframework.offlinetranslator.domain.UiLanguage
import dev.nucleusframework.offlinetranslator.domain.formatLatency
import dev.nucleusframework.offlinetranslator.ui.FilledPill
import dev.nucleusframework.offlinetranslator.ui.OutlinedPill
import dev.nucleusframework.offlinetranslator.ui.SectionLabel
import dev.nucleusframework.offlinetranslator.ui.TwoPane
import dev.nucleusframework.offlinetranslator.ui.VerticalContentScrollbar
import offlinetranslator.shared.generated.resources.Res
import offlinetranslator.shared.generated.resources.action_apply
import offlinetranslator.shared.generated.resources.action_copied
import offlinetranslator.shared.generated.resources.action_copy
import offlinetranslator.shared.generated.resources.char_count
import offlinetranslator.shared.generated.resources.latency_local
import offlinetranslator.shared.generated.resources.paragraph_count
import offlinetranslator.shared.generated.resources.proofread_header
import offlinetranslator.shared.generated.resources.proofread_placeholder
import offlinetranslator.shared.generated.resources.proofread_result_header
import offlinetranslator.shared.generated.resources.proofread_result_placeholder
import offlinetranslator.shared.generated.resources.target_install_model
import offlinetranslator.shared.generated.resources.translation_error
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Correcteur d'orthographe : même disposition que [TranslationContent], sans sélecteur de
 * langue — le modèle corrige dans la langue du texte saisi.
 */
@TraceRecomposition(tag = "proofread", threshold = 3)
@Composable
fun ProofreadContent(
    proofread: ProofreadState,
    uiLanguage: UiLanguage,
    modelInstalled: Boolean,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    TwoPane(
        first = { InputPanel(proofread, onIntent, it) },
        second = { ResultPanel(proofread, uiLanguage, modelInstalled, onIntent, it) },
        modifier = modifier,
    )
}

@Composable
private fun InputPanel(state: ProofreadState, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).border(1.dp, c.outlineVariant, RoundedCornerShape(20.dp))) {
        Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 20.dp), Alignment.CenterStart) {
            SectionLabel(stringResource(Res.string.proofread_header))
        }
        HorizontalDivider(color = c.surfaceContainerHighest)
        val focusRequester = remember { FocusRequester() }
        val scroll = rememberScrollState()
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            BasicTextField(
                value = state.text,
                onValueChange = { onIntent(AppIntent.SetProofreadText(it)) },
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
                                stringResource(Res.string.proofread_placeholder),
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
        HorizontalDivider(color = c.surfaceContainerHighest)
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "${pluralStringResource(Res.plurals.char_count, state.chars, state.chars)} / ${GemmaModel.MAX_INPUT_CHARS}",
                color = if (state.chars >= GemmaModel.MAX_INPUT_CHARS) c.error else c.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Text(
                pluralStringResource(Res.plurals.paragraph_count, state.paragraphs, state.paragraphs),
                color = c.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ResultPanel(
    state: ProofreadState,
    ui: UiLanguage,
    modelInstalled: Boolean,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(Res.string.proofread_result_header))
            Spacer(Modifier.weight(1f))
            val latency = formatLatency(state.latencyMs, ui)
            if (latency.isNotEmpty()) {
                Text(stringResource(Res.string.latency_local, latency), color = c.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        HorizontalDivider(color = c.surfaceContainerHighest)

        val streaming = state.status == TranslationStatus.WaitingEngine && modelInstalled
        val body = when {
            state.text.isBlank() -> stringResource(Res.string.proofread_result_placeholder)
            state.status == TranslationStatus.Error -> state.error ?: stringResource(Res.string.translation_error)
            !modelInstalled && state.result.isBlank() -> stringResource(Res.string.target_install_model)
            else -> null
        }
        if (body != null) {
            Text(body, Modifier.weight(1f).fillMaxWidth().padding(20.dp), color = c.onSurfaceVariant, fontSize = 18.sp, lineHeight = 28.sp)
        } else {
            val scroll = rememberScrollState()
            val shown = if (streaming) state.result + "▍" else state.result
            LaunchedEffect(shown) { scroll.animateScrollTo(scroll.maxValue) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    shown,
                    Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
                    color = c.onSurface,
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                )
                VerticalContentScrollbar(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledPill(
                stringResource(if (state.copied) Res.string.action_copied else Res.string.action_copy),
                onClick = { onIntent(AppIntent.CopyProofread) },
                icon = Icons.Outlined.ContentCopy,
                enabled = !state.copied && state.result.isNotBlank(),
            )
            OutlinedPill(
                stringResource(Res.string.action_apply),
                onClick = { onIntent(AppIntent.ApplyProofread) },
                enabled = state.result.isNotBlank() && state.result != state.text,
            )
        }
    }
}
