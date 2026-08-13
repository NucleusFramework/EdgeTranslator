package dev.nucleusframework.offlinetranslator

import dev.nucleusframework.offlinetranslator.data.FileStore
import dev.nucleusframework.offlinetranslator.di.createAppGraph
import dev.nucleusframework.offlinetranslator.engine.GemmaTranslator
import dev.nucleusframework.offlinetranslator.engine.HuggingFaceModelDownloader
import kotlin.test.Test
import kotlin.test.assertIs

class AppGraphTest {

    @Test
    fun productionGraphBindsImplementations() {
        val graph = createAppGraph()
        assertIs<FileStore>(graph.store)
        assertIs<GemmaTranslator>(graph.translator)
        assertIs<HuggingFaceModelDownloader>(graph.downloader)
    }
}
