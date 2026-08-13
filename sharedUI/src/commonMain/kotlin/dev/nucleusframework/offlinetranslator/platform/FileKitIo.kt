package dev.nucleusframework.offlinetranslator.platform

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import io.github.vinceglb.filekit.toKotlinxIoPath
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

internal fun filekitFilesDir(): String = FileKit.filesDir.path

internal fun filekitCacheDir(): String = FileKit.cacheDir.path

internal fun filekitDatabasesDir(): String = FileKit.databasesDir.path

internal fun filekitReadText(path: String): String? = try {
    val file = PlatformFile(path)
    if (file.isRegularFile()) file.source().buffered().use { it.readString() } else null
} catch (_: Exception) {
    null
}

internal fun filekitWriteText(path: String, content: String) {
    val file = PlatformFile(path)
    file.parent()?.createDirectories()
    file.sink().buffered().use { it.writeString(content) }
}

internal fun filekitDelete(path: String): Boolean = try {
    val file = PlatformFile(path)
    if (!file.exists()) true
    else {
        SystemFileSystem.delete(file.toKotlinxIoPath(), mustExist = false)
        true
    }
} catch (_: Exception) {
    false
}

internal fun filekitDeleteRecursively(path: String): Boolean = try {
    if (path.isBlank()) false
    else {
        deletePathRecursively(PlatformFile(path).toKotlinxIoPath())
        true
    }
} catch (_: Exception) {
    false
}

private fun deletePathRecursively(path: kotlinx.io.files.Path) {
    if (!SystemFileSystem.exists(path)) return
    if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
        SystemFileSystem.list(path).forEach(::deletePathRecursively)
    }
    SystemFileSystem.delete(path, mustExist = false)
}

internal fun filekitExists(path: String): Boolean = try {
    path.isNotBlank() && PlatformFile(path).exists()
} catch (_: Exception) {
    false
}

internal fun filekitSize(path: String): Long = try {
    val file = PlatformFile(path)
    if (file.exists() && file.isRegularFile()) file.size().coerceAtLeast(0L) else 0L
} catch (_: Exception) {
    0L
}

internal fun filekitMkdir(path: String) {
    PlatformFile(path).createDirectories()
}

internal fun filekitRename(from: String, to: String): Boolean = try {
    val dest = PlatformFile(to)
    dest.parent()?.createDirectories()
    SystemFileSystem.atomicMove(PlatformFile(from).toKotlinxIoPath(), dest.toKotlinxIoPath())
    true
} catch (_: Exception) {
    false
}

internal fun filekitWriteAppend(path: String, bytes: ByteArray, offset: Int, length: Int) {
    val file = PlatformFile(path)
    file.parent()?.createDirectories()
    file.sink(append = true).buffered().use { it.write(bytes, startIndex = offset, endIndex = offset + length) }
}

internal fun filekitTruncate(path: String) {
    val file = PlatformFile(path)
    file.parent()?.createDirectories()
    file.sink(append = false).use { }
}
