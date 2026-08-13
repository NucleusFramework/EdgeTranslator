package dev.nucleusframework.offlinetranslator.di

import dev.nucleusframework.offlinetranslator.app.AppViewModel
import dev.nucleusframework.offlinetranslator.data.AppStore
import dev.nucleusframework.offlinetranslator.engine.ModelDownloader
import dev.nucleusframework.offlinetranslator.engine.Translator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph

@DependencyGraph(AppScope::class)
interface AppGraph {
    val viewModelFactory: AppViewModel.Factory
    val store: AppStore
    val translator: Translator
    val downloader: ModelDownloader
}

fun createAppGraph(): AppGraph = createGraph()
