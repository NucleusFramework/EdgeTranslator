package dev.nucleusframework.offlinetranslator.app

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.skydoves.compose.stability.runtime.TraceRecomposition
import dev.nucleusframework.offlinetranslator.install.InstallScreen
import dev.nucleusframework.offlinetranslator.main.AboutScreen
import dev.nucleusframework.offlinetranslator.main.HistoryScreen
import dev.nucleusframework.offlinetranslator.main.MainShell
import dev.nucleusframework.offlinetranslator.main.SettingsScreen
import dev.nucleusframework.offlinetranslator.translation.ProofreadContent
import dev.nucleusframework.offlinetranslator.translation.TranslationContent
import dev.nucleusframework.offlinetranslator.ui.AppDialogHost
import dev.nucleusframework.offlinetranslator.ui.MessageBar

@TraceRecomposition(tag = "root")
@Composable
fun RootScreen(state: AppState, backStack: NavBackStack<AppKey>, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        val current = backStack.last()
        if (current.isMain()) {
            MainShell(
                destination = current,
                uiLanguage = state.data.settings.uiLanguage,
                offline = state.offline,
                modelId = state.data.model.id,
                onIntent = onIntent,
            ) {
                AppNavDisplay(backStack, state, onIntent)
            }
        } else {
            AppNavDisplay(backStack, state, onIntent)
        }
        MessageBar(
            message = state.message,
            onDismiss = remember(onIntent) { { onIntent(AppIntent.DismissMessage) } },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        AppDialogHost(
            dialog = state.dialog,
            speakLoading = state.translation.speakLoading,
            speakTarget = state.translation.speakTarget,
            settings = state.data.settings,
            onIntent = onIntent,
        )
    }
}

@Composable
private fun AppNavDisplay(backStack: NavBackStack<AppKey>, state: AppState, onIntent: (AppIntent) -> Unit) {
    val firstPage = remember { backStack.last() }
    var animatePages by remember { mutableStateOf(false) }
    if (!animatePages && backStack.last() != firstPage) animatePages = true
    val transform = if (animatePages) pageFade() else pageNone()
    NavDisplay(
        backStack = backStack,
        onBack = {
            when (backStack.last()) {
                AppKey.Download, AppKey.Voices -> onIntent(AppIntent.InstallBack)
                else -> Unit
            }
        },
        transitionSpec = { transform },
        popTransitionSpec = { transform },
        predictivePopTransitionSpec = { transform },
        entryProvider = entryProvider {
            entry<AppKey.Welcome> { InstallEntry(InstallStep.Welcome, state, onIntent) }
            entry<AppKey.Download> { InstallEntry(InstallStep.Download, state, onIntent) }
            entry<AppKey.Voices> { InstallEntry(InstallStep.Voices, state, onIntent) }
            entry<AppKey.Translate> {
                TranslationContent(
                    translation = state.translation,
                    settings = state.data.settings,
                    modelInstalled = state.data.model.installed,
                    voiceDownload = state.voiceDownload,
                    onIntent = onIntent,
                )
            }
            entry<AppKey.Proofread> {
                ProofreadContent(
                    proofread = state.proofread,
                    uiLanguage = state.data.settings.uiLanguage,
                    modelInstalled = state.data.model.installed,
                    onIntent = onIntent,
                )
            }
            entry<AppKey.History> {
                HistoryScreen(
                    data = state.data,
                    query = state.historyQuery,
                    filter = state.historyFilter,
                    onIntent = onIntent,
                )
            }
            entry<AppKey.Settings> {
                SettingsScreen(
                    settings = state.data.settings,
                    model = state.data.model,
                    download = state.download,
                    voiceDownload = state.voiceDownload,
                    ttsReady = state.translation.ttsReady,
                    sourceLang = state.translation.sourceLang,
                    targetLang = state.translation.targetLang,
                    onIntent = onIntent,
                )
            }
            entry<AppKey.About> { AboutScreen() }
        },
    )
}

@Composable
private fun InstallEntry(step: InstallStep, state: AppState, onIntent: (AppIntent) -> Unit) {
    InstallScreen(
        step = step,
        settings = state.data.settings,
        download = state.download,
        voiceDownload = state.voiceDownload,
        voicePicks = state.voicePicks,
        ttsReady = state.translation.ttsReady,
        installedVoices = state.translation.installedVoices,
        onIntent = onIntent,
    )
}

private val PageFadeEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

private fun pageFade(): ContentTransform =
    fadeIn(tween(150, easing = PageFadeEasing)) togetherWith fadeOut(tween(150, easing = PageFadeEasing))

private fun pageNone(): ContentTransform = EnterTransition.None togetherWith ExitTransition.None
