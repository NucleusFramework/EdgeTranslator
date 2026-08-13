package dev.nucleusframework.offlinetranslator.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.nucleusframework.offlinetranslator.db.AppDatabase
import dev.nucleusframework.offlinetranslator.platform.Platform
import dev.nucleusframework.offlinetranslator.platform.joinPath
import java.util.Properties

internal actual fun createSqlDriver(): SqlDriver {
    val dir = Platform.databasesDir()
    Platform.mkdir(dir)
    val url = "jdbc:sqlite:${joinPath(dir, "history.db")}"
    return JdbcSqliteDriver(url, Properties(), AppDatabase.Schema)
}
