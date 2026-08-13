package dev.nucleusframework.offlinetranslator.platform

import androidx.compose.ui.draganddrop.DragAndDropEvent

internal actual fun readDropPayload(event: DragAndDropEvent): DropPayload = DropPayload()
