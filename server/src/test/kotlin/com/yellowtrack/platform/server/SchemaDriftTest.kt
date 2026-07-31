package com.yellowtrack.platform.server

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Proves that the server's Postgres schema still mirrors the clients' SQLite one.
 *
 * ADR 0007 chose a Kotlin server so that a change to a shared entity would be a compile
 * error rather than a runtime surprise. The compiler does that for `core:model`; nothing
 * does it for the two schemas, which are written twice in two dialects and kept in step by
 * hand. ADR 0008 named this as a cost of the design — "the two schemas are no longer
 * mirror images and the mapping has to be maintained deliberately" — and a mapping
 * maintained by vigilance is a mapping that drifts.
 *
 * So this test is the compiler for the half the compiler cannot see. It reads the
 * committed SQLDelight snapshot as an ordinary SQLite file, applies the real Flyway
 * migrations to a real Postgres, and compares them column by column.
 *
 * It reads the *highest-numbered* snapshot rather than a pinned one, so adding a
 * SQLDelight migration widens what is compared automatically and fails here until the
 * server side catches up. Pinning the version would make this test agree with itself
 * forever while the schemas parted company.
 *
 * ## Running it
 *
 * Needs a Postgres. Locally that is `brew install postgresql@18`, `brew services start
 * postgresql@18`, `createdb yellowtrack_test`; in CI it is the service container in
 * `.github/workflows/ci.yml`. It fails rather than skips when there is none, because a
 * drift test that quietly does not run is worse than no drift test: it reports a safety
 * property it is not delivering.
 */
class SchemaDriftTest {
    // -- What the two sides are allowed to disagree about --------------------------------
    //
    // Everything in this section is a deliberate divergence. Anything not listed here is a
    // bug, and the assertions below say so.

    /**
     * Tables that exist on the device and have no business on the server.
     *
     * `outbox` is the queue of local mutations waiting to be uploaded (ADR 0008 decision
     * 6). The server is what they are uploaded *to*; it has no outbox of its own.
     */
    private val deviceOnlyTables = setOf("outbox")

    /**
     * The one column the server adds to every synced table.
     *
     * Postgres assigns it, no client writes it, and it is the cursor every pull ranges
     * over — ADR 0008 decision 1.
     */
    private val serverOnlyColumn = "server_seq"

    /**
     * The SQLite-to-Postgres type mapping, in full.
     *
     * `INTEGER` carries three unrelated meanings on the device — instants, counts, and
     * booleans — because SQLite has no boolean type. The `is_` prefix is what tells them
     * apart, and it is a rule rather than a hand-kept list of column names so that a new
     * boolean column is covered the day it is written.
     */
    private fun expectedPostgresType(
        table: String,
        column: Column,
    ): String =
        when (column.type.uppercase()) {
            "TEXT" -> "text"
            "REAL" -> "double precision"
            "INTEGER" -> if (column.name.startsWith("is_")) "boolean" else "bigint"
            else -> fail("$table.${column.name} has SQLite type ${column.type}, which the mapping does not cover")
        }

    // -- The comparison ------------------------------------------------------------------

    @Test
    fun `every table the device has, the server has too`() {
        val expected = (sqlite.keys - deviceOnlyTables).sorted()
        val actual = postgres.keys.sorted()

        assertEquals(
            expected,
            actual,
            "the server schema and the device schema disagree about which tables exist; " +
                "a table on only one side is an entity that cannot sync",
        )
    }

    @Test
    fun `every table holds the same columns on both sides`() {
        (sqlite.keys - deviceOnlyTables).sorted().forEach { table ->
            val onDevice = sqlite.getValue(table).map { it.name }.sorted()
            val onServer = postgres.getValue(table).map { it.name }.sorted()

            assertEquals(
                (onDevice + serverOnlyColumn).sorted(),
                onServer,
                "the columns of `$table` have drifted apart",
            )
        }
    }

    @Test
    fun `every column carries the mapped type and the same nullability`() {
        (sqlite.keys - deviceOnlyTables).sorted().forEach { table ->
            val onServer = postgres.getValue(table).associateBy { it.name }

            sqlite.getValue(table).forEach { deviceColumn ->
                val serverColumn =
                    onServer[deviceColumn.name]
                        ?: fail("`$table.${deviceColumn.name}` is missing from the server schema")

                assertEquals(
                    expectedPostgresType(table, deviceColumn),
                    serverColumn.type,
                    "`$table.${deviceColumn.name}` is ${deviceColumn.type} on the device, " +
                        "so the mapping requires it to be ${expectedPostgresType(table, deviceColumn)} on the server",
                )

                assertEquals(
                    deviceColumn.isNullable,
                    serverColumn.isNullable,
                    "`$table.${deviceColumn.name}` is ${nullability(deviceColumn.isNullable)} on the device " +
                        "and ${nullability(serverColumn.isNullable)} on the server; a row that is legal on one " +
                        "side and rejected on the other cannot round-trip",
                )
            }
        }
    }

    @Test
    fun `the sync cursor is the only thing the server adds`() {
        postgres.forEach { (table, columns) ->
            val onDevice = sqlite.getValue(table).map { it.name }.toSet()
            val added = columns.map { it.name }.toSet() - onDevice

            assertEquals(
                setOf(serverOnlyColumn),
                added,
                "`$table` has server-only columns beyond the sync cursor; every one of them is a " +
                    "field the device cannot see and cannot round-trip",
            )
        }
    }

    @Test
    fun `the sync cursor is a bigint the client can hold, and is never null`() {
        postgres.forEach { (table, columns) ->
            val cursor =
                columns.singleOrNull { it.name == serverOnlyColumn }
                    ?: fail("`$table` has no $serverOnlyColumn, so nothing would ever pull its rows")

            assertEquals("bigint", cursor.type, "`$table.$serverOnlyColumn` must be a bigint")
            assertTrue(
                !cursor.isNullable,
                "a null `$table.$serverOnlyColumn` would fall outside every `server_seq > ?` pull, " +
                    "so the row would exist on the server and reach no device",
            )
        }
    }

    // -- The mechanism behind the cursor --------------------------------------------------

    @Test
    fun `every synced table advances its cursor on update as well as on insert`() {
        val triggered = triggerEvents()

        postgres.keys.sorted().forEach { table ->
            assertEquals(
                setOf("INSERT", "UPDATE"),
                triggered[table] ?: emptySet(),
                "`$table` must reassign $serverOnlyColumn on both. A row whose cursor stands still " +
                    "when it changes sits behind every cursor that has already passed it, and is never " +
                    "pulled again — which is the silent loss ADR 0008 is written against",
            )
        }
    }

    @Test
    fun `an update moves a row's cursor past every row written before it`() {
        connection().use { db ->
            db.createStatement().use { statement ->
                statement.execute(
                    """
                    INSERT INTO client(id, studio_id, account_name, account_type, created_at, updated_at)
                    VALUES ('drift-1', 'studio-1', 'Ada Okafor', 'Individual', 1000, 1000),
                           ('drift-2', 'studio-1', 'Harbourline Coffee', 'Company', 1000, 1000)
                    """.trimIndent(),
                )
            }

            val secondInsert = cursorOf(db, "drift-2")

            db.createStatement().use { statement ->
                statement.execute("UPDATE client SET account_name = 'Ada Okafor-Bell' WHERE id = 'drift-1'")
            }

            assertTrue(
                cursorOf(db, "drift-1") > secondInsert,
                "editing the first row must put it after the second, or a device holding the cursor " +
                    "from the second row would never learn about the edit",
            )

            db.createStatement().use { it.execute("DELETE FROM client WHERE id IN ('drift-1', 'drift-2')") }
        }
    }

    private fun cursorOf(
        db: Connection,
        id: String,
    ): Long =
        db.prepareStatement("SELECT server_seq FROM client WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { rows ->
                assertTrue(rows.next(), "expected a client row with id $id")
                rows.getLong(1)
            }
        }

    private fun triggerEvents(): Map<String, Set<String>> =
        connection().use { db ->
            db.createStatement().use { statement ->
                statement
                    .executeQuery(
                        """
                        SELECT event_object_table, event_manipulation
                        FROM information_schema.triggers
                        WHERE trigger_schema = current_schema()
                        """.trimIndent(),
                    ).use { rows ->
                        buildMap<String, MutableSet<String>> {
                            while (rows.next()) {
                                getOrPut(rows.getString(1)) { mutableSetOf() }.add(rows.getString(2))
                            }
                        }
                    }
            }
        }

    // -- Reading the two schemas -----------------------------------------------------------

    private data class Column(
        val name: String,
        val type: String,
        val isNullable: Boolean,
    )

    private fun nullability(isNullable: Boolean) = if (isNullable) "nullable" else "not null"

    private val sqlite: Map<String, List<Column>> get() = deviceSchema

    private val postgres: Map<String, List<Column>> get() = serverSchema

    private fun connection(): Connection = openPostgres(testDatabase())

    companion object {
        /**
         * The committed SQLDelight snapshots, which are real SQLite files. Using the
         * shipped artefact rather than re-reading the `.sq` sources means this compares
         * against the schema that actually reaches a device.
         */
        private val snapshotDirectory =
            File("../shared/core/database/src/commonMain/sqldelight/databases")

        private fun testDatabase(): DatabaseConfig =
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

        private fun openPostgres(config: DatabaseConfig): Connection =
            try {
                DriverManager.getConnection(config.url, config.user, config.password)
            } catch (error: SQLException) {
                throw AssertionError(
                    """
                    Could not reach Postgres at ${config.url} as ${config.user}.

                    This test compares the server schema against the device one and cannot do that
                    without a database. It fails rather than skips on purpose: a drift check that
                    quietly does not run still looks green while the two schemas part company.

                        brew install postgresql@18
                        brew services start postgresql@18
                        createdb yellowtrack_test

                    Override with YELLOWTRACK_TEST_DB_URL, YELLOWTRACK_TEST_DB_USER and
                    YELLOWTRACK_TEST_DB_PASSWORD.
                    """.trimIndent(),
                    error,
                )
            }

        /** The device schema, read from the newest committed snapshot. */
        private val deviceSchema: Map<String, List<Column>> by lazy {
            val snapshot =
                snapshotDirectory
                    .listFiles { file -> file.name.endsWith(".db") }
                    ?.maxByOrNull { it.nameWithoutExtension.toIntOrNull() ?: -1 }
                    ?: fail("no SQLDelight schema snapshot under ${snapshotDirectory.absolutePath}")

            DriverManager.getConnection("jdbc:sqlite:${snapshot.absolutePath}").use { db ->
                tableNames(db, "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")
                    .associateWith { table -> sqliteColumns(db, table) }
            }
        }

        /** The server schema, read back after applying the real migrations. */
        private val serverSchema: Map<String, List<Column>> by lazy {
            val config = testDatabase()

            // Proves the migrations themselves apply, not merely that some database
            // somewhere has the right shape.
            openPostgres(config).close()
            flyway(config, allowClean = true).run {
                clean()
                migrate()
            }

            openPostgres(config).use { db ->
                tableNames(
                    db,
                    """
                    SELECT table_name
                    FROM information_schema.tables
                    WHERE table_schema = current_schema()
                      AND table_type = 'BASE TABLE'
                      AND table_name <> 'flyway_schema_history'
                    """.trimIndent(),
                ).associateWith { table -> postgresColumns(db, table) }
            }
        }

        private fun tableNames(
            db: Connection,
            sql: String,
        ): List<String> =
            db.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1)) }
                }
            }

        private fun sqliteColumns(
            db: Connection,
            table: String,
        ): List<Column> =
            db.createStatement().use { statement ->
                // pragma_table_info reports the schema as SQLite itself resolved it, which
                // is more trustworthy than parsing the CREATE TABLE text back out.
                statement.executeQuery("SELECT name, type, \"notnull\" FROM pragma_table_info('$table')").use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(Column(rows.getString(1), rows.getString(2), isNullable = rows.getInt(3) == 0))
                        }
                    }
                }
            }

        private fun postgresColumns(
            db: Connection,
            table: String,
        ): List<Column> =
            db
                .prepareStatement(
                    """
                    SELECT column_name, data_type, is_nullable
                    FROM information_schema.columns
                    WHERE table_schema = current_schema() AND table_name = ?
                    ORDER BY ordinal_position
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, table)
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                add(
                                    Column(
                                        rows.getString(1),
                                        rows.getString(2),
                                        isNullable = rows.getString(3) == "YES",
                                    ),
                                )
                            }
                        }
                    }
                }
    }
}
