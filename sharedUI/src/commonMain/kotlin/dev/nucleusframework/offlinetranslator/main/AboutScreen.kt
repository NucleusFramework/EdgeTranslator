package dev.nucleusframework.offlinetranslator.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.skydoves.compose.stability.runtime.TraceRecomposition
import dev.nucleusframework.offlinetranslator.platform.Platform
import dev.nucleusframework.offlinetranslator.ui.FilledPill
import dev.nucleusframework.offlinetranslator.ui.SectionLabel
import offlinetranslator.sharedui.generated.resources.Res
import offlinetranslator.sharedui.generated.resources.about_author
import offlinetranslator.sharedui.generated.resources.about_libraries
import offlinetranslator.sharedui.generated.resources.about_license
import offlinetranslator.sharedui.generated.resources.about_version
import offlinetranslator.sharedui.generated.resources.action_ok
import offlinetranslator.sharedui.generated.resources.app_name
import org.jetbrains.compose.resources.stringResource

private const val AUTHOR = "Elie Gambache"
private const val AUTHOR_URL = "https://eliegambache.kdroidfilter.com"
private const val LICENSE_NAME = "GNU Lesser General Public License v3.0"

@TraceRecomposition(tag = "about")
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val libraries by produceLibraries {
        Res.readBytes("files/aboutlibraries.json").decodeToString()
    }
    var showLicense by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.widthIn(max = 920.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
            licenseDialogConfirmText = stringResource(Res.string.action_ok),
            header = {
                item {
                    AboutHeader(onLicenseClick = { showLicense = true })
                }
            },
        )
    }

    if (showLicense) {
        LicenseSheet(onDismiss = { showLicense = false })
    }
}

@Composable
private fun AboutHeader(onLicenseClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text(
            stringResource(Res.string.app_name),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = c.onSurface,
        )
        val uriHandler = LocalUriHandler.current
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainer),
        ) {
            InfoRow(stringResource(Res.string.about_author), AUTHOR, onClick = { uriHandler.openUri(AUTHOR_URL) })
            HorizontalDivider(color = c.surfaceContainerHighest)
            InfoRow(stringResource(Res.string.about_version), Platform.appVersion.ifEmpty { "1.0.0" })
            HorizontalDivider(color = c.surfaceContainerHighest)
            InfoRow(stringResource(Res.string.about_license), LICENSE_NAME, onClick = onLicenseClick)
        }
        SectionLabel(stringResource(Res.string.about_libraries))
    }
}

@Composable
private fun InfoRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    val base = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Column(base.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(title, fontSize = 15.sp, color = c.onSurface)
        Text(value, fontSize = 13.sp, color = if (onClick != null) c.primary else c.onSurfaceVariant)
    }
}

@Composable
private fun LicenseSheet(onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { text = Res.readBytes("files/lgpl-3.0.txt").decodeToString() }
    Box(
        Modifier.fillMaxSize()
            .background(c.scrim.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = c.surface,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.pointerInput(Unit) { detectTapGestures {} },
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(24.dp).heightIn(max = 560.dp)) {
                Text(LICENSE_NAME, fontSize = 20.sp, color = c.onSurface)
                Spacer(Modifier.height(16.dp))
                Text(
                    text,
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    color = c.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FilledPill(stringResource(Res.string.action_ok), onClick = onDismiss)
                }
            }
        }
    }
}
