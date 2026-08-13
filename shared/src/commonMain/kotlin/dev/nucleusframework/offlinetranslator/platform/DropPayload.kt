package dev.nucleusframework.offlinetranslator.platform

import androidx.compose.ui.draganddrop.DragAndDropEvent

internal data class DropPayload(
    val text: String? = null,
    val image: ByteArray? = null,
    val unsupported: Boolean = false,
)

internal sealed interface DropChoice {
    data class ImagePath(val path: String) : DropChoice
    data class TextPath(val path: String) : DropChoice
    data class Clipboard(val text: String) : DropChoice
    data object Unsupported : DropChoice
    data object Empty : DropChoice
}

private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
private val TEXT_EXT = setOf("txt", "md", "csv", "json", "log", "html", "htm", "xml")

internal fun resolveDrop(clipboardText: String?, paths: List<String>): DropChoice {
    val image = paths.firstOrNull { it.fileExtension() in IMAGE_EXT }
    if (image != null) return DropChoice.ImagePath(image)
    val textFile = paths.firstOrNull { it.fileExtension() in TEXT_EXT }
    if (textFile != null) return DropChoice.TextPath(textFile)
    // File list is the payload. The string flavor is usually just the path —
    // don't paste that into the source field.
    if (paths.isNotEmpty()) return DropChoice.Unsupported
    val clip = clipboardText?.trim().orEmpty()
    if (clip.isNotEmpty()) return DropChoice.Clipboard(clip)
    return DropChoice.Empty
}

internal fun String.fileExtension(): String = substringAfterLast('.', missingDelimiterValue = "").lowercase()

internal expect fun readDropPayload(event: DragAndDropEvent): DropPayload
