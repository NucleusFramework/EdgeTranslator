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
}
