package dev.nucleusframework.offlinetranslator

import dev.nucleusframework.offlinetranslator.domain.LlmBackend
import dev.nucleusframework.offlinetranslator.engine.LlmAccelerator
import dev.nucleusframework.offlinetranslator.engine.pickBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LlmBackendTest {

    @Test
    fun cpuNeverProbesGpu() {
        var probed = false
        val pick = pickBackend(LlmBackend.Cpu, gpuKnown = null) { probed = true; true }
        assertEquals(LlmAccelerator.Cpu, pick.accelerator)
        assertNull(pick.gpuAvailable)
        assertFalse(probed)
    }

    @Test
    fun autoUsesGpuWhenItWorks() {
        val pick = pickBackend(LlmBackend.Auto, gpuKnown = null) { true }
        assertEquals(LlmAccelerator.Gpu, pick.accelerator)
        assertEquals(true, pick.gpuAvailable)
    }

    @Test
    fun autoAndGpuFallBackWhenGpuFails() {
        for (pref in listOf(LlmBackend.Auto, LlmBackend.Gpu)) {
            val pick = pickBackend(pref, gpuKnown = null) { false }
            assertEquals(LlmAccelerator.Cpu, pick.accelerator)
            assertEquals(false, pick.gpuAvailable)
        }
    }

    @Test
    fun knownMissingGpuSkipsProbe() {
        var probed = false
        val pick = pickBackend(LlmBackend.Gpu, gpuKnown = false) { probed = true; true }
        assertEquals(LlmAccelerator.Cpu, pick.accelerator)
        assertEquals(false, pick.gpuAvailable)
        assertFalse(probed)
    }

    @Test
    fun knownGpuIsStillTriedWhenAsked() {
        var probed = false
        val pick = pickBackend(LlmBackend.Gpu, gpuKnown = true) { probed = true; true }
        assertEquals(LlmAccelerator.Gpu, pick.accelerator)
        assertTrue(probed)
    }

    @Test
    fun autoUsesNpuFirstWhenItWorks() {
        var gpuProbed = false
        val pick = pickBackend(
            preference = LlmBackend.Auto,
            gpuKnown = null,
            gpuWorks = { gpuProbed = true; true },
            npuKnown = null,
            npuWorks = { true },
        )
        assertEquals(LlmAccelerator.Npu, pick.accelerator)
        assertEquals(true, pick.npuAvailable)
        assertFalse(gpuProbed)
    }

    @Test
    fun autoFallsBackToGpuWhenNpuFails() {
        val pick = pickBackend(
            preference = LlmBackend.Auto,
            gpuKnown = null,
            gpuWorks = { true },
            npuKnown = null,
            npuWorks = { false },
        )
        assertEquals(LlmAccelerator.Gpu, pick.accelerator)
        assertEquals(true, pick.gpuAvailable)
        assertEquals(false, pick.npuAvailable)
    }

    @Test
    fun gpuNeverProbesNpu() {
        var npuProbed = false
        val pick = pickBackend(
            preference = LlmBackend.Gpu,
            gpuKnown = null,
            gpuWorks = { true },
            npuKnown = null,
            npuWorks = { npuProbed = true; true },
        )
        assertEquals(LlmAccelerator.Gpu, pick.accelerator)
        assertNull(pick.npuAvailable)
        assertFalse(npuProbed)
    }

    @Test
    fun knownMissingNpuSkipsProbe() {
        var npuProbed = false
        val pick = pickBackend(
            preference = LlmBackend.Auto,
            gpuKnown = null,
            gpuWorks = { true },
            npuKnown = false,
            npuWorks = { npuProbed = true; true },
        )
        assertEquals(LlmAccelerator.Gpu, pick.accelerator)
        assertEquals(false, pick.npuAvailable)
        assertFalse(npuProbed)
    }

    @Test
    fun npuPreferenceFallsBackToGpu() {
        val pick = pickBackend(
            preference = LlmBackend.Npu,
            gpuKnown = null,
            gpuWorks = { true },
            npuKnown = null,
            npuWorks = { false },
        )
        assertEquals(LlmAccelerator.Gpu, pick.accelerator)
        assertEquals(false, pick.npuAvailable)
        assertEquals(true, pick.gpuAvailable)
    }

    @Test
    fun cpuNeverProbesNpu() {
        var npuProbed = false
        val pick = pickBackend(
            preference = LlmBackend.Cpu,
            gpuKnown = null,
            gpuWorks = { true },
            npuKnown = null,
            npuWorks = { npuProbed = true; true },
        )
        assertEquals(LlmAccelerator.Cpu, pick.accelerator)
        assertNull(pick.npuAvailable)
        assertFalse(npuProbed)
    }
}
