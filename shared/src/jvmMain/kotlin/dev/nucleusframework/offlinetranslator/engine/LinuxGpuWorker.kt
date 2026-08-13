package dev.nucleusframework.offlinetranslator.engine

import dev.nucleusframework.offlinetranslator.domain.LlmBackend
import dev.nucleusframework.offlinetranslator.platform.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.Base64
import kotlin.system.exitProcess

internal sealed class WorkerEvent {
    data class Partial(val text: String) : WorkerEvent()
    data class Done(val text: String) : WorkerEvent()
    data class Failed(val message: String) : WorkerEvent()
    data object Ready : WorkerEvent()
}

internal fun encodeWorkerField(text: String): String =
    Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

internal fun decodeWorkerField(b64: String): String =
    String(Base64.getDecoder().decode(b64), Charsets.UTF_8)

internal fun parseWorkerLine(line: String): WorkerEvent? {
    val trimmed = line.trimEnd('\r')
    return when {
        trimmed == "READY" -> WorkerEvent.Ready
        trimmed.startsWith("PARTIAL ") -> WorkerEvent.Partial(decodeWorkerField(trimmed.removePrefix("PARTIAL ")))
        trimmed.startsWith("DONE ") -> WorkerEvent.Done(decodeWorkerField(trimmed.removePrefix("DONE ")))
        trimmed.startsWith("ERROR ") -> WorkerEvent.Failed(trimmed.removePrefix("ERROR "))
        else -> null
    }
}

/**
 * GPU generate in a child JVM. NVIDIA's compiler SIGILL's in-process after
 * the first tokens (libnvidia-gpucomp); the parent keeps the text and stays up.
 */
internal class LinuxGpuWorkerProcess(
    private val process: Process,
    private val reader: BufferedReader,
    private val writer: BufferedWriter,
) {
    suspend fun generate(
        systemInstruction: String,
        userMessage: String,
        onPartial: (String) -> Unit,
    ): String = withContext(IoDispatcher) {
        writer.write("GEN\n")
        writer.write("SYSTEM ${encodeWorkerField(systemInstruction)}\n")
        writer.write("USER ${encodeWorkerField(userMessage)}\n")
        writer.write("END\n")
        writer.flush()
        var last = ""
        while (true) {
            ensureActive()
            val line = reader.readLine() ?: break
            when (val event = parseWorkerLine(line)) {
                is WorkerEvent.Partial -> {
                    last = event.text
                    onPartial(last)
                }
                is WorkerEvent.Done -> return@withContext event.text
                is WorkerEvent.Failed -> error(event.message)
                else -> Unit
            }
        }
        if (last.isNotEmpty()) return@withContext last
        error("GPU worker exited (code ${process.waitFor()})")
    }

    fun destroy() {
        runCatching { writer.write("QUIT\n"); writer.flush() }
        process.destroy()
    }

    companion object {
        internal fun workerCommand(modelPath: String, cacheDir: String, threads: Int): List<String>? {
            val self = ProcessHandle.current().info().command().orElse(null)
            val native = System.getProperty("org.graalvm.nativeimage.imagecode") != null
            if (native && self != null) {
                return listOf(self, "--gpu-worker", modelPath, cacheDir, threads.toString())
            }
            val java = self?.takeIf { java.io.File(it).name.startsWith("java") }
                ?: (System.getProperty("java.home") + "/bin/java")
            val cp = System.getProperty("java.class.path") ?: return null
            val resources = System.getProperty("compose.application.resources.dir").orEmpty()
            return buildList {
                add(java)
                add("--enable-native-access=ALL-UNNAMED")
                add("-XX:ErrorFile=${System.getProperty("java.io.tmpdir")}/hs_err_gpu_worker_%p.log")
                if (resources.isNotEmpty()) add("-Dcompose.application.resources.dir=$resources")
                add("-Dedgetranslator.gpu.worker=1")
                add("-cp")
                add(cp)
                add("dev.nucleusframework.offlinetranslator.engine.LinuxGpuWorkerKt")
                add(modelPath)
                add(cacheDir)
                add(threads.toString())
            }
        }

        fun start(modelPath: String, cacheDir: String, threads: Int): LinuxGpuWorkerProcess? {
            val command = workerCommand(modelPath, cacheDir, threads) ?: return null
            val process = ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .apply { environment()["EDGE_TRANSLATOR_GPU_WORKER"] = "1" }
                .start()
            val reader = process.inputStream.bufferedReader()
            val writer = process.outputStream.bufferedWriter()
            var ready = false
            while (true) {
                val line = reader.readLine() ?: break
                if (parseWorkerLine(line) == WorkerEvent.Ready) {
                    ready = true
                    break
                }
            }
            if (!ready || !process.isAlive) {
                System.err.println("GPU worker failed to become READY (alive=${process.isAlive})")
                process.destroy()
                return null
            }
            return LinuxGpuWorkerProcess(process, reader, writer)
        }
    }
}

fun main(args: Array<String>) = runGpuWorker(args)

fun runGpuWorker(args: Array<String>) {
    val modelPath = args.getOrNull(0) ?: error("model path")
    val cacheDir = args.getOrNull(1) ?: error("cache dir")
    val threads = args.getOrNull(2)?.toIntOrNull() ?: 0
    val llm = NativeLlm()
    try {
        llm.loadInProcess(modelPath, cacheDir, threads, LlmBackend.Gpu)
        println("READY")
        System.out.flush()
        val input = System.`in`.bufferedReader()
        while (true) {
            when (input.readLine() ?: break) {
                "QUIT" -> break
                "GEN" -> {
                    var system = ""
                    var user = ""
                    while (true) {
                        val line = input.readLine() ?: break
                        when {
                            line == "END" -> break
                            line.startsWith("SYSTEM ") -> system = decodeWorkerField(line.removePrefix("SYSTEM "))
                            line.startsWith("USER ") -> user = decodeWorkerField(line.removePrefix("USER "))
                        }
                    }
                    try {
                        val text = runBlocking {
                            llm.generateInProcess(system, user) { partial ->
                                println("PARTIAL ${encodeWorkerField(partial)}")
                                System.out.flush()
                            }
                        }
                        println("DONE ${encodeWorkerField(text)}")
                        System.out.flush()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        println("ERROR ${t.message ?: t::class.simpleName}")
                        System.out.flush()
                    }
                }
            }
        }
    } catch (t: Throwable) {
        System.err.println("WORKER_FAIL ${t.message}")
        exitProcess(1)
    }
}
