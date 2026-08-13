package dev.nucleusframework.offlinetranslator.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.io.File

// Same AWT transfer path as Nucleus tao-demo / compose-demo:
// DragAndDropEvent.awtTransferable + javaFileListFlavor / stringFlavor.

private val URI_LIST_FLAVOR = DataFlavor("text/uri-list;class=java.lang.String")

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun readDropPayload(event: DragAndDropEvent): DropPayload {
    val transferable = runCatching { event.awtTransferable }.getOrNull() ?: return DropPayload()
    val text = flavorString(transferable, DataFlavor.stringFlavor)
    val uriList = flavorString(transferable, URI_LIST_FLAVOR)
    val files = if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        @Suppress("UNCHECKED_CAST")
        runCatching { transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File> }.getOrNull()
    } else {
        null
    }
    val paths = buildList {
        files.orEmpty().forEach { add(it.absolutePath) }
        addAll(pathsFromText(uriList))
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

private fun flavorString(
    transferable: java.awt.datatransfer.Transferable,
    flavor: DataFlavor,
): String? {
    if (!transferable.isDataFlavorSupported(flavor)) return null
    return runCatching { transferable.getTransferData(flavor) as? String }.getOrNull()
}
