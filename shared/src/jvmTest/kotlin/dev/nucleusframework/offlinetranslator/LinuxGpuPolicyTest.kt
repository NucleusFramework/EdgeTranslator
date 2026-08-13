package dev.nucleusframework.offlinetranslator

import dev.nucleusframework.offlinetranslator.engine.WorkerEvent
import dev.nucleusframework.offlinetranslator.engine.decodeWorkerField
import dev.nucleusframework.offlinetranslator.engine.encodeWorkerField
import dev.nucleusframework.offlinetranslator.engine.LinuxGpuWorkerProcess
import dev.nucleusframework.offlinetranslator.engine.hasMultimodalPayload
import dev.nucleusframework.offlinetranslator.engine.linuxGpuCompanionLibs
import dev.nucleusframework.offlinetranslator.engine.linuxGpuTeardownUnsafe
import dev.nucleusframework.offlinetranslator.engine.parseWorkerLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinuxGpuPolicyTest {

    @Test
    fun linuxNvidiaIsolatesGpu() {
        assertTrue(linuxGpuTeardownUnsafe("Linux", nvidiaPresent = true))
        assertTrue(linuxGpuTeardownUnsafe("GNU/Linux", nvidiaPresent = true))
    }

    @Test
    fun linuxWithoutNvidiaStaysInProcess() {
        assertFalse(linuxGpuTeardownUnsafe("Linux", nvidiaPresent = false))
    }

    @Test
    fun windowsAndMacStillCloseConversations() {
        assertFalse(linuxGpuTeardownUnsafe("Windows 11", nvidiaPresent = true))
        assertFalse(linuxGpuTeardownUnsafe("Mac OS X", nvidiaPresent = false))
    }

    @Test
    fun workerCommandReexecsNativeImageAndUsesClasspathOnHotspot() {
        val args = checkNotNull(LinuxGpuWorkerProcess.workerCommand("/m.litertlm", "/cache", 4))
        assertTrue(args.size >= 4)
        val native = System.getProperty("org.graalvm.nativeimage.imagecode") != null
        if (native) {
            assertTrue("--gpu-worker" in args)
        } else {
            assertTrue("dev.nucleusframework.offlinetranslator.engine.LinuxGpuWorkerKt" in args)
            assertTrue("-cp" in args)
        }
    }

    @Test
    fun imageAndAudioSkipTheGpuWorker() {
        assertFalse(hasMultimodalPayload(null, null))
        assertFalse(hasMultimodalPayload(byteArrayOf(), byteArrayOf()))
        assertTrue(hasMultimodalPayload(byteArrayOf(1), null))
        assertTrue(hasMultimodalPayload(null, byteArrayOf(1)))
    }

    @Test
    fun linuxCompanionLibsDoNotLoadASecondDawn() {
        val libs = linuxGpuCompanionLibs()
        assertTrue("libOpenCL.so" in libs)
        assertTrue("libLiteRtTopKWebGpuSampler.so" in libs)
        assertFalse(libs.any { it.contains("dawn", ignoreCase = true) })
        assertFalse("libLiteRt.so" in libs)
    }

    @Test
    fun workerProtocolRoundTripsNewlinesAndUnicode() {
        val text = "Bonjour\nça va ?"
        assertEquals(text, decodeWorkerField(encodeWorkerField(text)))
        assertEquals(WorkerEvent.Ready, parseWorkerLine("READY"))
        assertEquals(WorkerEvent.Partial(text), parseWorkerLine("PARTIAL ${encodeWorkerField(text)}"))
        assertEquals(WorkerEvent.Done(text), parseWorkerLine("DONE ${encodeWorkerField(text)}\r"))
        assertEquals(WorkerEvent.Failed("boom"), parseWorkerLine("ERROR boom"))
        assertNull(parseWorkerLine("INFO: noise"))
    }
}
