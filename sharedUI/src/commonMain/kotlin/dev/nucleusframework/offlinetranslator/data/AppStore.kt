package dev.nucleusframework.offlinetranslator.data

import dev.nucleusframework.offlinetranslator.domain.AppData
import dev.nucleusframework.offlinetranslator.platform.Platform
import dev.nucleusframework.offlinetranslator.platform.joinPath
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

interface AppStore {
    fun load(): AppData
    fun save(data: AppData)
}

class MemoryStore(initial: AppData = AppData()) : AppStore {
    private var data: AppData = initial
    override fun load(): AppData = data
    override fun save(data: AppData) {
        this.data = data
    }
}

@ContributesBinding(AppScope::class)
@Inject
class FileStore(
    private val dir: () -> String = { Platform.appDir() },
) : AppStore {
    private val file get() = joinPath(dir(), "state.txt")

    override fun load(): AppData {
        val raw = Platform.readText(file)
        if (raw.isNullOrBlank()) return seedData()
        return runCatching { decodeSnapshot(raw) }.getOrElse { seedData() }
    }

    override fun save(data: AppData) {
        Platform.writeText(file, encodeSnapshot(data))
    }
}
