package dev.nucleusframework.offlinetranslator.engine

import androidx.compose.runtime.Immutable
import dev.nucleusframework.offlinetranslator.domain.LlmModel
import dev.nucleusframework.offlinetranslator.domain.MODEL_BYTES
import dev.nucleusframework.offlinetranslator.domain.ModelInfo
import dev.nucleusframework.offlinetranslator.platform.Platform
import dev.nucleusframework.offlinetranslator.platform.joinPath
import dev.nucleusframework.offlinetranslator.platform.parentPath

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
    /** LiteRT-LM CLI registry id (`litert-lm run gemma4-e2b`). */
    val registryId: String,
    val repo: String,
    val bytes: Long,
    val sha256: String,
    val quantization: String = GemmaModel.QUANTIZATION,
) {
    val url: String get() = "https://huggingface.co/$repo/resolve/main/$fileName"

    fun aliases(): List<String> = listOf(registryId, fileName)

    fun modelDir(): String = parentPath(destPath())

    fun destPath(): String = resolvedDest() ?: joinPath(joinPath(GemmaModels.dir(), registryId), "model.litertlm")

    fun partialPath(): String = destPath() + ".partial"

    fun ownerMarkerPath(): String = joinPath(modelDir(), ".edgetranslator")

    private fun resolvedDest(): String? = aliases()
        .map { joinPath(joinPath(GemmaModels.dir(), it), "model.litertlm") }
        .firstOrNull { Platform.fileSize(it) == bytes }

    fun isOnDisk(): Boolean = Platform.fileSize(destPath()) == bytes

    fun ownedByApp(): Boolean = Platform.exists(ownerMarkerPath())

    fun markOwned() {
        Platform.mkdir(modelDir())
        Platform.writeText(ownerMarkerPath(), "")
    }

    fun removeFromDisk() {
        Platform.delete(destPath())
        Platform.delete(partialPath())
        Platform.deleteRecursively(modelDir())
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
    fun dir(): String = Platform.modelsDir()

    val Fast = CatalogModel(
        id = LlmModel.Fast,
        name = "Gemma 4 E2B IT",
        fileName = "gemma-4-E2B-it.litertlm",
        registryId = "gemma4-e2b",
        repo = "litert-community/gemma-4-E2B-it-litert-lm",
        bytes = MODEL_BYTES,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
    )
    val Precise = CatalogModel(
        id = LlmModel.Precise,
        name = "Gemma 4 E4B IT",
        fileName = "gemma-4-E4B-it.litertlm",
        registryId = "gemma4-e4b",
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

    /**
     * 0.14 ConversationConfig has no maxOutputToken, so the source field must leave
     * room for the system prompt and unbounded decode inside [MAX_NUM_TOKENS].
     */
    const val MAX_INPUT_CHARS = 8_000

    fun capInput(text: String): String =
        if (text.length <= MAX_INPUT_CHARS) text else text.take(MAX_INPUT_CHARS)

    fun cacheDir(): String = Platform.cacheDir()
}
