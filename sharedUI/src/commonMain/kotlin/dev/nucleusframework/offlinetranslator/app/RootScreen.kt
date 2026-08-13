package dev.nucleusframework.offlinetranslator.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dev.nucleusframework.offlinetranslator.install.InstallScreen
import dev.nucleusframework.offlinetranslator.main.HistoryScreen
import dev.nucleusframework.offlinetranslator.main.MainShell
import dev.nucleusframework.offlinetranslator.main.SettingsScreen
import dev.nucleusframework.offlinetranslator.translation.ProofreadContent
import dev.nucleusframework.offlinetranslator.translation.TranslationContent
import dev.nucleusframework.offlinetranslator.ui.AppDialogHost
import dev.nucleusframework.offlinetranslator.ui.MessageBar

@Composable
fun RootScreen(state: AppState, backStack: NavBackStack<AppKey>, onIntent: (AppIntent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        val current = backStack.last()
        if (current.isMain()) {
            MainShell(current, state, onIntent) {
                AppNavDisplay(backStack, state, onIntent)
            }
        } else {
            AppNavDisplay(backStack, state, onIntent)
        }
        MessageBar(
            message = state.message,
            onDismiss = { onIntent(AppIntent.DismissMessage) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        AppDialogHost(state, onIntent)
    }
}

@Composable
private fun AppNavDisplay(backStack: NavBackStack<AppKey>, state: AppState, onIntent: (AppIntent) -> Unit) {
    NavDisplay(
        backStack = backStack,
        onBack = {
            when (backStack.last()) {
                AppKey.Download, AppKey.Voices -> onIntent(AppIntent.InstallBack)
                else -> Unit
            }
        },
        entryProvider = entryProvider {
            entry<AppKey.Welcome> { InstallScreen(InstallStep.Welcome, state, onIntent) }
            entry<AppKey.Download> { InstallScreen(InstallStep.Download, state, onIntent) }
            entry<AppKey.Voices> { InstallScreen(InstallStep.Voices, state, onIntent) }
            entry<AppKey.Translate> { TranslationContent(state, onIntent) }
            entry<AppKey.Proofread> { ProofreadContent(state, onIntent) }
            entry<AppKey.History> { HistoryScreen(state, onIntent) }
            entry<AppKey.Settings> { SettingsScreen(state, onIntent) }
        },
    )
}
