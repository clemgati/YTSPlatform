package com.yellowtrack.platform.core.database

import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

/**
 * Web driver, backed by SQLite compiled to WebAssembly running inside a dedicated worker.
 *
 * The worker script is served by the web application — see `webApp` — rather than bundled
 * here, because it has to be fetched from a URL the browser can resolve at runtime.
 *
 * Unlike the other platforms there is no driver-level schema handling, so the schema is
 * created explicitly. This is the reason the whole generated API is asynchronous.
 */
class WebDatabaseDriverFactory(
    private val workerUrl: String = DEFAULT_WORKER_URL,
) : DatabaseDriverFactory {
    override suspend fun create(): SqlDriver {
        val driver = WebWorkerDriver(Worker(workerUrl))
        YellowTrackDatabase.Schema.awaitCreate(driver)
        return driver
    }

    companion object {
        const val DEFAULT_WORKER_URL: String = "sqlite.worker.js"
    }
}
