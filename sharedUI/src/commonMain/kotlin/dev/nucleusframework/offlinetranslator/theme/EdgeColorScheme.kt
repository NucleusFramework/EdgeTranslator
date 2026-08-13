package dev.nucleusframework.offlinetranslator.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.materialkolor.rememberDynamicColorScheme

// Brand fallback seed (Edge blue), used when the OS exposes no accent color.
private val EdgeSeed = Color(0xFF005AC1)

/**
 * The app's Material 3 scheme: MaterialKolor generated from the live OS accent
 * (Nucleus system-color), brand seed as fallback, dark/light following the OS.
 * Exposed so the desktop window can style its title bar with the same scheme.
 */
@Composable
fun rememberEdgeColorScheme(isDark: Boolean = isSystemInDarkTheme()): ColorScheme {
    val seed = rememberSystemAccentColor() ?: EdgeSeed
    return rememberDynamicColorScheme(seedColor = seed, isDark = isDark)
}

@Composable
fun EdgeTheme(modifier: Modifier = Modifier, isDark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = rememberEdgeColorScheme(isDark)) {
        Surface(modifier = modifier, content = content)
    }
}
