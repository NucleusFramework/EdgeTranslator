package dev.nucleusframework.offlinetranslator.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ponytail: Android could seed from Material You (dynamicLightColorScheme) — not wired.
// Fall back to the brand seed for now; add it when the Android app needs dynamic color.
@Composable
actual fun rememberSystemAccentColor(): Color? = null
