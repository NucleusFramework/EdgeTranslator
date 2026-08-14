package dev.nucleusframework.offlinetranslator.engine

import dev.nucleusframework.offlinetranslator.domain.LlmBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LlmAccelerator { None, Cpu, Gpu, Npu }

object LlmRuntime {
    @Volatile
    var preference: LlmBackend = LlmBackend.Auto

    private val _accelerator = MutableStateFlow(LlmAccelerator.None)
    val accelerator: StateFlow<LlmAccelerator> = _accelerator.asStateFlow()

    private val _gpuAvailable = MutableStateFlow<Boolean?>(null)
    val gpuAvailable: StateFlow<Boolean?> = _gpuAvailable.asStateFlow()

    private val _npuAvailable = MutableStateFlow<Boolean?>(null)
    val npuAvailable: StateFlow<Boolean?> = _npuAvailable.asStateFlow()

    internal fun report(value: LlmAccelerator) {
        _accelerator.value = value
    }

    internal fun reportGpuAvailable(available: Boolean) {
        _gpuAvailable.value = available
    }

    internal fun reportNpuAvailable(available: Boolean) {
        _npuAvailable.value = available
    }
}

internal data class BackendPick(
    val accelerator: LlmAccelerator,
    val gpuAvailable: Boolean?,
    val npuAvailable: Boolean? = null,
)

internal fun pickBackend(
    preference: LlmBackend,
    gpuKnown: Boolean?,
    npuKnown: Boolean? = null,
    npuWorks: () -> Boolean = { false },
    gpuWorks: () -> Boolean,
): BackendPick {
    if (preference == LlmBackend.Cpu) return BackendPick(LlmAccelerator.Cpu, gpuKnown, npuKnown)

    val tryNpu = preference == LlmBackend.Auto || preference == LlmBackend.Npu
    var npuAvailable = npuKnown
    if (tryNpu && npuKnown != false) {
        if (npuWorks()) return BackendPick(LlmAccelerator.Npu, gpuKnown, true)
        npuAvailable = false
    }

    if (gpuKnown != false && gpuWorks()) return BackendPick(LlmAccelerator.Gpu, true, npuAvailable)
    return BackendPick(LlmAccelerator.Cpu, false, npuAvailable)
}

/** NVIDIA WebGPU SIGILL after a GPU turn. ARM without NVIDIA stays in-process. */
internal fun linuxGpuTeardownUnsafe(osName: String, nvidiaPresent: Boolean): Boolean =
    osName.contains("linux", ignoreCase = true) && nvidiaPresent
