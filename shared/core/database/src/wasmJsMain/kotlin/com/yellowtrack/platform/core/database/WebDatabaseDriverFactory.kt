package com.yellowtrack.platform.core.database

import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

/**
 * Web driver, backed by SQLite compiled to WebAssembly running inside a dedicated worker.
 *
 * The worker is ours rather than the stock one from `@cashapp/sqldelight-sqljs-worker`,
 * because that one holds the database in memory and drops it on every reload. See
 * `webApp/src/wasmJsMain/resources/yellowtrack-sqljs.worker.js`; it keeps the same message
 * protocol and adds IndexedDB underneath. The sql.js binaries it loads are copied to the
 * served root by `webApp/webpack.config.d/sqljs.js`.
 *
 * Loaded by absolute path rather than through webpack's `new URL(..., import.meta.url)`
 * marker. That marker exists so a bundler can follow an ES import inside the worker; ours
 * uses `importScripts` of a copied file and needs no bundling, and a plain path is one fewer
 * thing that can move when the build changes.
 */
class WebDatabaseDriverFactory : DatabaseDriverFactory {
    override suspend fun create(): SqlDriver {
        val driver = WebWorkerDriver(createSqlJsWorker())

        // Now that the database survives a reload, this can no longer create the schema
        // unconditionally. It did before because the database was always empty — every load
        // was a first load — and running CREATE TABLE over a restored database fails on the
        // first table that already exists.
        //
        // Every other platform gets this from its driver, which is handed the schema and
        // decides. The web driver has no such hook, so the decision is made here, from the
        // same `user_version` those drivers read.
        val current = driver.userVersion()
        val target = YellowTrackDatabase.Schema.version

        when {
            current == 0L -> {
                YellowTrackDatabase.Schema.awaitCreate(driver)
                driver.setUserVersion(target)
            }

            current < target -> {
                YellowTrackDatabase.Schema.awaitMigrate(driver, current, target)
                driver.setUserVersion(target)
            }

            // A database written by a newer build than this one. Left alone rather than
            // migrated backwards: two tabs on different versions is an ordinary thing during
            // a deploy, and the older one reading a newer schema is survivable where
            // rewriting it is not.
            else -> Unit
        }

        driver.enforceForeignKeys()
        return driver
    }
}

/** Zero on a database this build has never written, which is what SQLite starts at. */
private suspend fun SqlDriver.userVersion(): Long =
    executeQuery(
        identifier = null,
        sql = "PRAGMA user_version;",
        mapper = { cursor ->
            // AsyncValue rather than Value: the web cursor's own next() suspends, and a
            // plain mapper is not a coroutine body.
            QueryResult.AsyncValue {
                if (cursor.next().await()) cursor.getLong(0) ?: 0L else 0L
            }
        },
        parameters = 0,
    ).await()

private suspend fun SqlDriver.setUserVersion(version: Long) {
    execute(identifier = null, sql = "PRAGMA user_version = $version;", parameters = 0).await()
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun createSqlJsWorker(): Worker = js("""new Worker("/yellowtrack-sqljs.worker.js")""")
