package dev.nucleusframework.offlinetranslator.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The live OS accent color, or null when the platform/backend exposes none.
 * Recomposes when the user changes their system accent.
 */
@Composable
expect fun rememberSystemAccentColor(): Color?
