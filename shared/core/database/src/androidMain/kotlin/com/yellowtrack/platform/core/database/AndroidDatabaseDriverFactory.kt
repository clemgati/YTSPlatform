package com.yellowtrack.platform.core.database

import android.content.Context
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * The schema is generated asynchronously so that the web driver can use it; the Android
 * driver applies it synchronously, which [synchronous] adapts.
 */
class AndroidDatabaseDriverFactory(
    private val context: Context,
) : DatabaseDriverFactory {
    override suspend fun create(): SqlDriver =
        AndroidSqliteDriver(
            schema = YellowTrackDatabase.Schema.synchronous(),
            context = context,
            name = DatabaseDriverFactory.DATABASE_NAME,
        )
}
