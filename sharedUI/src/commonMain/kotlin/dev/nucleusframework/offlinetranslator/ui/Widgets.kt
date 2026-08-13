package dev.nucleusframework.offlinetranslator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Uppercase, letter-spaced section caption used throughout the design. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), modifier, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
}

/** Filled (primary) pill button. */
@Composable
fun FilledPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (enabled) c.primary else c.surfaceContainerHighest,
        contentColor = if (enabled) c.onPrimary else c.onSurfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.height(40.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(18.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Outlined (text-on-surface) pill button. */
@Composable
fun OutlinedPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val fg = if (enabled) c.primary else c.onSurfaceVariant.copy(alpha = 0.55f)
    val stroke = if (enabled) c.outline else c.outlineVariant
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, stroke, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = fg)
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

/** Small 32dp filter/choice chip (rounded 8). */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val base = modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onClick)
    val shaped = if (selected) base.background(c.primaryContainer) else base.border(1.dp, c.outlineVariant, RoundedCornerShape(8.dp))
    val fg = when {
        !enabled -> c.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> c.onPrimaryContainer
        else -> c.onSurfaceVariant
    }
    Row(
        shaped.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, fontSize = 14.sp, color = fg, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

/** Circular icon-only button. */
@Composable
fun IconGlyphButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        contentColor = tint,
        shape = CircleShape,
        modifier = modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription, Modifier.size(22.dp)) }
    }
}
