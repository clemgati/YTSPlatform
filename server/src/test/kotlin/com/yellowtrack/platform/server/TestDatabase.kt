package com.yellowtrack.platform.server

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * The one Postgres the server tests share, cleaned and migrated once per JVM.
 *
 * Shared rather than per-class because migrating is the slow part and because two classes
 * each cleaning the database would wipe the other's fixtures. Tests that write rows are
 * expected to use identifiers of their own rather than to assume an empty table.
 *
 * Everything here fails rather than skips when there is no database. A test that quietly
 * does not run still reports green, and the properties these tests hold — that one studio
 * cannot read another's rows — are not ones to be green about by default.
 */
object TestDatabase {
    val config: DatabaseConfig =
        DatabaseConfig(
            url =
                System.getenv("YELLOWTRACK_TEST_DB_URL")
                    ?: "jdbc:postgresql://localhost:5432/yellowtrack_test",
            user =
                System.getenv("YELLOWTRACK_TEST_DB_USER")
                    ?: System.getProperty("user.name")
                    ?: "postgres",
            password = System.getenv("YELLOWTRACK_TEST_DB_PASSWORD") ?: "",
        )

    /**
     * A pool connecting as the owner. The server connects as `yellowtrack_app` instead —
     * `Database` issues `SET LOCAL ROLE` per transaction, so the policies apply either way,
     * and tests that care about the distinction set the role themselves.
     */
    val database: Database by lazy {
        ensureMigrated()
        Database.pooled(config)
    }

    /** A raw connection, already migrated. Used by tests that need to act as the owner. */
    fun connection(): Connection {
        ensureMigrated()
        return open()
    }

    private val migrated: Boolean by lazy {
        // Proves the migrations apply, rather than that some database somewhere has the
        // right shape.
        open().close()
        flyway(config, allowClean = true).run {
            clean()
            migrate()
        }
        true
    }

    private fun ensureMigrated() {
        check(migrated)
    }

    private fun open(): Connection =
        try {
            DriverManager.getConnection(config.url, config.user, config.password)
        } catch (error: SQLException) {
            throw AssertionError(
                """
                Could not reach Postgres at ${config.url} as ${config.user}.

                The server tests compare the two schemas and prove that one studio cannot read
                another's rows. Neither is possible without a database, and both fail rather than
                skip on purpose: a check that quietly does not run still looks green.

                    brew install postgresql@18
                    brew services start postgresql@18
                    createdb yellowtrack_test

                Override with YELLOWTRACK_TEST_DB_URL, YELLOWTRACK_TEST_DB_USER and
                YELLOWTRACK_TEST_DB_PASSWORD.
                """.trimIndent(),
                error,
            )
        }
}
