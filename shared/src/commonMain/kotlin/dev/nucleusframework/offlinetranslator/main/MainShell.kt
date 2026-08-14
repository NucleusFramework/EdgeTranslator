package dev.nucleusframework.offlinetranslator.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.compose.stability.runtime.TraceRecomposition
import dev.nucleusframework.offlinetranslator.app.AppIntent
import dev.nucleusframework.offlinetranslator.app.AppKey
import dev.nucleusframework.offlinetranslator.app.MainDestinations
import dev.nucleusframework.offlinetranslator.app.label
import dev.nucleusframework.offlinetranslator.domain.LlmBackend
import dev.nucleusframework.offlinetranslator.domain.LlmModel
import dev.nucleusframework.offlinetranslator.domain.UiLanguage
import dev.nucleusframework.offlinetranslator.engine.GemmaModels
import dev.nucleusframework.offlinetranslator.engine.LlmAccelerator
import dev.nucleusframework.offlinetranslator.engine.LlmRuntime
import dev.nucleusframework.offlinetranslator.platform.Platform
import offlinetranslator.shared.generated.resources.Res
import offlinetranslator.shared.generated.resources.app_name
import offlinetranslator.shared.generated.resources.nav_backend
import offlinetranslator.shared.generated.resources.offline
import org.jetbrains.compose.resources.stringResource

/**
 * The running app (design section B): offline strip + extended navigation rail,
 * with the content area supplied by Nav3.
 */
@TraceRecomposition(tag = "shell")
@Composable
fun MainShell(
    destination: AppKey,
    uiLanguage: UiLanguage,
    offline: Boolean,
    modelId: LlmModel,
    backend: LlmBackend,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // ponytail: compose-resources only re-reads a string when its call site recomposes, and the
    // chrome's args don't change with the language — so key the chrome on it. Overriding
    // LocalComposeEnvironment would be the general fix, but it's internal to the library.
    // Not applied to content(): that would reset each screen's scroll position.
    Column(modifier.fillMaxSize()) {
        // On desktop the brand + screen chrome live in the window title bar.
        if (!LocalHostHasTitleBar.current) key(uiLanguage) { OfflineBar(offline) }
        Row(Modifier.fillMaxSize()) {
            key(uiLanguage) { NavRail(destination, modelId, backend, onIntent) }
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        }
    }
}

@Composable
private fun OfflineBar(offline: Boolean) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(c.surfaceContainer).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.app_name),
            Modifier.weight(1f),
            color = c.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        LlmAcceleratorBadge(Modifier.padding(end = 12.dp))
        if (offline) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.WifiOff, null, Modifier.size(16.dp), tint = c.onSurfaceVariant)
                Text(stringResource(Res.string.offline), color = c.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun NavRail(selected: AppKey, modelId: LlmModel, backend: LlmBackend, onIntent: (AppIntent) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.width(220.dp).fillMaxHeight().background(c.surfaceContainer)
            // Desktop: the rail's own surface moves the window, macOS-style.
            // The items are clickable, so they claim their presses and stay out.
            .then(LocalWindowDrag.current)
            .padding(horizontal = 12.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The app identity lives in the window chrome, not here.
        MainDestinations.filter { it != AppKey.Settings && it != AppKey.About }.forEach { dest ->
            NavRailItem(dest, dest == selected) { onIntent(AppIntent.Navigate(dest)) }
        }

        Spacer(Modifier.weight(1f))
        NavRailItem(AppKey.Settings, AppKey.Settings == selected) { onIntent(AppIntent.Navigate(AppKey.Settings)) }
        NavRailItem(AppKey.About, AppKey.About == selected) { onIntent(AppIntent.Navigate(AppKey.About)) }
        HorizontalDivider(color = c.outlineVariant)
        Column(
            Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LocalSystemMeters.current?.invoke(Modifier)
            val resolved by LlmRuntime.accelerator.collectAsState()
            Text(
                stringResource(
                    Res.string.nav_backend,
                    if (resolved == LlmAccelerator.None) backendLabel(backend) else acceleratorLabel(),
                ),
                color = c.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Text(
                GemmaModels.of(modelId).name,
                color = c.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            val version = Platform.appVersion
            if (version.isNotEmpty()) {
                Text(version, color = c.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun NavRailItem(dest: AppKey, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val easing = CubicBezierEasing(0.2833f, 0.99f, 0.31833f, 0.99f)
    val bg by animateColorAsState(
        if (selected) c.primaryContainer else c.surfaceContainer,
        tween(280, easing = easing),
        label = "nav-bg",
    )
    val fg by animateColorAsState(
        if (selected) c.onPrimaryContainer else c.onSurfaceVariant,
        tween(280, easing = easing),
        label = "nav-fg",
    )
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(dest.icon(), null, tint = fg)
        Text(dest.label(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

private fun AppKey.icon(): ImageVector = when (this) {
    AppKey.Translate -> Icons.Outlined.Translate
    AppKey.Proofread -> Icons.Outlined.Spellcheck
    AppKey.History -> Icons.Outlined.Star
    AppKey.Settings -> Icons.Outlined.Settings
    AppKey.About -> Icons.Outlined.Info
    else -> Icons.Outlined.Translate
}
