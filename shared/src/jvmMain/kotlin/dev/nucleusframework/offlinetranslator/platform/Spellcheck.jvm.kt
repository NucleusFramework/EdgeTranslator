package dev.nucleusframework.offlinetranslator.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.nucleusframework.application.spellcheck.SpellcheckContextMenu
import java.util.Locale

@Composable
internal actual fun Spellchecked(text: String, onTextChange: (String) -> Unit, languageTag: String?, content: @Composable () -> Unit) {
    // Null locale falls back to SpellChecker.locale. No engine or no matching
    // dictionary makes the wrapper transparent.
    val locale = remember(languageTag) { languageTag?.let(Locale::forLanguageTag) }
    SpellcheckContextMenu(
        text = text,
        onTextChange = onTextChange,
        locale = locale,
        content = content,
    )
}
