package com.yellowtrack.platform.core.database

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

/**
 * Desktop driver, storing the database under the user's application-data directory rather
 * than the working directory so that it survives however the app happens to be launched.
 */
class JvmDatabaseDriverFactory(
    private val databaseFile: File = defaultDatabaseFile(),
) : DatabaseDriverFactory {
    override suspend fun create(): SqlDriver {
        databaseFile.parentFile?.mkdirs()

        return JdbcSqliteDriver(
            url = "jdbc:sqlite:${databaseFile.absolutePath}",
            properties = Properties(),
            schema = YellowTrackDatabase.Schema.synchronous(),
        )
    }

    companion object {
        fun defaultDatabaseFile(): File = File(applicationDataDirectory(), DatabaseDriverFactory.DATABASE_NAME)

        private fun applicationDataDirectory(): File {
            val home = System.getProperty("user.home").orEmpty()
            val osName = System.getProperty("os.name").orEmpty().lowercase()

            val base =
                when {
                    osName.contains("mac") -> File(home, "Library/Application Support")
                    osName.contains("win") -> File(System.getenv("APPDATA") ?: home)
                    else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share")
                }

            return File(base, "YellowTrack")
        }
    }
}
