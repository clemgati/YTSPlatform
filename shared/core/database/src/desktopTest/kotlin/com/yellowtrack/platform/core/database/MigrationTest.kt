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

    private fun v3Database(): SqlDriver = snapshotDatabase(version = 3)

    private fun v4Database(): SqlDriver = snapshotDatabase(version = 4)

    private fun v5Database(): SqlDriver = snapshotDatabase(version = 5)

    private fun v6Database(): SqlDriver = snapshotDatabase(version = 6)

    private fun v7Database(): SqlDriver = snapshotDatabase(version = 7)

    private fun v8Database(): SqlDriver = snapshotDatabase(version = 8)

    private fun v9Database(): SqlDriver = snapshotDatabase(version = 9)

    private fun v10Database(): SqlDriver = snapshotDatabase(version = 10)

    private fun v11Database(): SqlDriver = snapshotDatabase(version = 11)

    private fun v12Database(): SqlDriver = snapshotDatabase(version = 12)

    private fun v13Database(): SqlDriver = snapshotDatabase(version = 13)

    private fun v14Database(): SqlDriver = snapshotDatabase(version = 14)

    private fun v15Database(): SqlDriver = snapshotDatabase(version = 15)

    /**
     * The per-studio singletons take the studio's id, so two devices write one row.
     *
     * `studio_profile` and `codb_profile` hold one row per studio and both took a generated
     * id, which was harmless while neither synchronised. It stops being harmless the moment
     * they do: the device that signed up would create one row and the device that signed in
     * another, and the second push would violate the unique index on the server rather than
     * merge with the first.
     *
     * The rewrite has to keep the studio's own details — this is the profile every invoice
     * is built from — so the test checks the row moved rather than that a row exists.
     */
    @Test
    fun `migration 15 keys the per-studio singletons by their studio`() =
        runTest {
            val driver = v15Database()

            driver.exec(
                """
                INSERT INTO studio_profile(id, studio_id, name, tax_number, created_at, updated_at, version, currency)
                VALUES ('generated-id-1', 'studio-1', 'Harbourline Photography', 'GB123456789', 1, 1, 3, 'GBP')
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO codb_profile(id, studio_id, currency, target_annual_salary_minor,
                                         billable_days_per_year, tax_rate_basis_points,
                                         created_at, updated_at, version)
                VALUES ('generated-id-2', 'studio-1', 'GBP', 4000000, 120, 2000, 1, 1, 2)
                """.trimIndent(),
            )

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 15, newVersion = 16)

            assertEquals(
                "studio-1|Harbourline Photography|GB123456789|3",
                driver.scalar(
                    "SELECT id || '|' || name || '|' || tax_number || '|' || version FROM studio_profile",
                ),
                "the profile should be keyed by its studio and otherwise untouched — this is what " +
                    "every invoice is built from",
            )
            assertEquals(
                "studio-1|2",
                driver.scalar("SELECT id || '|' || version FROM codb_profile"),
            )

            driver.close()
        }

    /**
     * The seeded templates take an id both devices agree on; a renamed one keeps its own.
     *
     * Seeding runs once per device, so a generated id gave two devices two full sets of the
     * same four templates. The rename case is the one worth pinning: a studio that has made
     * a template its own must not have it merged with another device's untouched copy.
     */
    @Test
    fun `migration 16 keys the seeded templates, and leaves renamed ones alone`() =
        runTest {
            // From 15, through both new migrations. There is no 16 snapshot: the schema
            // generator only ever emits the current version, so a version that was current
            // only between two commits never gets one.
            val driver = v15Database()

            driver.exec(
                """
                INSERT INTO service_template(id, studio_id, name, service_line,
                                             default_session_duration_min, default_session_count,
                                             created_at, updated_at, version)
                VALUES ('generated-1', 'studio-1', 'Wedding — Full Day', 'Wedding', 600, 1, 1, 1, 1)
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO service_template(id, studio_id, name, service_line,
                                             default_session_duration_min, default_session_count,
                                             created_at, updated_at, version)
                VALUES ('generated-2', 'studio-1', 'Weddings — our way', 'Wedding', 600, 1, 1, 1, 1)
                """.trimIndent(),
            )

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 15, newVersion = 17)

            assertEquals(
                "studio-1:default:Wedding — Full Day",
                driver.scalar("SELECT id FROM service_template WHERE name = 'Wedding — Full Day'"),
                "a seeded template must land on the id every other device derives",
            )
            assertEquals(
                "generated-2",
                driver.scalar("SELECT id FROM service_template WHERE name = 'Weddings — our way'"),
                "a renamed template is the studio's own, and merging it with another device's " +
                    "untouched copy would be the wrong answer",
            )

            driver.close()
        }

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
            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 1, newVersion = 9)

            assertEquals(1L, driver.countOf("client"))
            assertEquals("Long-standing Client", driver.scalar("SELECT account_name FROM client"))
            assertEquals(0L, driver.countOf("invoice"), "the ledger tables from 1 → 2 must still be laid down")
            assertNull(driver.scalar("SELECT latitude FROM session"), "and the 2 → 3 columns must exist")
            assertEquals(0L, driver.countOf("shot"), "and the 3 → 4 table must be laid down")
            assertEquals(0L, driver.countOf("crew_member"), "and the 4 → 5 table too")
            assertEquals(0L, driver.countOf("talent_release"), "and the 5 → 6 table")
            assertEquals(0L, driver.countOf("post_task"), "and the 6 → 7 table")
            assertEquals(0L, driver.countOf("deliverable"), "and the 7 → 8 table")
            assertEquals(0L, driver.countOf("media_copy"), "and the 8 → 9 table")

            driver.close()
        }

    // --- Version three to four: shot lists ----------------------------------------------

    @Test
    fun `a version three database keeps its sessions when shot lists arrive`() =
        runTest {
            val driver = v3Database()

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
                                    starts_at, ends_at, time_zone_id, latitude, longitude,
                                    created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Wedding Day', 'Shoot', 'Confirmed',
                        2000, 3000, 'Europe/London', 50.2, -5.5, 1000, 1000, 1);
                """.trimIndent(),
            )

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 3, newVersion = 4)

            assertEquals(1L, driver.countOf("session"), "the shoot day must survive the upgrade")
            assertEquals(
                "50.2",
                driver.scalar("SELECT CAST(latitude AS TEXT) FROM session"),
                "the coordinate added in 2 → 3 must not be lost by 3 → 4",
            )
            assertEquals(0L, driver.countOf("shot"), "and the new table is there and empty")

            driver.close()
        }

    @Test
    fun `a grouped shot can be written and read back after upgrading`() =
        runTest {
            val driver = v3Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 3, newVersion = 4)

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
                                    starts_at, ends_at, time_zone_id, created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Wedding Day', 'Shoot', 'Confirmed',
                        2000, 3000, 'Europe/London', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO shot(id, studio_id, session_id, description, group_name, people,
                                 position, is_captured, created_at, updated_at, version)
                VALUES ('shot-1', 'studio-1', 'session-1', 'Bride with her grandmother',
                        'Bride''s family', 'Grandma Ruth', 3, 0, 1000, 1000, 1);
                """.trimIndent(),
            )

            assertEquals("Bride's family", driver.scalar("SELECT group_name FROM shot"))
            assertEquals("Grandma Ruth", driver.scalar("SELECT people FROM shot"))

            driver.close()
        }

    // --- Version four to five: crew -----------------------------------------------------

    @Test
    fun `a version four database keeps its shots when crew arrive`() =
        runTest {
            val driver = v4Database()

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
                                    starts_at, ends_at, time_zone_id, created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Wedding Day', 'Shoot', 'Confirmed',
                        2000, 3000, 'Europe/London', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO shot(id, studio_id, session_id, description, group_name,
                                 position, is_captured, created_at, updated_at, version)
                VALUES ('shot-1', 'studio-1', 'session-1', 'Bride with her grandmother',
                        'Bride''s family', 0, 0, 1000, 1000, 1);
                """.trimIndent(),
            )

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 4, newVersion = 5)

            assertEquals(1L, driver.countOf("shot"), "the shot list from 3 → 4 must survive 4 → 5")
            assertEquals("Bride's family", driver.scalar("SELECT group_name FROM shot"))
            assertEquals(0L, driver.countOf("crew_member"), "and the new table is there and empty")

            driver.close()
        }

    @Test
    fun `a crew member with their own call time survives a round trip`() =
        runTest {
            val driver = v4Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 4, newVersion = 5)

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
                                    starts_at, ends_at, time_zone_id, created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Wedding Day', 'Shoot', 'Confirmed',
                        2000, 3000, 'Europe/London', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO crew_member(id, studio_id, session_id, name, role, phone, call_time,
                                        created_at, updated_at, version)
                VALUES ('crew-1', 'studio-1', 'session-1', 'Priya Shah', 'MakeUp',
                        '07700 900123', 1500, 1000, 1000, 1);
                """.trimIndent(),
            )

            assertEquals("Priya Shah", driver.scalar("SELECT name FROM crew_member"))
            assertEquals("MakeUp", driver.scalar("SELECT role FROM crew_member"))
            assertEquals("1500", driver.scalar("SELECT CAST(call_time AS TEXT) FROM crew_member"))

            driver.close()
        }

    // --- Version five to six: talent releases -------------------------------------------

    @Test
    fun `a version five database keeps its crew when releases arrive`() =
        runTest {
            val driver = v5Database()

            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Harbourline Coffee', 'Company', '[]', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                    created_at, updated_at, version)
                VALUES ('project-1', 'studio-1', 'client-1', 'Autumn Brand Shoot', 'Branding',
                        'Booked', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO session(id, studio_id, project_id, title, kind, status,
                                    starts_at, ends_at, time_zone_id, created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Shoot day', 'Shoot', 'Confirmed',
                        2000, 3000, 'Europe/London', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO crew_member(id, studio_id, session_id, name, role,
                                        created_at, updated_at, version)
                VALUES ('crew-1', 'studio-1', 'session-1', 'Priya Shah', 'MakeUp', 1000, 1000, 1);
                """.trimIndent(),
            )

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 5, newVersion = 6)

            assertEquals(1L, driver.countOf("crew_member"), "the crew from 4 → 5 must survive 5 → 6")
            assertEquals("Priya Shah", driver.scalar("SELECT name FROM crew_member"))
            assertEquals(0L, driver.countOf("talent_release"), "and the new table is there and empty")

            driver.close()
        }

    @Test
    fun `a refused release is stored as a refusal rather than a missing row`() =
        runTest {
            val driver = v5Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 5, newVersion = 6)

            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Harbourline Coffee', 'Company', '[]', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                    created_at, updated_at, version)
                VALUES ('project-1', 'studio-1', 'client-1', 'Autumn Brand Shoot', 'Branding',
                        'Booked', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO session(id, studio_id, project_id, title, kind, status,
                                    starts_at, ends_at, time_zone_id, created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Shoot day', 'Shoot', 'Confirmed',
                        2000, 3000, 'Europe/London', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO talent_release(id, studio_id, session_id, person_name, kind, status,
                                           created_at, updated_at, version)
                VALUES ('release-1', 'studio-1', 'session-1', 'Ada Okafor', 'Adult', 'Refused',
                        1000, 1000, 1);
                """.trimIndent(),
            )

            assertEquals(
                "Refused",
                driver.scalar("SELECT status FROM talent_release"),
                "someone who said no is not the same as someone who was never asked",
            )

            driver.close()
        }

    // --- Version six to seven: post-production ------------------------------------------

    @Test
    fun `a version six database keeps its releases when post-production arrives`() =
        runTest {
            val driver = v6Database()

            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Harbourline Coffee', 'Company', '[]', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                    created_at, updated_at, version)
                VALUES ('project-1', 'studio-1', 'client-1', 'Autumn Brand Shoot', 'Branding',
                        'Booked', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO session(id, studio_id, project_id, title, kind, status,
                                    starts_at, ends_at, time_zone_id, created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Shoot day', 'Shoot', 'Completed',
                        2000, 3000, 'Europe/London', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO talent_release(id, studio_id, session_id, person_name, kind, status,
                                           created_at, updated_at, version)
                VALUES ('release-1', 'studio-1', 'session-1', 'Ada Okafor', 'Adult', 'Signed',
                        1000, 1000, 1);
                """.trimIndent(),
            )

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 6, newVersion = 7)

            assertEquals(1L, driver.countOf("talent_release"), "the releases from 5 → 6 must survive 6 → 7")
            assertEquals(0L, driver.countOf("post_task"), "and the new table is there and empty")

            driver.close()
        }

    @Test
    fun `estimated and actual hours survive a round trip as fractions`() =
        runTest {
            val driver = v6Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 6, newVersion = 7)

            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Harbourline Coffee', 'Company', '[]', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                    created_at, updated_at, version)
                VALUES ('project-1', 'studio-1', 'client-1', 'Autumn Brand Shoot', 'Branding',
                        'Booked', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO post_task(id, studio_id, project_id, name, kind, status,
                                      estimated_hours, actual_hours, created_at, updated_at, version)
                VALUES ('task-1', 'studio-1', 'project-1', 'Cull', 'Cull', 'Done',
                        2.5, 4.25, 1000, 1000, 1);
                """.trimIndent(),
            )

            // Half hours are the normal unit of this work, so the column must not round.
            assertEquals("2.5", driver.scalar("SELECT CAST(estimated_hours AS TEXT) FROM post_task"))
            assertEquals("4.25", driver.scalar("SELECT CAST(actual_hours AS TEXT) FROM post_task"))

            driver.close()
        }

    // --- Version seven to eight: deliverables -------------------------------------------

    @Test
    fun `a version seven database keeps its post-production when deliverables arrive`() =
        runTest {
            val driver = v7Database()

            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Harbourline Coffee', 'Company', '[]', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                    created_at, updated_at, version)
                VALUES ('project-1', 'studio-1', 'client-1', 'Autumn Brand Shoot', 'Branding',
                        'Booked', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO post_task(id, studio_id, project_id, name, kind, status,
                                      estimated_hours, actual_hours, created_at, updated_at, version)
                VALUES ('task-1', 'studio-1', 'project-1', 'Cull', 'Cull', 'Done',
                        2.5, 4.25, 1000, 1000, 1);
                """.trimIndent(),
            )

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 7, newVersion = 8)

            assertEquals(1L, driver.countOf("post_task"), "the hours from 6 → 7 must survive 7 → 8")
            assertEquals("4.25", driver.scalar("SELECT CAST(actual_hours AS TEXT) FROM post_task"))
            assertEquals(0L, driver.countOf("deliverable"), "and the new table is there and empty")

            driver.close()
        }

    @Test
    fun `revision rounds default to none rather than to nothing`() =
        runTest {
            val driver = v7Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 7, newVersion = 8)

            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Harbourline Coffee', 'Company', '[]', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                    created_at, updated_at, version)
                VALUES ('project-1', 'studio-1', 'client-1', 'Autumn Brand Shoot', 'Branding',
                        'Booked', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO deliverable(id, studio_id, project_id, name, kind, status,
                                        created_at, updated_at, version)
                VALUES ('deliverable-1', 'studio-1', 'project-1', 'Full gallery', 'Gallery',
                        'NotStarted', 1000, 1000, 1);
                """.trimIndent(),
            )

            assertEquals(
                "0",
                driver.scalar("SELECT CAST(revisions_used AS TEXT) FROM deliverable"),
                "a null count would make every comparison against the contract meaningless",
            )

            driver.close()
        }

    // --- Version eight to nine: where the files are -------------------------------------

    @Test
    fun `a version eight database keeps its deliverables when file copies arrive`() =
        runTest {
            val driver = v8Database()

            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Harbourline Coffee', 'Company', '[]', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                    created_at, updated_at, version)
                VALUES ('project-1', 'studio-1', 'client-1', 'Autumn Brand Shoot', 'Branding',
                        'Booked', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO session(id, studio_id, project_id, title, kind, status,
                                    starts_at, ends_at, time_zone_id, created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Shoot day', 'Shoot', 'Completed',
                        2000, 3000, 'Europe/London', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO deliverable(id, studio_id, project_id, name, kind, status,
                                        revisions_used, created_at, updated_at, version)
                VALUES ('deliverable-1', 'studio-1', 'project-1', 'Full gallery', 'Gallery',
                        'Delivered', 2, 1000, 1000, 1);
                """.trimIndent(),
            )

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 8, newVersion = 9)

            assertEquals(1L, driver.countOf("deliverable"), "the deliverables from 7 → 8 must survive 8 → 9")
            assertEquals("2", driver.scalar("SELECT CAST(revisions_used AS TEXT) FROM deliverable"))
            assertEquals(0L, driver.countOf("media_copy"), "and the new table is there and empty")

            driver.close()
        }

    @Test
    fun `a copy is recorded as unverified until someone opens it`() =
        runTest {
            val driver = v8Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 8, newVersion = 9)

            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Harbourline Coffee', 'Company', '[]', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                    created_at, updated_at, version)
                VALUES ('project-1', 'studio-1', 'client-1', 'Autumn Brand Shoot', 'Branding',
                        'Booked', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO session(id, studio_id, project_id, title, kind, status,
                                    starts_at, ends_at, time_zone_id, created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Shoot day', 'Shoot', 'Completed',
                        2000, 3000, 'Europe/London', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO media_copy(id, studio_id, session_id, volume_name, kind, is_offsite,
                                       copied_at, created_at, updated_at, version)
                VALUES ('copy-1', 'studio-1', 'session-1', 'Red Samsung T7', 'ExternalDrive', 0,
                        2500, 1000, 1000, 1);
                """.trimIndent(),
            )

            assertEquals("0", driver.scalar("SELECT CAST(is_offsite AS TEXT) FROM media_copy"))
            assertNull(
                driver.scalar("SELECT verified_at FROM media_copy"),
                "a copy just made has not been opened and read back",
            )

            driver.close()
        }

    // --- Version nine to ten: what the studio owns --------------------------------------

    @Test
    fun `a version nine database keeps its file copies when gear arrives`() =
        runTest {
            val driver = v9Database()

            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Harbourline Coffee', 'Company', '[]', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                    created_at, updated_at, version)
                VALUES ('project-1', 'studio-1', 'client-1', 'Autumn Brand Shoot', 'Branding',
                        'Booked', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO session(id, studio_id, project_id, title, kind, status,
                                    starts_at, ends_at, time_zone_id, created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Shoot day', 'Shoot', 'Completed',
                        2000, 3000, 'Europe/London', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO media_copy(id, studio_id, session_id, volume_name, kind, is_offsite,
                                       copied_at, created_at, updated_at, version)
                VALUES ('copy-1', 'studio-1', 'session-1', 'Red Samsung T7', 'ExternalDrive', 0,
                        2500, 1000, 1000, 1);
                """.trimIndent(),
            )

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 9, newVersion = 10)

            assertEquals(1L, driver.countOf("media_copy"), "the copies from 8 → 9 must survive 9 → 10")
            assertEquals(0L, driver.countOf("gear_item"), "and the new tables are there and empty")
            assertEquals(0L, driver.countOf("packing_entry"))
            assertEquals(0L, driver.countOf("lighting_recipe"))

            driver.close()
        }

    @Test
    fun `gear arrives owned and unpacked rather than in an unknown state`() =
        runTest {
            val driver = v9Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 9, newVersion = 10)

            driver.exec(
                """
                INSERT INTO gear_item(id, studio_id, name, category, status,
                                      created_at, updated_at, version)
                VALUES ('gear-1', 'studio-1', 'Canon R5 body', 'Camera', 'InService', 1000, 1000, 1);
                """.trimIndent(),
            )

            assertNull(
                driver.scalar("SELECT serial_number FROM gear_item"),
                "plenty of gear has no serial, and inventing one is worse than recording none",
            )
            assertNull(
                driver.scalar("SELECT purchase_price_minor FROM gear_item"),
                "an unknown price must stay unknown rather than read as free",
            )

            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Harbourline Coffee', 'Company', '[]', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                    created_at, updated_at, version)
                VALUES ('project-1', 'studio-1', 'client-1', 'Autumn Brand Shoot', 'Branding',
                        'Booked', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO session(id, studio_id, project_id, title, kind, status,
                                    starts_at, ends_at, time_zone_id, created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Shoot day', 'Shoot', 'Booked',
                        2000, 3000, 'Europe/London', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO packing_entry(id, studio_id, session_id, gear_item_id,
                                          created_at, updated_at, version)
                VALUES ('pack-1', 'studio-1', 'session-1', 'gear-1', 1000, 1000, 1);
                """.trimIndent(),
            )

            assertEquals(
                "0",
                driver.scalar("SELECT CAST(is_packed AS TEXT) FROM packing_entry"),
                "adding something to the list is not the same as putting it in the van",
            )
            assertEquals(
                "0",
                driver.scalar("SELECT CAST(is_returned AS TEXT) FROM packing_entry"),
            )

            driver.close()
        }

    @Test
    fun `a recipe with no lights written down yet reads as empty rather than null`() =
        runTest {
            val driver = v9Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 9, newVersion = 10)

            driver.exec(
                """
                INSERT INTO lighting_recipe(id, studio_id, name, created_at, updated_at, version)
                VALUES ('recipe-1', 'studio-1', 'Clamshell headshot', 1000, 1000, 1);
                """.trimIndent(),
            )

            assertEquals(
                "[]",
                driver.scalar("SELECT lights FROM lighting_recipe"),
                "a null would have to be decoded defensively everywhere it is read",
            )

            driver.close()
        }

    // --- Version ten to eleven: who the studio is on paper ------------------------------

    @Test
    fun `a version ten database keeps its gear when the studio profile arrives`() =
        runTest {
            val driver = v10Database()

            driver.exec(
                """
                INSERT INTO gear_item(id, studio_id, name, category, status,
                                      created_at, updated_at, version)
                VALUES ('gear-1', 'studio-1', 'Canon R5 body', 'Camera', 'InService', 1000, 1000, 1);
                """.trimIndent(),
            )

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 10, newVersion = 11)

            assertEquals(1L, driver.countOf("gear_item"), "the gear from 9 → 10 must survive 10 → 11")
            assertEquals(0L, driver.countOf("studio_profile"), "and the new table is there and empty")

            driver.close()
        }

    @Test
    fun `a studio cannot end up with two profiles and two names on two documents`() =
        runTest {
            val driver = v10Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 10, newVersion = 11)

            driver.exec(
                """
                INSERT INTO studio_profile(id, studio_id, name, created_at, updated_at, version)
                VALUES ('profile-1', 'studio-1', 'Yellow Track Studios', 1000, 1000, 1);
                """.trimIndent(),
            )

            val second =
                runCatching {
                    driver.exec(
                        """
                        INSERT INTO studio_profile(id, studio_id, name, created_at, updated_at, version)
                        VALUES ('profile-2', 'studio-1', 'Something Else', 1000, 1000, 1);
                        """.trimIndent(),
                    )
                }

            assertTrue(
                second.isFailure,
                "two profiles for one studio would put two different names on two invoices",
            )
            assertEquals(1L, driver.countOf("studio_profile"))

            driver.close()
        }

    @Test
    fun `everything but the name is optional, because most of it is filled in later`() =
        runTest {
            val driver = v10Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 10, newVersion = 11)

            driver.exec(
                """
                INSERT INTO studio_profile(id, studio_id, name, created_at, updated_at, version)
                VALUES ('profile-1', 'studio-1', 'Yellow Track Studios', 1000, 1000, 1);
                """.trimIndent(),
            )

            assertNull(driver.scalar("SELECT tax_number FROM studio_profile"))
            assertNull(
                driver.scalar("SELECT payment_instructions FROM studio_profile"),
                "an empty string and a missing value would be two states meaning one thing",
            )

            driver.close()
        }

    // --- Version eleven to twelve: what the studio charges in ---------------------------

    @Test
    fun `a studio that never chose a currency is charging in dollars, not in nothing`() =
        runTest {
            val driver = v11Database()

            driver.exec(
                """
                INSERT INTO studio_profile(id, studio_id, name, created_at, updated_at, version)
                VALUES ('profile-1', 'studio-1', 'Yellow Track Studios', 1000, 1000, 1);
                """.trimIndent(),
            )

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 11, newVersion = 12)

            assertEquals(
                "USD",
                driver.scalar("SELECT currency FROM studio_profile"),
                "a null currency would have to be handled at every place money is rendered",
            )
            assertEquals(
                "Yellow Track Studios",
                driver.scalar("SELECT name FROM studio_profile"),
                "the details from 10 → 11 must survive 11 → 12",
            )

            driver.close()
        }

    @Test
    fun `a studio can charge in something other than dollars`() =
        runTest {
            val driver = v11Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 11, newVersion = 12)

            driver.exec(
                """
                INSERT INTO studio_profile(id, studio_id, name, currency, created_at, updated_at, version)
                VALUES ('profile-1', 'studio-1', 'Yellow Track Studios', 'GBP', 1000, 1000, 1);
                """.trimIndent(),
            )

            assertEquals("GBP", driver.scalar("SELECT currency FROM studio_profile"))

            driver.close()
        }

    // --- Version twelve to thirteen: the studio's drives ---------------------------------

    @Test
    fun `copies recorded before the register keep working and keep their names`() =
        runTest {
            val driver = v12Database()

            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Harbourline Coffee', 'Company', '[]', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                    created_at, updated_at, version)
                VALUES ('project-1', 'studio-1', 'client-1', 'Autumn Brand Shoot', 'Branding',
                        'Booked', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO session(id, studio_id, project_id, title, kind, status,
                                    starts_at, ends_at, time_zone_id, created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Shoot day', 'Shoot', 'Completed',
                        2000, 3000, 'Europe/London', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO media_copy(id, studio_id, session_id, volume_name, kind, is_offsite,
                                       copied_at, created_at, updated_at, version)
                VALUES ('copy-1', 'studio-1', 'session-1', 'Red Samsung T7', 'ExternalDrive', 0,
                        2500, 1000, 1000, 1);
                """.trimIndent(),
            )

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 12, newVersion = 13)

            assertEquals(1L, driver.countOf("media_copy"), "the copies from 8 → 9 must survive 12 → 13")
            assertEquals(
                "Red Samsung T7",
                driver.scalar("SELECT volume_name FROM media_copy"),
                "the free-text name is the label of last resort for copies not in the register",
            )
            assertNull(
                driver.scalar("SELECT volume_id FROM media_copy"),
                "not in the register reads as null, not as a broken reference",
            )
            assertEquals(0L, driver.countOf("storage_volume"), "nothing is guessed into the register")

            driver.close()
        }

    @Test
    fun `a drive arrives in use rather than in an unknown state`() =
        runTest {
            val driver = v12Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 12, newVersion = 13)

            driver.exec(
                """
                INSERT INTO storage_volume(id, studio_id, label, kind, created_at, updated_at, version)
                VALUES ('volume-1', 'studio-1', 'Red Samsung T7', 'ExternalDrive', 1000, 1000, 1);
                """.trimIndent(),
            )

            assertEquals(
                "InUse",
                driver.scalar("SELECT status FROM storage_volume"),
                "a drive just added has not failed, and defaulting otherwise would report lost copies",
            )
            assertEquals("0", driver.scalar("SELECT CAST(is_offsite AS TEXT) FROM storage_volume"))
            assertNull(
                driver.scalar("SELECT last_checked_at FROM storage_volume"),
                "nobody has opened it yet, which is different from having opened it at time zero",
            )

            driver.close()
        }

    // --- Version thirteen to fourteen: what was actually found on the drive --------------

    @Test
    fun `copies ticked by hand keep their tick and gain no invented file count`() =
        runTest {
            val driver = v13Database()

            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Harbourline Coffee', 'Company', '[]', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                    created_at, updated_at, version)
                VALUES ('project-1', 'studio-1', 'client-1', 'Autumn Brand Shoot', 'Branding',
                        'Booked', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO session(id, studio_id, project_id, title, kind, status,
                                    starts_at, ends_at, time_zone_id, created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Shoot day', 'Shoot', 'Completed',
                        2000, 3000, 'Europe/London', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO media_copy(id, studio_id, session_id, volume_name, kind, is_offsite,
                                       copied_at, verified_at, created_at, updated_at, version)
                VALUES ('copy-1', 'studio-1', 'session-1', 'Red Samsung T7', 'ExternalDrive', 0,
                        2500, 2600, 1000, 1000, 1);
                """.trimIndent(),
            )

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 13, newVersion = 14)

            assertEquals(
                "2600",
                driver.scalar("SELECT CAST(verified_at AS TEXT) FROM media_copy"),
                "a studio that checked a drive by hand did check it; the tick stands",
            )
            assertNull(
                driver.scalar("SELECT verified_file_count FROM media_copy"),
                "but nothing read it, so there is no count — and a count must never be invented",
            )
            assertNull(
                driver.scalar("SELECT path FROM media_copy"),
                "and nowhere on disk has been named yet",
            )

            driver.close()
        }

    @Test
    fun `a copy can record where it is and what was read there`() =
        runTest {
            val driver = v13Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 13, newVersion = 14)

            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Harbourline Coffee', 'Company', '[]', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                    created_at, updated_at, version)
                VALUES ('project-1', 'studio-1', 'client-1', 'Autumn Brand Shoot', 'Branding',
                        'Booked', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO session(id, studio_id, project_id, title, kind, status,
                                    starts_at, ends_at, time_zone_id, created_at, updated_at, version)
                VALUES ('session-1', 'studio-1', 'project-1', 'Shoot day', 'Shoot', 'Completed',
                        2000, 3000, 'Europe/London', 1000, 1000, 1);
                """.trimIndent(),
            )
            driver.exec(
                """
                INSERT INTO media_copy(id, studio_id, session_id, volume_name, kind, is_offsite,
                                       copied_at, verified_at, path, verified_file_count,
                                       verified_bytes, created_at, updated_at, version)
                VALUES ('copy-1', 'studio-1', 'session-1', 'Red Samsung T7', 'ExternalDrive', 0,
                        2500, 2600, '/Volumes/Red T7/2026/Johnson', 2481, 101203344179,
                        1000, 1000, 1);
                """.trimIndent(),
            )

            assertEquals("2481", driver.scalar("SELECT CAST(verified_file_count AS TEXT) FROM media_copy"))
            assertEquals(
                "101203344179",
                driver.scalar("SELECT CAST(verified_bytes AS TEXT) FROM media_copy"),
                "a wedding runs past what a 32-bit count would hold",
            )

            driver.close()
        }

    // --- Version fourteen to fifteen: where reconciliation keeps its state ---------------

    @Test
    fun `a version fourteen database keeps everything when the sync tables arrive`() =
        runTest {
            val driver = v14Database()

            driver.exec(
                """
                INSERT INTO client(id, studio_id, account_name, account_type, tags,
                                   created_at, updated_at, version)
                VALUES ('client-1', 'studio-1', 'Harbourline Coffee', 'Company', '[]', 1000, 1000, 3);
                """.trimIndent(),
            )

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 14, newVersion = 15)

            assertEquals(1L, driver.countOf("client"), "the studio's accounts must survive the upgrade")
            assertEquals(
                "3",
                driver.scalar("SELECT CAST(version AS TEXT) FROM client"),
                "and their version must not be reset — it is what conflicts are detected with",
            )
            assertEquals(0L, driver.countOf("sync_state"), "the new tables are there and empty")
            assertEquals(0L, driver.countOf("sync_conflict"))

            driver.close()
        }

    @Test
    fun `a device that has never synced starts at the beginning rather than at nothing`() =
        runTest {
            val driver = v14Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 14, newVersion = 15)

            driver.exec("INSERT INTO sync_state(studio_id) VALUES ('studio-1');")

            assertEquals(
                "0",
                driver.scalar("SELECT CAST(last_server_seq AS TEXT) FROM sync_state"),
                "a null cursor would have to be handled at every comparison; zero is before every row",
            )
            assertNull(
                driver.scalar("SELECT last_synced_at FROM sync_state"),
                "never synced is not the same as synced at time zero",
            )

            driver.close()
        }

    @Test
    fun `a conflict keeps both versions, because keeping only the winner is the loss`() =
        runTest {
            val driver = v14Database()

            YellowTrackDatabase.Schema.awaitMigrate(driver, oldVersion = 14, newVersion = 15)

            driver.exec(
                """
                INSERT INTO sync_conflict(id, studio_id, entity_table, entity_id,
                                          losing_payload, winning_payload, detected_at,
                                          created_at, updated_at, version)
                VALUES ('conflict-1', 'studio-1', 'session', 'session-1',
                        '{"title":"Ceremony — 2pm"}', '{"title":"Ceremony — 3pm"}',
                        2000, 2000, 2000, 1);
                """.trimIndent(),
            )

            assertEquals(
                """{"title":"Ceremony — 2pm"}""",
                driver.scalar("SELECT losing_payload FROM sync_conflict"),
                "the discarded version is the whole point: last-write-wins is only defensible " +
                    "while the work it threw away can still be read back",
            )
            assertNull(
                driver.scalar("SELECT resolved_at FROM sync_conflict"),
                "a conflict arrives unresolved rather than pre-dismissed",
            )

            driver.close()
        }

    @Test
    fun `a fresh database reports the current schema version`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

            assertEquals(17L, YellowTrackDatabase.Schema.version, "adding a migration must bump the version")

            driver.close()
        }
}
