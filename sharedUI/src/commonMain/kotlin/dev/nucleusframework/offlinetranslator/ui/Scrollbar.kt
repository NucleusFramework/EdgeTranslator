package dev.nucleusframework.offlinetranslator.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun VerticalListScrollbar(state: LazyListState, modifier: Modifier = Modifier)

@Composable
internal expect fun VerticalContentScrollbar(state: ScrollState, modifier: Modifier = Modifier)
