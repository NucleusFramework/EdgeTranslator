package dev.nucleusframework.offlinetranslator.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.io.File

// Same AWT transfer path as Nucleus tao-demo / compose-demo:
// DragAndDropEvent.awtTransferable + javaFileListFlavor / stringFlavor.

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun readDropPayload(event: DragAndDropEvent): DropPayload {
    val transferable = runCatching { event.awtTransferable }.getOrNull() ?: return DropPayload()
    val text = if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        runCatching { transferable.getTransferData(DataFlavor.stringFlavor) as? String }.getOrNull()
    } else {
        null
    }
    val paths = if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        @Suppress("UNCHECKED_CAST")
        val files = runCatching { transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File> }.getOrNull()
        files.orEmpty().map { it.absolutePath }
    } else {
        emptyList()
    }
    return when (val choice = resolveDrop(text, paths)) {
        is DropChoice.ImagePath -> {
            val bytes = runCatching { File(choice.path).readBytes() }.getOrNull()
            if (bytes == null || bytes.isEmpty()) DropPayload(unsupported = true) else DropPayload(image = bytes)
        }
        is DropChoice.TextPath -> {
            val body = Platform.readText(choice.path)
            if (body.isNullOrBlank()) DropPayload(unsupported = true) else DropPayload(text = body)
        }
        is DropChoice.Clipboard -> DropPayload(text = choice.text)
        DropChoice.Unsupported -> DropPayload(unsupported = true)
        DropChoice.Empty -> DropPayload()
    }
}
