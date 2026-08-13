package dev.nucleusframework.offlinetranslator

import dev.nucleusframework.offlinetranslator.domain.AUTO_LANG
import dev.nucleusframework.offlinetranslator.domain.LangNameStyle
import dev.nucleusframework.offlinetranslator.domain.Languages
import dev.nucleusframework.offlinetranslator.domain.UiLanguage
import dev.nucleusframework.offlinetranslator.engine.GemmaModels
import dev.nucleusframework.offlinetranslator.engine.PiperVoices
import dev.nucleusframework.offlinetranslator.platform.litertLmModelsDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguagesTest {

    @Test
    fun catalogIsThirtyFiveUniqueCodes() {
        assertEquals(35, Languages.all.size)
        assertEquals(35, Languages.all.map { it.code }.toSet().size)
        assertEquals(35, UiLanguage.entries.size)
        assertEquals(Languages.all.map { it.code }.toSet(), UiLanguage.entries.map { it.code }.toSet())
    }

    @Test
    fun audioIsTheFleursSet() {
        assertEquals(
            setOf("ar", "de", "en", "es", "fr", "hi", "it", "ja", "ko", "pt", "ru", "zh"),
            Languages.all.filter { it.audio }.map { it.code }.toSet(),
        )
        assertEquals(12, Languages.audioCount)
        assertTrue(Languages.hasAudio(AUTO_LANG))
        assertTrue(Languages.hasAudio("fr"))
        assertTrue(!Languages.hasAudio("sw"))
    }

    @Test
    fun ttsIsPiperCoverage() {
        assertEquals(30, Languages.ttsCount)
        assertEquals(
            setOf("fil", "hr", "ja", "mi", "th"),
            Languages.all.filter { !it.tts }.map { it.code }.toSet(),
        )
        assertTrue(Languages.hasTts("fr"))
        assertTrue(!Languages.hasTts("ja"))
        assertEquals(
            Languages.all.filter { it.tts }.map { it.code }.toSet(),
            Languages.all.filter { PiperVoices.of(it.code) != null }.map { it.code }.toSet(),
        )
        assertTrue(PiperVoices.forLang("en").size > 1)
        assertTrue(PiperVoices.forLang("fr").size > 1)
        assertTrue(PiperVoices.all().none { it.quality == "low" || it.quality == "x_low" })
        assertTrue(PiperVoices.forLang("en").all { it.quality == "high" })
        assertTrue(PiperVoices.forLang("de").all { it.quality == "high" })
        assertEquals("high", PiperVoices.defaultFor("en")?.quality)
        assertEquals("en_US-lessac-high", PiperVoices.defaultFor("en")?.id)
        assertEquals("high", PiperVoices.defaultFor("de")?.quality)
        assertEquals(PiperVoices.all().size, PiperVoices.all().distinctBy { it.lang to it.name }.size)
        val voicesDir = PiperVoices.dir().replace('\\', '/')
        assertTrue(voicesDir.endsWith("/models/voices"))
        val dest = GemmaModels.Fast.destPath().replace('\\', '/')
        assertTrue(dest.endsWith("/model.litertlm"))
        assertTrue(dest.contains("/${GemmaModels.Fast.registryId}/") || dest.contains("/${GemmaModels.Fast.fileName}/"))
        assertTrue(GemmaModels.Fast.ownerMarkerPath().replace('\\', '/').endsWith("/.edgetranslator"))
        assertEquals("/home/u/.litert-lm/models", litertLmModelsDir("/home/u"))
        assertEquals("/Users/u/.litert-lm/models", litertLmModelsDir("/Users/u"))
        assertEquals("C:\\Users\\u\\.litert-lm\\models", litertLmModelsDir("C:\\Users\\u"))
    }

    @Test
    fun searchFindsAudioLanguages() {
        val hits = Languages.search("audio", UiLanguage.En)
        assertEquals(12, hits.size)
        assertTrue(hits.all { it.audio })
    }

    @Test
    fun labelsFollowNameStyle() {
        assertEquals("Hébreu", Languages.label("he", UiLanguage.Fr, LangNameStyle.System))
        assertEquals("Hebrew", Languages.label("he", UiLanguage.En, LangNameStyle.System))
        assertEquals("עברית", Languages.label("he", UiLanguage.Fr, LangNameStyle.Native))
        assertEquals("English", Languages.label("en", UiLanguage.Fr, LangNameStyle.Native))
        assertEquals("日本語", Languages.get("ja")?.native)
    }
}
