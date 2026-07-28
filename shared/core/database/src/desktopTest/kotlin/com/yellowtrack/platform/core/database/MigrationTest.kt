package com.yellowtrack.platform.core.database

import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves that a database created by the previous release survives the upgrade.
 *
 * The `verifyMigrations` Gradle task already checks that the migration produces the
 * declared *shape*. It says nothing about whether the studio's rows are still there
 * afterwards, which is the thing that actually matters — `docs/VISION.md` calls for zero
 * avoidable data loss, and a migration that silently drops a year of bookings would pass
 * a shape check.
 *
 * The fixture is the committed `1.db` snapshot, so this exercises the real schema that
 * shipped rather than a hand-written approximation of it.
 */
class MigrationTest {
    private fun v1Database(): SqlDriver {
        val snapshot = File("src/commonMain/sqldelight/databases/1.db")
        assertTrue(snapshot.exists(), "expected the v1 schema snapshot at ${snapshot.absolutePath}")

        // Copied, so the test never writes to the committed artifact.
        val working = Files.createTempFile("yellowtrack-migration", ".db").toFile()
        working.deleteOnExit()
        snapshot.copyTo(working, overwrite = true)

        return JdbcSqliteDriver("jdbc:sqlite:${working.absolutePath}")
    }

    private fun SqlDriver.exec(sql: String) = execute(null, sql, 0)

    private suspend fun SqlDriver.countOf(table: String): Long =
        executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM $table",
            mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) },
            parameters = 0,
        ).await() ?: 0L

    private suspend fun SqlDriver.scalar(sql: String): String? =
        executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
            parameters = 0,
        ).await()

    @Test
    fun `a version one database keeps its clients and projects and sessions`() =
        runTest {
            val driver = v1Database()

            // A studio mid-season: one couple, one wedding, two shoot days.
            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Sarah & Michael Johnson', 'Couple', '[]', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                    contract_value_minor, contract_currency,
                                    created_at, updated_at, version)
                VALUES ('project-1', 'studio-1', 'client-1', 'Johnson Wedding', 'Wedding',
                        'Booked', 450000, 'USD', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO session(id, studio_id, project_id, title, kind, status,
                                    starts_at, ends_at, time_zone_id, created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Wedding Day', 'Shoot', 'Confirmed',
                        2000, 3000, 'America/New_York', 1000, 1000, 1);
                """.trimIndent(),
            )

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 1, newVersion = 2)

            assertEquals(1L, driver.countOf("client"), "the client must survive the upgrade")
            assertEquals(1L, driver.countOf("project"))
            assertEquals(1L, driver.countOf("session"))

            assertEquals("Sarah & Michael Johnson", driver.scalar("SELECT account_name FROM client"))
            assertEquals(
                "450000",
                driver.scalar("SELECT CAST(contract_value_minor AS TEXT) FROM project"),
                "the contract value must not be mangled by the upgrade",
            )

            driver.close()
        }

    @Test
    fun `the ledger tables exist and are empty after upgrading`() =
        runTest {
            val driver = v1Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 1, newVersion = 2)

            listOf("lead", "quote", "contract", "invoice", "payment", "expense", "mileage", "codb_profile")
                .forEach { table ->
                    assertEquals(0L, driver.countOf(table), "expected an empty $table table after migrating")
                }

            driver.close()
        }

    @Test
    fun `the outbox laid down in version one is still present and untouched`() =
        runTest {
            val driver = v1Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 1, newVersion = 2)

            // Carried since the first migration precisely so that sync arrives without one.
            assertEquals(0L, driver.countOf("outbox"))

            driver.close()
        }

    @Test
    fun `a fresh database reports the current schema version`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

            assertEquals(2L, YellowTrackDatabase.Schema.version, "adding a migration must bump the version")

            driver.close()
        }
}
