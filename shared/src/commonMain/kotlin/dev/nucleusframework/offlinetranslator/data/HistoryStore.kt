package dev.nucleusframework.offlinetranslator.data

import app.cash.sqldelight.db.SqlDriver
import dev.nucleusframework.offlinetranslator.db.AppDatabase
import dev.nucleusframework.offlinetranslator.db.History
import dev.nucleusframework.offlinetranslator.domain.HistoryItem
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

interface HistoryStore {
    fun all(): List<HistoryItem>
    fun get(id: String): HistoryItem?
    fun insert(item: HistoryItem)
    fun togglePin(id: String)
    fun delete(id: String)
    fun deleteUnpinned()
    fun clear()
    fun purgeOlderThan(cutEpochMs: Long)
}

class MemoryHistoryStore(initial: List<HistoryItem> = emptyList()) : HistoryStore {
    private val items = initial.toMutableList()

    override fun all(): List<HistoryItem> =
        items.sortedWith(compareByDescending<HistoryItem> { it.pinned }.thenByDescending { it.createdAt })

    override fun get(id: String): HistoryItem? = items.find { it.id == id }

    override fun insert(item: HistoryItem) {
        if (items.none { it.id == item.id }) items.add(item)
    }

    override fun togglePin(id: String) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) items[i] = items[i].copy(pinned = !items[i].pinned)
    }

    override fun delete(id: String) {
        items.removeAll { it.id == id }
    }

    override fun deleteUnpinned() {
        items.removeAll { !it.pinned }
    }

    override fun clear() {
        items.clear()
    }

    override fun purgeOlderThan(cutEpochMs: Long) {
        items.removeAll { !it.pinned && it.createdAt < cutEpochMs }
    }
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class SqlHistoryStore(driver: SqlDriver) : HistoryStore {
    private val queries = AppDatabase(driver).historyQueries

    override fun all(): List<HistoryItem> = queries.selectAll().executeAsList().map { it.toDomain() }

    override fun get(id: String): HistoryItem? = queries.selectById(id).executeAsOneOrNull()?.toDomain()

    override fun insert(item: HistoryItem) {
        queries.insert(
            id = item.id,
            createdAt = item.createdAt,
            sourceLang = item.sourceLang,
            targetLang = item.targetLang,
            sourceText = item.sourceText,
            targetText = item.targetText,
            pinned = if (item.pinned) 1 else 0,
        )
    }

    override fun togglePin(id: String) {
        queries.togglePin(id)
    }

    override fun delete(id: String) {
        queries.deleteById(id)
    }

    override fun deleteUnpinned() {
        queries.deleteUnpinned()
    }

    override fun clear() {
        queries.deleteAll()
    }

    override fun purgeOlderThan(cutEpochMs: Long) {
        queries.purgeOlderThan(cutEpochMs)
    }
}

private fun History.toDomain() = HistoryItem(
    id = id,
    createdAt = createdAt,
    sourceLang = sourceLang,
    targetLang = targetLang,
    sourceText = sourceText,
    targetText = targetText,
    pinned = pinned != 0L,
)
