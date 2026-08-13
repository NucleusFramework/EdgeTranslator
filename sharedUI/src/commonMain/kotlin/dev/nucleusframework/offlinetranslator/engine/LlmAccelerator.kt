package dev.nucleusframework.offlinetranslator.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LlmAccelerator { None, Cpu, Gpu }

object LlmRuntime {
    private val _accelerator = MutableStateFlow(LlmAccelerator.None)
    val accelerator: StateFlow<LlmAccelerator> = _accelerator.asStateFlow()

    internal fun report(value: LlmAccelerator) {
        _accelerator.value = value
    }
}
