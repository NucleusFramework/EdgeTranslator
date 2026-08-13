package dev.nucleusframework.offlinetranslator.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import offlinetranslator.sharedui.generated.resources.Res
import offlinetranslator.sharedui.generated.resources.app_name
import offlinetranslator.sharedui.generated.resources.ic_github
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val GITHUB_REPO = "https://github.com/kdroidFilter/OfflineTranslator"

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
    Box(
        modifier.size(40.dp).clip(CircleShape).clickable { uriHandler.openUri(GITHUB_REPO) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(Res.drawable.ic_github), "GitHub", Modifier.size(18.dp), tint = c.onSurfaceVariant)
    }
}
