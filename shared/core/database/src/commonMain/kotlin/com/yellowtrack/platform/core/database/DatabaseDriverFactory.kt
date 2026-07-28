package com.yellowtrack.platform.core.database

import app.cash.sqldelight.db.SqlDriver

/**
 * Creates the platform's SQLite driver, with the schema already created or migrated.
 *
 * Suspending because the web driver's handshake with its worker is asynchronous. Each
 * platform provides its own implementation through the Koin platform module.
 */
interface DatabaseDriverFactory {
    suspend fun create(): SqlDriver

    companion object {
        /** File name used for the on-device database on every platform that has a filesystem. */
        const val DATABASE_NAME: String = "yellowtrack.db"
    }
}

/** Builds the database from a platform driver. */
suspend fun createYellowTrackDatabase(driverFactory: DatabaseDriverFactory): YellowTrackDatabase =
    YellowTrackDatabase(driverFactory.create())
