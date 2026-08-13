package dev.nucleusframework.offlinetranslator

import dev.nucleusframework.offlinetranslator.engine.linuxGpuTeardownUnsafe

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxGpuPolicyTest {

    @Test
    fun linuxKeepsGpuConversationOpen() {
        assertTrue(linuxGpuTeardownUnsafe("Linux"))
        assertTrue(linuxGpuTeardownUnsafe("GNU/Linux"))
    }

    @Test
    fun windowsAndMacStillCloseConversations() {
        assertFalse(linuxGpuTeardownUnsafe("Windows 11"))
        assertFalse(linuxGpuTeardownUnsafe("Mac OS X"))
    }
}
