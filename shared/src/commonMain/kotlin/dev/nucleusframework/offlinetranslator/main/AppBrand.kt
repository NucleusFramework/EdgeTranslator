package dev.nucleusframework.offlinetranslator.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import offlinetranslator.shared.generated.resources.Res
import offlinetranslator.shared.generated.resources.app_name
import offlinetranslator.shared.generated.resources.ic_github
import offlinetranslator.shared.generated.resources.ic_window
import offlinetranslator.shared.generated.resources.tooltip_github
import offlinetranslator.shared.generated.resources.tooltip_sponsor
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val GITHUB_REPO = "https://github.com/kdroidFilter/OfflineTranslator"
private const val SPONSOR_URL = "https://ko-fi.com/lomityaesh"

/**
 * True when the platform host shows the app identity in its own window chrome
 * (desktop, via the Nucleus WindowScaffold title bar). MainShell then skips its
 * in-body OfflineBar.
 */
val LocalHostHasTitleBar = staticCompositionLocalOf { false }

/**
 * Window-drag surface, published by the desktop host (Nucleus
 * `Modifier.windowDragArea`). Empty on platforms whose windows the app does not
 * move itself, so shared UI can apply it unconditionally.
 */
val LocalWindowDrag = staticCompositionLocalOf<Modifier> { Modifier }

/**
 * Live CPU / RAM / GPU meters, published by the desktop host — they read the
 * host machine through Nucleus system-info, which only exists there. Null
 * elsewhere, so the navigation rail simply omits the block.
 */
val LocalSystemMeters = staticCompositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }

/** Launcher mark. Used as `MaterialDecoratedWindow.icon` on Windows. */
@Composable
fun windowIconPainter(): Painter = painterResource(Res.drawable.ic_window)

/** App identity: translate glyph + name. Used in the desktop window chrome. */
@Composable
fun BrandLabel(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Translate, null, Modifier.size(18.dp), tint = c.primary)
        Text(stringResource(Res.string.app_name), color = c.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Opens the project repository. Used at the trailing edge of the desktop title bar. */
@Composable
fun GitHubButton(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val c = MaterialTheme.colorScheme
    val label = stringResource(Res.string.tooltip_github)
    TitleBarIconButton(label, { uriHandler.openUri(GITHUB_REPO) }, modifier) {
        Icon(painterResource(Res.drawable.ic_github), label, Modifier.size(18.dp), tint = c.onSurfaceVariant)
    }
}

/** Opens Ko-fi. Used next to [GitHubButton] on the desktop title bar. */
@Composable
fun SponsorButton(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val c = MaterialTheme.colorScheme
    val label = stringResource(Res.string.tooltip_sponsor)
    TitleBarIconButton(label, { uriHandler.openUri(SPONSOR_URL) }, modifier) {
        Icon(Icons.Filled.Favorite, label, Modifier.size(18.dp), tint = c.onSurfaceVariant)
    }
}

@Composable
private fun TitleBarIconButton(tooltip: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            icon()
        }
    }
}
