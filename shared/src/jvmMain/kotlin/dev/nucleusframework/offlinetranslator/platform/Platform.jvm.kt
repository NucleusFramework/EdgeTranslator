package dev.nucleusframework.offlinetranslator.platform

import dev.nucleusframework.core.runtime.NucleusApp
import dev.nucleusframework.systeminfo.SystemInfo
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.source
import kotlinx.coroutines.ensureActive
import kotlinx.io.buffered
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

private const val FILEKIT_APP_ID = "EdgeTranslator"

internal fun ensureFileKit() {
    FileKit.init(appId = FILEKIT_APP_ID)
}

internal actual object Platform {
    actual val osLabel: String
        get() {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            return when {
                os.contains("mac") -> "macOS"
                os.contains("win") -> "Windows"
                else -> "Linux"
            }
        }

    actual val appVersion: String
        get() = NucleusApp.version.orEmpty()

    actual fun cpuCount(): Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    actual fun totalRamBytes(): Long = runCatching {
        SystemInfo.memoryInfo()?.totalMemory ?: 0L
    }.getOrDefault(0L)

    actual fun appDir(): String {
        ensureFileKit()
        return filekitFilesDir()
    }

    actual fun cacheDir(): String {
        ensureFileKit()
        return filekitCacheDir()
    }

    actual fun databasesDir(): String {
        ensureFileKit()
        return filekitDatabasesDir()
    }

    actual fun readText(path: String): String? = filekitReadText(path)

    actual fun writeText(path: String, content: String) = filekitWriteText(path, content)

    actual fun delete(path: String): Boolean = filekitDelete(path)

    actual fun deleteRecursively(path: String): Boolean = filekitDeleteRecursively(path)

    actual fun exists(path: String): Boolean = filekitExists(path)

    actual fun fileSize(path: String): Long = filekitSize(path)

    actual fun mkdir(path: String) = filekitMkdir(path)

    actual fun freeSpace(path: String): Long = File(path).usableSpace

    actual fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    actual suspend fun sha256(path: String): String? = filekitSha256(path)

    actual fun rename(from: String, to: String): Boolean = filekitRename(from, to)

    actual fun writeAppend(path: String, bytes: ByteArray, offset: Int, length: Int) = filekitWriteAppend(path, bytes, offset, length)

    actual fun truncate(path: String) = filekitTruncate(path)

    actual fun now(): Long = System.currentTimeMillis()
    actual fun applyLocale(tag: String) {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag(tag))
    }

    // Read once at object init, which happens before the first applyLocale() call.
    private val bootLanguage: String = java.util.Locale.getDefault().language

    actual fun systemLanguage(): String = bootLanguage
}

internal suspend fun filekitSha256(path: String): String? {
    val file = PlatformFile(path)
    if (!file.isRegularFile()) return null
    val source = try {
        file.source().buffered()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        return null
    }
    return try {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(8192)
        var sinceCheck = 0
        while (!source.exhausted()) {
            val n = source.readAtMostTo(buf)
            if (n <= 0) break
            digest.update(buf, 0, n)
            sinceCheck += n
            if (sinceCheck >= 1_048_576) {
                coroutineContext.ensureActive()
                sinceCheck = 0
            }
        }
        digest.digest().joinToString("") { b -> "%02x".format(b) }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    } finally {
        source.close()
    }
}
