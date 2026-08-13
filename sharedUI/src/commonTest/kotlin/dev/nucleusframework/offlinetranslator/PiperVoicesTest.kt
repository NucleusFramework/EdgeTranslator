package dev.nucleusframework.offlinetranslator

import dev.nucleusframework.offlinetranslator.domain.AUTO_LANG
import dev.nucleusframework.offlinetranslator.engine.PiperVoices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PiperVoicesTest {

    /** Settings lists the voices you have, not the 30-language catalog. */
    @Test
    fun visibleLangsListsOnlyActiveInstalledAndDownloading() {
        assertEquals(emptyList(), PiperVoices.visibleLangs(active = emptySet(), busy = null, installed = emptySet()))

        val shown = PiperVoices.visibleLangs(
            active = setOf("fr", AUTO_LANG),
            busy = "de_DE-thorsten-high",
            installed = setOf("en"),
        )
        assertEquals(setOf("fr", "en", "de"), shown.toSet())
        assertEquals(shown, shown.distinct())
        assertTrue(AUTO_LANG !in shown, "auto is not a voice language")
        assertTrue(shown.size < PiperVoices.langs.size, "the catalog must not be listed in full")
    }
}
