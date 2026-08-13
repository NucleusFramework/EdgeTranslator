package dev.nucleusframework.offlinetranslator.app

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import offlinetranslator.sharedui.generated.resources.Res
import offlinetranslator.sharedui.generated.resources.nav_history
import offlinetranslator.sharedui.generated.resources.nav_proofread
import offlinetranslator.sharedui.generated.resources.nav_settings
import offlinetranslator.sharedui.generated.resources.nav_translate
import org.jetbrains.compose.resources.stringResource

sealed interface AppKey : NavKey {
    data object Welcome : AppKey
    data object Download : AppKey
    data object Voices : AppKey
    data object Translate : AppKey
    data object Proofread : AppKey
    data object History : AppKey
    data object Settings : AppKey
}

val MainDestinations: List<AppKey> = listOf(
    AppKey.Translate,
    AppKey.Proofread,
    AppKey.History,
    AppKey.Settings,
)

fun AppKey.isMain(): Boolean = this in MainDestinations

@Composable
fun AppKey.label(): String = when (this) {
    AppKey.Translate -> stringResource(Res.string.nav_translate)
    AppKey.Proofread -> stringResource(Res.string.nav_proofread)
    AppKey.History -> stringResource(Res.string.nav_history)
    AppKey.Settings -> stringResource(Res.string.nav_settings)
    else -> ""
}

fun InstallStep.toKey(): AppKey = when (this) {
    InstallStep.Welcome -> AppKey.Welcome
    InstallStep.Download -> AppKey.Download
    InstallStep.Voices -> AppKey.Voices
}

fun installStack(step: InstallStep): List<AppKey> = InstallStep.entries.take(step.ordinal + 1).map { it.toKey() }
