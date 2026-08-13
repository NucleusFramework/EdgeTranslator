package dev.nucleusframework.offlinetranslator.engine

import androidx.compose.runtime.Immutable
import dev.nucleusframework.offlinetranslator.domain.LlmModel
import dev.nucleusframework.offlinetranslator.domain.MODEL_BYTES
import dev.nucleusframework.offlinetranslator.domain.ModelInfo
import dev.nucleusframework.offlinetranslator.platform.Platform
import dev.nucleusframework.offlinetranslator.platform.joinPath

/**
 * Gemma 4 IT packaged for LiteRT-LM, same artifacts as Google AI Edge Gallery.
 * Fast: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
 * Precise: https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
 */
@Immutable
data class CatalogModel(
    val id: LlmModel,
    val name: String,
    val fileName: String,
    val repo: String,
    val bytes: Long,
    val sha256: String,
    val quantization: String = GemmaModel.QUANTIZATION,
) {
    val url: String get() = "https://huggingface.co/$repo/resolve/main/$fileName"

    fun destPath(): String = joinPath(GemmaModels.dir(), fileName)

    fun partialPath(): String = destPath() + ".partial"

    fun isOnDisk(): Boolean = Platform.fileSize(destPath()) == bytes

    fun removeFromDisk() {
        Platform.delete(destPath())
        Platform.delete(partialPath())
    }

    fun toInfo(now: Long) = ModelInfo(
        id = id,
        installed = true,
        installedAt = now,
        sha256 = sha256.take(8),
        path = destPath(),
        lastChecked = now,
        name = name,
        version = GemmaModel.VERSION,
        quantization = quantization,
        expectedBytes = bytes,
    )
}

object GemmaModels {
    fun dir(): String = joinPath(Platform.appDir(), "models")

    val Fast = CatalogModel(
        id = LlmModel.Fast,
        name = "Gemma 4 E2B IT",
        fileName = "gemma-4-E2B-it.litertlm",
        repo = "litert-community/gemma-4-E2B-it-litert-lm",
        bytes = MODEL_BYTES,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
    )
    val Precise = CatalogModel(
        id = LlmModel.Precise,
        name = "Gemma 4 E4B IT",
        fileName = "gemma-4-E4B-it.litertlm",
        repo = "litert-community/gemma-4-E4B-it-litert-lm",
        bytes = 3_659_530_240L,
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
    )
    val all = listOf(Fast, Precise)

    fun of(id: LlmModel): CatalogModel = if (id == LlmModel.Precise) Precise else Fast
}

object GemmaModel {
    const val VERSION = "1.0"
    const val QUANTIZATION = "QAT 2/4/8-bit"
    const val DISK_BUFFER_BYTES = 400_000_000L

    /** LiteRT-LM artifact supports 32k; official Gemma 4 is 128k. */
    const val CONTEXT_TOKENS = 32_768
    const val MAX_NUM_TOKENS = CONTEXT_TOKENS / 2 - 5_000

    fun cacheDir(): String = Platform.cacheDir()
}
