package dev.nucleusframework.offlinetranslator.platform

import androidx.compose.runtime.Composable

/** Android text fields already get the IME's own spellcheck. */
@Composable
internal actual fun Spellchecked(text: String, onTextChange: (String) -> Unit, languageTag: String?, content: @Composable () -> Unit) {
    content()
}
