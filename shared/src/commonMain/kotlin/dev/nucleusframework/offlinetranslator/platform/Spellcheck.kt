package dev.nucleusframework.offlinetranslator.platform

import androidx.compose.runtime.Composable

/**
 * Wraps an editable text field with the OS spellchecker: suggestions in the
 * context menu and a red wavy underline under misspelled words.
 *
 * @param languageTag BCP 47 tag of the text being typed, or `null` to follow
 *   the process locale. [dev.nucleusframework.offlinetranslator.domain.AUTO_LANG]
 *   has no dictionary, so callers pass `null` for it.
 */
@Composable
internal expect fun Spellchecked(text: String, onTextChange: (String) -> Unit, languageTag: String?, content: @Composable () -> Unit)
