package dev.nucleusframework.offlinetranslator.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.nucleusframework.offlinetranslator.db.AppDatabase
import dev.nucleusframework.offlinetranslator.platform.androidContext

internal actual fun createSqlDriver(): SqlDriver = AndroidSqliteDriver(AppDatabase.Schema, androidContext(), "history.db")
