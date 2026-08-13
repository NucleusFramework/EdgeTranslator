package dev.nucleusframework.offlinetranslator.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun VerticalListScrollbar(state: LazyListState, modifier: Modifier) = Unit

@Composable
internal actual fun VerticalContentScrollbar(state: ScrollState, modifier: Modifier) = Unit
