package dev.nucleusframework.offlinetranslator.engine

import dev.nucleusframework.offlinetranslator.domain.DownloadError
import dev.nucleusframework.offlinetranslator.domain.DownloadFailedException
import dev.nucleusframework.offlinetranslator.domain.DownloadLog
import dev.nucleusframework.offlinetranslator.platform.IoDispatcher
import dev.nucleusframework.offlinetranslator.platform.Platform
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class)
@Inject
class HuggingFaceModelDownloader(
    private val httpClient: HttpClient,
) : ModelDownloader {

    override suspend fun download(
        destPath: String,
        url: String,
        expectedSha256: String,
        expectedBytes: Long,
        onConnect: () -> Unit,
        onVerify: () -> Unit,
        onProgress: (bytes: Long, total: Long, speedBps: Long, log: DownloadLog?) -> Unit,
    ): DownloadedModel = withContext(IoDispatcher) {
        val existing = Platform.fileSize(destPath)
        val skipSha = expectedSha256.isBlank()
        if (existing == expectedBytes && expectedBytes > 0) {
            if (skipSha) {
                onProgress(existing, expectedBytes, 0, DownloadLog.AlreadyPresent)
                return@withContext DownloadedModel(destPath, "", existing)
            }
            onVerify()
            val sha = Platform.sha256(destPath)
            if (sha != null && sha.equals(expectedSha256, ignoreCase = true)) {
                onProgress(existing, expectedBytes, 0, DownloadLog.AlreadyPresent)
                return@withContext DownloadedModel(destPath, sha, existing)
            }
        }

        val partial = "$destPath.partial"
        var downloaded = Platform.fileSize(partial)
        onConnect()
        try {
            httpClient.prepareGet(url) {
                header(HttpHeaders.UserAgent, "EdgeTranslator/1.0")
                header(HttpHeaders.AcceptEncoding, "identity")
                if (downloaded > 0) header(HttpHeaders.Range, "bytes=$downloaded-")
            }.execute { response ->
                val status = response.status.value
                if (status == 200 && downloaded > 0) {
                    Platform.truncate(partial)
                    downloaded = 0
                } else if (!response.status.isSuccess()) {
                    throw DownloadFailedException(httpError(status))
                }
                val remaining = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                val total = when {
                    status == 206 && remaining != null -> downloaded + remaining
                    remaining != null && downloaded == 0L -> remaining
                    else -> expectedBytes
                }
                onProgress(downloaded, total, 0, DownloadLog.Transfer)
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(256 * 1024)
                var lastTick = Platform.now()
                var windowBytes = 0L
                var lastLogAt = 0L
                while (!channel.isClosedForRead) {
                    ensureActive()
                    val n = channel.readAvailable(buffer)
                    if (n <= 0) continue
                    Platform.writeAppend(partial, buffer, 0, n)
                    downloaded += n
                    windowBytes += n
                    val now = Platform.now()
                    val dt = now - lastTick
                    if (dt >= 200) {
                        val speed = if (dt > 0) windowBytes * 1000 / dt else 0
                        val log = if (now - lastLogAt >= 1500) {
                            lastLogAt = now
                            DownloadLog.ReceivedMb((downloaded / 1_000_000).toInt(), (total / 1_000_000).toInt())
                        } else null
                        onProgress(downloaded, total, speed, log)
                        lastTick = now
                        windowBytes = 0
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloadFailedException) {
            throw e
        } catch (e: Exception) {
            throw DownloadFailedException(DownloadError.Interrupted)
        }

        onVerify()
        if (skipSha) {
            Platform.delete(destPath)
            if (!Platform.rename(partial, destPath)) {
                throw DownloadFailedException(DownloadError.InstallFailed)
            }
            return@withContext DownloadedModel(destPath, "", Platform.fileSize(destPath))
        }
        val sha = Platform.sha256(partial) ?: throw DownloadFailedException(DownloadError.ShaCompute)
        if (!sha.equals(expectedSha256, ignoreCase = true)) {
            Platform.delete(partial)
            throw DownloadFailedException(DownloadError.ShaMismatch)
        }
        Platform.delete(destPath)
        if (!Platform.rename(partial, destPath)) {
            throw DownloadFailedException(DownloadError.InstallFailed)
        }
        DownloadedModel(destPath, sha, Platform.fileSize(destPath))
    }

    private fun httpError(status: Int): DownloadError = when (status) {
        401, 403 -> DownloadError.HttpDenied(status)
        404 -> DownloadError.NotFound
        else -> DownloadError.Http(status)
    }
}
