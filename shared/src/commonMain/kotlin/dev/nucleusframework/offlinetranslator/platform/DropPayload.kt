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
    val allPaths = paths + pathsFromText(clipboardText)
    val image = allPaths.firstOrNull { it.fileExtension() in IMAGE_EXT }
    if (image != null) return DropChoice.ImagePath(image)
    val textFile = allPaths.firstOrNull { it.fileExtension() in TEXT_EXT }
    if (textFile != null) return DropChoice.TextPath(textFile)
    // File list is the payload. The string flavor is usually just the path —
    // don't paste that into the source field.
    if (allPaths.isNotEmpty()) return DropChoice.Unsupported
    val clip = clipboardText?.trim().orEmpty()
    if (clip.isNotEmpty()) return DropChoice.Clipboard(clip)
    return DropChoice.Empty
}

/** File URIs and absolute paths carried by `text/uri-list` / string flavor on Linux. */
internal fun pathsFromText(text: String?): List<String> {
    if (text.isNullOrBlank()) return emptyList()
    return text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull(::pathFromDropLine)
        .toList()
}

private fun pathFromDropLine(line: String): String? {
    val uri = line.trim()
    if (uri.startsWith("file:", ignoreCase = true)) {
        return runCatching { java.net.URI(uri).path }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
    if (uri.startsWith("/")) return uri
    if (uri.length >= 3 && uri[1] == ':' && uri[0].isLetter()) return uri
    return null
}

internal fun String.fileExtension(): String = substringAfterLast('.', missingDelimiterValue = "").lowercase()

internal expect fun readDropPayload(event: DragAndDropEvent): DropPayload
