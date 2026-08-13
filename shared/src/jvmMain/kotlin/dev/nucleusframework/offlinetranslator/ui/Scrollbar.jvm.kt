package dev.nucleusframework.offlinetranslator.ui

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun VerticalListScrollbar(state: LazyListState, modifier: Modifier) {
    ThemedScrollbar(rememberScrollbarAdapter(state), modifier)
}

@Composable
internal actual fun VerticalContentScrollbar(state: ScrollState, modifier: Modifier) {
    ThemedScrollbar(rememberScrollbarAdapter(state), modifier)
}

@Composable
private fun ThemedScrollbar(adapter: ScrollbarAdapter, modifier: Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    VerticalScrollbar(
        adapter = adapter,
        modifier = modifier,
        style = LocalScrollbarStyle.current.copy(
            unhoverColor = onSurface.copy(alpha = 0.18f),
            hoverColor = onSurface.copy(alpha = 0.50f),
        ),
    )
}
