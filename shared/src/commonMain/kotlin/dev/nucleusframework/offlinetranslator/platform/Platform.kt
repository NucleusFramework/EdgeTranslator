package dev.nucleusframework.offlinetranslator.platform

import dev.nucleusframework.offlinetranslator.domain.UiLanguage

internal expect object Platform {
    val osLabel: String
    val appVersion: String
    fun cpuCount(): Int
    fun totalRamBytes(): Long
    fun appDir(): String
    fun cacheDir(): String
    fun databasesDir(): String
    /** Shared LiteRT-LM registry on desktop; app sandbox on Android. */
    fun modelsDir(): String
    fun readText(path: String): String?
    fun writeText(path: String, content: String)
    fun delete(path: String): Boolean
    fun deleteRecursively(path: String): Boolean
    fun exists(path: String): Boolean
    fun fileSize(path: String): Long
    fun mkdir(path: String)
    fun freeSpace(path: String): Long
    fun copyToClipboard(text: String)
    suspend fun sha256(path: String): String?
    fun rename(from: String, to: String): Boolean
    fun writeAppend(path: String, bytes: ByteArray, offset: Int, length: Int)
    fun truncate(path: String)
    fun now(): Long
    fun applyLocale(tag: String)

    /**
     * The OS language, captured at startup — [applyLocale] overwrites the default locale, so this
     * has to be read before the app ever applies its own.
     */
    fun systemLanguage(): String
}

internal fun systemUiLanguage(): UiLanguage = UiLanguage.fromCode(Platform.systemLanguage())

internal fun pathSeparator(path: String): Char =
    if (path.contains('\\') && !path.contains('/')) '\\' else '/'

internal fun joinPath(dir: String, name: String): String {
    val sep = pathSeparator(dir)
    return if (dir.endsWith('/') || dir.endsWith('\\')) dir + name else dir + sep + name
}

internal fun parentPath(path: String): String {
    val sep = pathSeparator(path)
    val i = path.lastIndexOf(sep)
    return if (i <= 0) path else path.substring(0, i)
}

/** LiteRT-LM CLI registry: `{home}/.litert-lm/models` on Windows, Linux, and macOS. */
internal fun litertLmModelsDir(home: String): String =
    joinPath(joinPath(home, ".litert-lm"), "models")
