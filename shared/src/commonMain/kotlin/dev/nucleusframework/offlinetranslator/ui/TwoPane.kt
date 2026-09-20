package dev.nucleusframework.offlinetranslator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Two ~280.dp panels + swap + padding no longer fit comfortably below this. */
private val TwoPaneMinWidth = 680.dp

/** Side-by-side panes; stacks top/bottom when the content width is too narrow. */
@Composable
fun TwoPane(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    between: @Composable ((stacked: Boolean) -> Unit)? = null,
) {
    // Movable so the panes keep their state (text, cursor, scroll) when a resize
    // moves them between the Column and the Row.
    val currentFirst by rememberUpdatedState(first)
    val currentSecond by rememberUpdatedState(second)
    val firstPane = remember { movableContentOf<Modifier> { currentFirst(it) } }
    val secondPane = remember { movableContentOf<Modifier> { currentSecond(it) } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val stacked = maxWidth < TwoPaneMinWidth
        val content = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)
        if (stacked) {
            Column(content, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                firstPane(Modifier.weight(1f).fillMaxWidth())
                if (between != null) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { between(true) }
                }
                secondPane(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(
                content,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                firstPane(Modifier.weight(1f).fillMaxHeight())
                between?.invoke(false)
                secondPane(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}
