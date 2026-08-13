package dev.nucleusframework.offlinetranslator.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.nucleusframework.offlinetranslator.db.AppDatabase
import dev.nucleusframework.offlinetranslator.domain.HistoryItem
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlHistoryStoreTest {

    private fun store(): SqlHistoryStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), AppDatabase.Schema)
        return SqlHistoryStore(driver)
    }

    @Test
    fun insertQueryPinAndPurge() {
        val history = store()
        history.insert(HistoryItem("1", 10, "fr", "en", "contrat", "contract", pinned = false))
        history.insert(HistoryItem("2", 20, "en", "fr", "hello", "bonjour", pinned = false))
        history.insert(HistoryItem("1", 10, "fr", "en", "dup", "dup", pinned = false))
        assertEquals(listOf("2", "1"), history.all().map { it.id })
        assertEquals("contrat", history.get("1")?.sourceText)

        history.togglePin("1")
        assertTrue(history.get("1")!!.pinned)
        assertEquals(listOf("1", "2"), history.all().map { it.id })

        history.delete("2")
        assertEquals(listOf("1"), history.all().map { it.id })
        history.insert(HistoryItem("2", 20, "en", "fr", "hello", "bonjour", pinned = false))

        history.purgeOlderThan(25)
        assertEquals(listOf("1"), history.all().map { it.id })

        history.deleteUnpinned()
        assertEquals(listOf("1"), history.all().map { it.id })
        history.togglePin("1")
        history.deleteUnpinned()
        assertTrue(history.all().isEmpty())
        assertFalse(history.get("1") != null)

        history.insert(HistoryItem("3", 30, "fr", "en", "pin", "pin", pinned = true))
        history.insert(HistoryItem("4", 40, "en", "fr", "tmp", "tmp", pinned = false))
        history.clear()
        assertTrue(history.all().isEmpty())
    }
}
