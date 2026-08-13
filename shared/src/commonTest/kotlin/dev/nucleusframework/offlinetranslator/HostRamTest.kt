package dev.nucleusframework.offlinetranslator

import dev.nucleusframework.offlinetranslator.domain.GIB_BYTES
import dev.nucleusframework.offlinetranslator.domain.LlmModel
import dev.nucleusframework.offlinetranslator.domain.allowedOn
import dev.nucleusframework.offlinetranslator.domain.minRamGib
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostRamTest {

    @Test
    fun advertisedMinima() {
        assertEquals(8, LlmModel.Fast.minRamGib())
        assertEquals(16, LlmModel.Precise.minRamGib())
    }

    @Test
    fun unknownProbeIsPermissive() {
        assertTrue(LlmModel.Fast.allowedOn(0L))
        assertTrue(LlmModel.Precise.allowedOn(0L))
        assertTrue(LlmModel.Precise.allowedOn(-1L))
    }

    @Test
    fun slackAcceptsStickerRamThatReportsShort() {
        assertTrue(LlmModel.Fast.allowedOn(7L * GIB_BYTES + 1))
        assertTrue(LlmModel.Fast.allowedOn(8L * GIB_BYTES))
        assertFalse(LlmModel.Fast.allowedOn(6L * GIB_BYTES))
        assertTrue(LlmModel.Precise.allowedOn(15L * GIB_BYTES + 1))
        assertTrue(LlmModel.Precise.allowedOn(16L * GIB_BYTES))
        assertFalse(LlmModel.Precise.allowedOn(14L * GIB_BYTES))
    }
}
