package dev.nucleusframework.offlinetranslator.engine

import dev.nucleusframework.offlinetranslator.domain.DownloadLog

data class DownloadedModel(
    val path: String,
    val sha256: String,
    val bytes: Long,
    val createdByApp: Boolean = false,
)

fun interface ModelDownloader {
    suspend fun download(
        destPath: String,
        url: String,
        expectedSha256: String,
        expectedBytes: Long,
        onConnect: () -> Unit,
        onVerify: () -> Unit,
        onProgress: (bytes: Long, total: Long, speedBps: Long, log: DownloadLog?) -> Unit,
    ): DownloadedModel
}

object IdleDownloader : ModelDownloader {
    override suspend fun download(
        destPath: String,
        url: String,
        expectedSha256: String,
        expectedBytes: Long,
        onConnect: () -> Unit,
        onVerify: () -> Unit,
        onProgress: (bytes: Long, total: Long, speedBps: Long, log: DownloadLog?) -> Unit,
    ): DownloadedModel = kotlinx.coroutines.awaitCancellation()
}
