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

/**
 * Turns on foreign key enforcement, which SQLite leaves off by default.
 *
 * Declared in the schema and enforced nowhere was the state until 0.7.0: a row could
 * reference a parent that did not exist and nothing would say so. That was survivable only
 * because it was accidental — a pulled child whose parent had not arrived yet simply sat
 * invisible until it did.
 *
 * Enabling it is safe now that the server closes each page over its parents, and not
 * before: pages are ordered by `server_seq`, an edit bumps that, and a parent edited after
 * its own child used to arrive a page later. With this on, that failed — and failed again
 * on every retry, because the cursor only advances once a page is written.
 *
 * Existing rows are not revalidated. SQLite checks this on write, so a database that
 * already holds an orphan keeps it rather than refusing to open.
 */
fun SqlDriver.enforceForeignKeys() {
    execute(identifier = null, sql = "PRAGMA foreign_keys = ON;", parameters = 0)
}
