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
import kotlin.test.assertNull
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
    private fun v1Database(): SqlDriver = snapshotDatabase(version = 1)

    private fun v2Database(): SqlDriver = snapshotDatabase(version = 2)

    /** A copy of the committed snapshot for [version], so the real shipped schema is used. */
    private fun snapshotDatabase(version: Int): SqlDriver {
        val snapshot = File("src/commonMain/sqldelight/databases/$version.db")
        assertTrue(snapshot.exists(), "expected the v$version schema snapshot at ${snapshot.absolutePath}")

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

    // --- Version two to three: where a session happens ----------------------------------

    @Test
    fun `a version two database keeps its sessions when coordinates arrive`() =
        runTest {
            val driver = v2Database()

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
                                    created_at, updated_at, version)
                VALUES ('project-1', 'studio-1', 'client-1', 'Johnson Wedding', 'Wedding',
                        'Booked', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO session(id, studio_id, project_id, title, kind, status,
                                    starts_at, ends_at, time_zone_id, location_name,
                                    created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Wedding Day', 'Shoot', 'Confirmed',
                        2000, 3000, 'Europe/London', 'Thornbury Manor', 1000, 1000, 1);
                """.trimIndent(),
            )

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 2, newVersion = 3)

            assertEquals(1L, driver.countOf("session"), "a booked shoot day must survive the upgrade")
            assertEquals("Wedding Day", driver.scalar("SELECT title FROM session"))
            assertEquals(
                "Thornbury Manor",
                driver.scalar("SELECT location_name FROM session"),
                "the place it was already at must not be lost to the column that describes where that is",
            )
            assertNull(
                driver.scalar("SELECT latitude FROM session"),
                "a session recorded before coordinates existed has none, rather than a default one",
            )

            driver.close()
        }

    @Test
    fun `a coordinate can be written and read back after upgrading`() =
        runTest {
            val driver = v2Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 2, newVersion = 3)

            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Ada Okafor', 'Individual', '[]', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                    created_at, updated_at, version)
                VALUES ('project-1', 'studio-1', 'client-1', 'Coastal Shoot', 'Portrait',
                        'Booked', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO session(id, studio_id, project_id, title, kind, status,
                                    starts_at, ends_at, time_zone_id, latitude, longitude,
                                    created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Cliff top', 'Shoot', 'Scheduled',
                        2000, 3000, 'Europe/London', 50.2, -5.5, 1000, 1000, 1);
                """.trimIndent(),
            )

            assertEquals("50.2", driver.scalar("SELECT CAST(latitude AS TEXT) FROM session"))
            assertEquals("-5.5", driver.scalar("SELECT CAST(longitude AS TEXT) FROM session"))

            driver.close()
        }

    @Test
    fun `a version one database can be brought all the way to the current schema`() =
        runTest {
            val driver = v1Database()

            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Long-standing Client', 'Company', '[]', 1000, 1000, 1);
                """.trimIndent(),
            )

            // A studio that skipped a release upgrades through every step, not just the last.
            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 1, newVersion = 3)

            assertEquals(1L, driver.countOf("client"))
            assertEquals("Long-standing Client", driver.scalar("SELECT account_name FROM client"))
            assertEquals(0L, driver.countOf("invoice"), "the ledger tables from 1 → 2 must still be laid down")
            assertNull(driver.scalar("SELECT latitude FROM session"), "and the 2 → 3 columns must exist")

            driver.close()
        }

    @Test
    fun `a fresh database reports the current schema version`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

            assertEquals(3L, YellowTrackDatabase.Schema.version, "adding a migration must bump the version")

            driver.close()
        }
}
