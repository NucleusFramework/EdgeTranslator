package dev.nucleusframework.offlinetranslator.platform

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.source
import kotlinx.coroutines.ensureActive
import kotlinx.io.buffered
import java.io.File
import java.lang.ref.WeakReference
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

@SuppressLint("StaticFieldLeak")
private var appContext: Context? = null
private var activityRef: WeakReference<ComponentActivity>? = null

fun bindAndroidContext(context: Context) {
    appContext = context.applicationContext
    if (context is ComponentActivity) activityRef = WeakReference(context)
}

private fun ctx(): Context = requireNotNull(appContext) { "bindAndroidContext() must be called from AppActivity.onCreate" }

internal fun androidContext(): Context = ctx()

internal fun androidActivity(): ComponentActivity? = activityRef?.get()

internal actual object Platform {
    actual val osLabel: String = "Android"

    actual fun cpuCount(): Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    actual fun appDir(): String = filekitFilesDir()

    actual fun cacheDir(): String = filekitCacheDir()

    actual fun databasesDir(): String = filekitDatabasesDir()

    actual fun readText(path: String): String? = filekitReadText(path)

    actual fun writeText(path: String, content: String) = filekitWriteText(path, content)

    actual fun delete(path: String): Boolean = filekitDelete(path)

    actual fun deleteRecursively(path: String): Boolean = filekitDeleteRecursively(path)

    actual fun exists(path: String): Boolean = filekitExists(path)

    actual fun fileSize(path: String): Long = filekitSize(path)

    actual fun mkdir(path: String) = filekitMkdir(path)

    actual fun freeSpace(path: String): Long = File(path).usableSpace

    actual fun copyToClipboard(text: String) {
        val cm = ctx().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Edge Translator", text))
    }

    actual suspend fun sha256(path: String): String? = filekitSha256(path)

    actual fun rename(from: String, to: String): Boolean = filekitRename(from, to)

    actual fun writeAppend(path: String, bytes: ByteArray, offset: Int, length: Int) = filekitWriteAppend(path, bytes, offset, length)

    actual fun truncate(path: String) = filekitTruncate(path)

    actual fun now(): Long = System.currentTimeMillis()
    actual fun applyLocale(tag: String) {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag(tag))
    }

    // System resources, not Locale.getDefault() — applyLocale() overwrites the latter, and the user
    // can change the device language without the process being killed.
    actual fun systemLanguage(): String = android.content.res.Resources.getSystem().configuration.locales[0].language
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
