package com.yellowtrack.platform.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import java.sql.Connection
import javax.sql.DataSource

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

        /**
         * The credentials migrations run under, which are not the ones requests run under.
         *
         * ADR 0009 has the application connect as `yellowtrack_app`, which owns nothing and
         * holds no `CREATE` — that is the whole point, because a role that owns the tables
         * is exempt from their policies. Flyway needs precisely what that role lacks.
         *
         * Two failures follow from using one credential for both, and both of them happen
         * during a deployment rather than in a test. On a fresh database `yellowtrack_app`
         * does not exist yet, because migration V2 is what creates it. On an existing one,
         * the next migration cannot create a table.
         *
         * Falls back to the request credentials when unset, so development — where one
         * account is the owner and there is no separation to make — is unchanged.
         */
        fun forMigrations(): DatabaseConfig {
            val serving = fromEnvironment()

            return serving.copy(
                user = System.getenv("MIGRATION_USER") ?: serving.user,
                password = System.getenv("MIGRATION_PASSWORD") ?: serving.password,
            )
        }
    }
}

/**
 * Flyway over the migrations in `src/main/resources/db/migration`.
 *
 * [allowClean] exists for the tests, which need an empty database to migrate into. It
 * defaults to false so that nothing on the serving path can drop the schema: Flyway
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

/**
 * The pool, and the two ways a request is allowed to reach the database.
 *
 * The distinction between [inStudio] and [unscoped] is the tenant boundary as the
 * application sees it, and it is deliberately awkward to get wrong: there is no method
 * that runs business queries without naming a studio.
 */
class Database(
    private val dataSource: DataSource,
) : AutoCloseable {
    /**
     * Runs [block] inside a transaction scoped to one studio.
     *
     * Two statements do the work. `SET LOCAL ROLE` drops to the role that cannot bypass
     * row level security — necessary because in development the connecting role is a
     * superuser, and a superuser is exempt from every policy in the schema. `set_config`
     * with `is_local = true` sets `app.studio_id` for the length of this transaction, and
     * the policies compare `studio_id` against it.
     *
     * Both are scoped to the transaction, so a pooled connection cannot carry one
     * request's studio into the next one's.
     *
     * `set_config` rather than `SET LOCAL app.studio_id = ...` because it takes the value
     * as a parameter. `SET` does not, and would mean interpolating a studio identifier
     * into SQL text.
     */
    fun <T> inStudio(
        studioId: String,
        block: (Connection) -> T,
    ): T =
        transaction { connection ->
            connection.prepareStatement("SELECT set_config('app.studio_id', ?, true)").use { statement ->
                statement.setString(1, studioId)
                statement.executeQuery().close()
            }
            block(connection)
        }

    /**
     * Runs [block] in a transaction with no studio set.
     *
     * Only for the authentication tables, which are what *establish* which studio a
     * request acts as — a policy keyed on `app.studio_id` cannot guard the lookup that
     * decides what `app.studio_id` should be. ADR 0009 decision 7 explains why this hole
     * exists and why it is narrow.
     *
     * Business tables read through here return nothing, because their policies are
     * fail-closed. That is the intended outcome rather than a limitation.
     */
    fun <T> unscoped(block: (Connection) -> T): T = transaction(block)

    /**
     * Every transaction, scoped or not, drops to the role that cannot bypass row level
     * security before it runs anything.
     *
     * This is what makes [unscoped] safe to have at all. In production the connecting role
     * is already `yellowtrack_app` and this changes nothing; in development it is the
     * developer's own superuser account, which is exempt from every policy in the schema.
     * Without the drop, `unscoped` would read business tables as a superuser and return
     * *every* studio's rows — the opposite of what its name promises, and only on the
     * machines the code is written on.
     *
     * `SET LOCAL`, so a pooled connection cannot carry the role or the studio out of one
     * request and into the next.
     */
    private fun <T> transaction(block: (Connection) -> T): T =
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.createStatement().use { it.execute("SET LOCAL ROLE yellowtrack_app") }
                val result = block(connection)
                connection.commit()
                result
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }

    override fun close() {
        (dataSource as? AutoCloseable)?.close()
    }

    companion object {
        fun pooled(config: DatabaseConfig = DatabaseConfig.fromEnvironment()): Database =
            Database(
                HikariDataSource(
                    HikariConfig().apply {
                        jdbcUrl = config.url
                        username = config.user
                        password = config.password
                        // A photographer-sized workload. Sized to be obviously enough
                        // rather than tuned against a load nobody has measured yet.
                        maximumPoolSize = 10
                        poolName = "yellowtrack"
                    },
                ),
            )
    }
}
