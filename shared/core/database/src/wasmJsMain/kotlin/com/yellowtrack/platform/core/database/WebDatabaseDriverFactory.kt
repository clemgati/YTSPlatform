package com.yellowtrack.platform.core.database

import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

/**
 * Web driver, backed by SQLite compiled to WebAssembly running inside a dedicated worker.
 *
 * The worker script and its sql.js dependency are pulled from npm (see this module's
 * build script) and bundled by webpack. The `new URL(..., import.meta.url)` form is the
 * marker webpack looks for to emit the worker as its own chunk, so [createSqlJsWorker]
 * must stay a single JS expression. The sql-wasm.wasm binary the worker fetches at
 * runtime is copied to the served root by `webApp/webpack.config.d/sqljs.js`.
 *
 * Unlike the other platforms there is no driver-level schema handling, so the schema is
 * created explicitly. This is the reason the whole generated API is asynchronous.
 */
class WebDatabaseDriverFactory : DatabaseDriverFactory {
    override suspend fun create(): SqlDriver {
        val driver = WebWorkerDriver(createSqlJsWorker())
        YellowTrackDatabase.Schema.awaitCreate(driver)
        return driver
    }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun createSqlJsWorker(): Worker =
    js("""new Worker(new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url))""")
