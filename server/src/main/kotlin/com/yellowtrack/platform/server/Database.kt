package com.yellowtrack.platform.server

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

/**
 * Where Postgres is, and who connects to it.
 *
 * Read from the environment rather than a checked-in file, because the production
 * credential must never be in the repository and a default that happens to work in
 * production is how it ends up there. The defaults point at a local development database
 * and are useless anywhere else.
 */
data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
) {
    companion object {
        /**
         * The development default is Postgres on the loopback address, owned by the
         * account running the process — which is what `brew install postgresql@18`
         * produces. See `docs/CONTRIBUTING.md`.
         */
        fun fromEnvironment(): DatabaseConfig =
            DatabaseConfig(
                url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/yellowtrack_dev",
                user = System.getenv("DATABASE_USER") ?: System.getProperty("user.name") ?: "postgres",
                password = System.getenv("DATABASE_PASSWORD") ?: "",
            )
    }
}

/**
 * Flyway over the migrations in `src/main/resources/db/migration`.
 *
 * [allowClean] exists for the drift test, which needs an empty database to migrate into.
 * It defaults to false so that nothing on the serving path can drop the schema: Flyway
 * disables `clean` by default for exactly this reason, and re-enabling it is the sort of
 * thing that should be visible at the call site.
 */
internal fun flyway(
    config: DatabaseConfig,
    allowClean: Boolean = false,
): Flyway =
    Flyway
        .configure()
        .dataSource(config.url, config.user, config.password)
        .cleanDisabled(!allowClean)
        .load()

/** Brings the database up to the schema this build expects. */
fun migrate(config: DatabaseConfig = DatabaseConfig.fromEnvironment()): MigrateResult = flyway(config).migrate()
