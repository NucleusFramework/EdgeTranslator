package dev.nucleusframework.offlinetranslator.engine

import dev.nucleusframework.offlinetranslator.domain.LlmBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LlmAccelerator { None, Cpu, Gpu }

object LlmRuntime {
    @Volatile
    var preference: LlmBackend = LlmBackend.Auto

    private val _accelerator = MutableStateFlow(LlmAccelerator.None)
    val accelerator: StateFlow<LlmAccelerator> = _accelerator.asStateFlow()

    private val _gpuAvailable = MutableStateFlow<Boolean?>(null)
    val gpuAvailable: StateFlow<Boolean?> = _gpuAvailable.asStateFlow()

    internal fun report(value: LlmAccelerator) {
        _accelerator.value = value
    }

    internal fun reportGpuAvailable(available: Boolean) {
        _gpuAvailable.value = available
    }
}

internal data class BackendPick(val accelerator: LlmAccelerator, val gpuAvailable: Boolean?)

internal fun pickBackend(
    preference: LlmBackend,
    gpuKnown: Boolean?,
    gpuWorks: () -> Boolean,
): BackendPick {
    if (preference == LlmBackend.Cpu) return BackendPick(LlmAccelerator.Cpu, gpuKnown)
    if (gpuKnown != false && gpuWorks()) return BackendPick(LlmAccelerator.Gpu, true)
    return BackendPick(LlmAccelerator.Cpu, false)
}

/** NVIDIA WebGPU teardown SIGILL after a GPU turn — keep the conversation alive. */
internal fun linuxGpuTeardownUnsafe(osName: String): Boolean =
    osName.contains("linux", ignoreCase = true)
