package dev.nucleusframework.offlinetranslator.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.nucleusframework.systemcolor.systemAccentColor

// Nucleus reads the live OS accent via JNI (macOS/Windows/Linux).
// Returns null when unsupported or when the user picked macOS "multicolor".
@Composable
actual fun rememberSystemAccentColor(): Color? = systemAccentColor()
