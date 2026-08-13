package dev.nucleusframework.offlinetranslator

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.nucleusframework.offlinetranslator.app.AppViewModel
import dev.nucleusframework.offlinetranslator.app.RootScreen
import dev.nucleusframework.offlinetranslator.di.AppGraph
import dev.nucleusframework.offlinetranslator.di.createAppGraph
import dev.nucleusframework.offlinetranslator.domain.ThemeMode
import dev.nucleusframework.offlinetranslator.platform.Platform
import dev.nucleusframework.offlinetranslator.theme.EdgeTheme

@Preview
@Composable
fun App(
    graph: AppGraph? = null,
    provided: AppViewModel? = null,
    onThemeChanged: @Composable (isDark: Boolean) -> Unit = {},
    /** Same deal as [onThemeChanged]: window chrome lives outside this composable and needs it too. */
    onLayoutDirectionChanged: @Composable (isRtl: Boolean) -> Unit = {},
    onQuit: () -> Unit = {},
    forceOnboarding: Boolean = false,
) {
    val appGraph = graph ?: remember { createAppGraph() }
    val vm = provided ?: viewModel { appGraph.viewModelFactory.create(onQuit, forceOnboarding) }
    val state by vm.state.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (state.data.settings.theme) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    onThemeChanged(isDark)
    val ui = state.data.settings.uiLanguage
    remember(ui) { Platform.applyLocale(ui.code); ui }
    onLayoutDirectionChanged(ui.rtl)
    val dir = if (ui.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    EdgeTheme(isDark) {
        CompositionLocalProvider(LocalLayoutDirection provides dir) {
            RootScreen(state = state, backStack = vm.backStack, onIntent = vm::onIntent)
        }
    }
}
