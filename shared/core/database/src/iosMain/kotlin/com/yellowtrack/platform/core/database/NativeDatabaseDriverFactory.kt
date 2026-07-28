package com.yellowtrack.platform.core.database

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

class NativeDatabaseDriverFactory : DatabaseDriverFactory {
    override suspend fun create(): SqlDriver =
        NativeSqliteDriver(
            schema = YellowTrackDatabase.Schema.synchronous(),
            name = DatabaseDriverFactory.DATABASE_NAME,
        )
}
