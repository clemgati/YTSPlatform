package com.yellowtrack.platform.server

import java.sql.Connection
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Proves that one studio cannot read another's rows.
 *
 * This is the test the whole of ADR 0009 exists to make possible. The failure it guards
 * against — a query that forgets its `WHERE studio_id` — returns *more* rows rather than
 * throwing, looks like ordinary data on a screen, and in this domain hands one
 * photography business another's clients, contracts and takings.
 *
 * Everything here goes through `Database`, not through hand-written SQL, so what is being
 * checked is the path the server actually serves requests on.
 */
class RowLevelSecurityTest {
    // -- The mechanism is switched on -------------------------------------------------------

    @Test
    fun `the application role cannot create tables, which is why migrations need another`() {
        TestDatabase.connection().use { db ->
            db.autoCommit = false
            db.createStatement().use { it.execute("SET LOCAL ROLE yellowtrack_app") }

            val refusal =
                assertFailsWith<SQLException>(
                    "a role that can create tables can own them, and an owner is exempt from " +
                        "its own policies unless every one of them is FORCEd",
                ) {
                    db.createStatement().use { it.execute("CREATE TABLE rls_probe (id text)") }
                }

            assertTrue(
                refusal.message?.contains("permission denied", ignoreCase = true) == true,
                "expected a privilege refusal, got: ${refusal.message}",
            )
            db.rollback()
        }
    }

    /**
     * The counterpart to the test above, and the reason `MIGRATION_USER` exists.
     *
     * These two roles were once one variable. On a fresh database that fails because
     * migration V2 is what creates `yellowtrack_app`; on an existing one the next migration
     * stops at `permission denied for schema public`. Both failures land during a deployment.
     */
    @Test
    fun `the migrating role is not the role that serves requests`() {
        assertNotEquals(
            "yellowtrack_app",
            DatabaseConfig.forMigrations().user,
            "migrations must run as an owner; serving must not",
        )
    }

    @Test
    fun `every table keyed to a studio has row level security, forced`() {
        val unguarded = mutableListOf<String>()

        TestDatabase.connection().use { db ->
            scopedTables(db).forEach { table ->
                db
                    .prepareStatement(
                        "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = ?",
                    ).use { statement ->
                        statement.setString(1, table)
                        statement.executeQuery().use { rows ->
                            assertTrue(rows.next(), "expected a pg_class row for $table")
                            val enabled = rows.getBoolean(1)
                            // FORCE matters as much as ENABLE: without it the table's owner is
                            // exempt, and migrations run as the owner.
                            val forced = rows.getBoolean(2)
                            if (!enabled || !forced) unguarded += table
                        }
                    }
            }
        }

        assertEquals(
            emptyList(),
            unguarded,
            "these tables carry a studio_id and are not protected by it. A table that is ENABLE " +
                "but not FORCE looks protected and is not",
        )
    }

    @Test
    fun `the two tables outside the boundary are exactly the ones declared`() {
        TestDatabase.connection().use { db ->
            val withoutPolicy =
                columnOwners(db, "studio_id").filterNot { table ->
                    db.prepareStatement("SELECT 1 FROM pg_policies WHERE tablename = ?").use { statement ->
                        statement.setString(1, table)
                        statement.executeQuery().use { it.next() }
                    }
                }

            assertEquals(
                listOf("auth_session", "studio_member"),
                withoutPolicy.sorted(),
                "a table keyed to a studio and not covered by a policy is either the authentication " +
                    "hole ADR 0009 decision 7 argues for, or an accident. There are only supposed to " +
                    "be two, and they are supposed to be these",
            )
        }
    }

    @Test
    fun `the application role cannot simply bypass the policies`() {
        TestDatabase.connection().use { db ->
            db
                .prepareStatement("SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = 'yellowtrack_app'")
                .use { statement ->
                    statement.executeQuery().use { rows ->
                        assertTrue(rows.next(), "the application role must exist")
                        assertFalse(rows.getBoolean(1), "a superuser is exempt from every policy in the schema")
                        assertFalse(rows.getBoolean(2), "BYPASSRLS would make all of this decoration")
                    }
                }
        }
    }

    // -- The boundary holds -----------------------------------------------------------------

    @Test
    fun `a studio sees its own rows and not the other studio's`() {
        val fixture = fixture()

        val visible =
            TestDatabase.database.inStudio(fixture.studioA) { db ->
                db.createStatement().use { statement ->
                    statement.executeQuery("SELECT account_name FROM client").use { rows ->
                        buildList { while (rows.next()) add(rows.getString(1)) }
                    }
                }
            }

        assertEquals(listOf("Ada Okafor"), visible, "a studio must see exactly its own clients")
    }

    /**
     * The half of fail-closed that reads as success.
     *
     * A statement outside a studio's scope sees nothing, so a `DELETE` matches nothing and
     * reports that it worked. Migration `V6` said `DELETE FROM sync_conflict`, Flyway logged
     * "Successfully applied 1 migration", and 167 rows stayed exactly where they were. The
     * purge in `AccountDeletion` carries a comment about the same trap, met from the other
     * direction.
     *
     * Written down because the lesson is not "remember to scope your deletes" — it is that an
     * unscoped write is *quiet*, and quiet is what makes it expensive.
     */
    @Test
    fun `a delete that never names a studio removes nothing and says nothing`() {
        val fixture = fixture()

        val deleted =
            TestDatabase.database.unscoped { db ->
                db.createStatement().use { statement -> statement.executeUpdate("DELETE FROM client") }
            }

        val surviving =
            TestDatabase.database.inStudio(fixture.studioA) { db ->
                db.createStatement().use { statement ->
                    statement.executeQuery("SELECT count(*) FROM client").use { rows ->
                        rows.next()
                        rows.getLong(1)
                    }
                }
            }

        assertEquals(0, deleted, "nothing was visible, so nothing was deleted")
        assertTrue(
            surviving > 0,
            "the studio's rows are untouched — and the statement that missed them raised nothing, " +
                "which is why a cleanup is checked by counting rather than by its exit code",
        )
    }

    @Test
    fun `a transaction that never names a studio sees nothing at all`() {
        fixture()

        val visible =
            TestDatabase.database.unscoped { db ->
                db.createStatement().use { statement ->
                    statement.executeQuery("SELECT count(*) FROM client").use { rows ->
                        rows.next()
                        rows.getLong(1)
                    }
                }
            }

        assertEquals(
            0L,
            visible,
            "a forgotten studio must return nothing rather than everything. Fail-closed is the whole " +
                "reason this lives in the database instead of in a WHERE clause",
        )
    }

    @Test
    fun `a studio cannot write a row belonging to another studio`() {
        val fixture = fixture()

        assertFailsWith<SQLException>(
            "the policy must refuse the write outright. Reading is not the only way to cross the " +
                "boundary: a studio that could plant rows under another's id would corrupt books it " +
                "cannot even see",
        ) {
            TestDatabase.database.inStudio(fixture.studioA) { db ->
                db
                    .prepareStatement(
                        """
                        INSERT INTO client(id, studio_id, account_name, account_type, created_at, updated_at)
                        VALUES (?, ?, 'Planted Row', 'Company', 1000, 1000)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, "${fixture.prefix}-smuggled")
                        statement.setString(2, fixture.studioB)
                        statement.executeUpdate()
                    }
            }
        }
    }

    @Test
    fun `a studio cannot edit another studio's row`() {
        val fixture = fixture()

        val updated =
            TestDatabase.database.inStudio(fixture.studioA) { db ->
                db.prepareStatement("UPDATE client SET account_name = 'Hijacked' WHERE id = ?").use { statement ->
                    statement.setString(1, "${fixture.prefix}-client-b")
                    statement.executeUpdate()
                }
            }

        assertEquals(0, updated, "the other studio's row must be invisible to the UPDATE, not merely protected")

        val stillItsOwn =
            TestDatabase.database.inStudio(fixture.studioB) { db ->
                db.prepareStatement("SELECT account_name FROM client WHERE id = ?").use { statement ->
                    statement.setString(1, "${fixture.prefix}-client-b")
                    statement.executeQuery().use { rows ->
                        rows.next()
                        rows.getString(1)
                    }
                }
            }

        assertEquals("Rival Client", stillItsOwn)
    }

    @Test
    fun `the sync cursor is still assigned when writing through a studio scope`() {
        val fixture = fixture()

        val cursor =
            TestDatabase.database.inStudio(fixture.studioA) { db ->
                db.prepareStatement("SELECT server_seq FROM client WHERE id = ?").use { statement ->
                    statement.setString(1, "${fixture.prefix}-client-a")
                    statement.executeQuery().use { rows ->
                        rows.next()
                        rows.getLong(1)
                    }
                }
            }

        assertTrue(
            cursor > 0,
            "the trigger draws from a sequence, so the application role needs USAGE on it. Without " +
                "the grant this write would fail rather than merely be unpulled",
        )
    }

    // -- Fixtures ---------------------------------------------------------------------------

    private data class Fixture(
        val prefix: String,
        val studioA: String,
        val studioB: String,
    )

    /**
     * Two studios with one client each, under identifiers unique to this run.
     *
     * Unique rather than cleaned up, because the shared database is migrated once for the
     * whole JVM and a test that emptied `client` would pull the fixtures out from under
     * whatever ran next.
     */
    private fun fixture(): Fixture {
        val prefix = "rls-${counter++}"
        val fixture = Fixture(prefix, "$prefix-studio-a", "$prefix-studio-b")

        // Seeded as the owner: creating the tenants is what the sign-up path does, and it
        // cannot itself be scoped to a tenant that does not exist yet.
        TestDatabase.connection().use { db ->
            db
                .prepareStatement("INSERT INTO studio(id, name, created_at, updated_at) VALUES (?, ?, 1000, 1000)")
                .use { statement ->
                    statement.setString(1, fixture.studioA)
                    statement.setString(2, "Harbourline Photography")
                    statement.executeUpdate()
                    statement.setString(1, fixture.studioB)
                    statement.setString(2, "Thornbury Studios")
                    statement.executeUpdate()
                }
        }

        insertClient(fixture.studioA, "$prefix-client-a", "Ada Okafor", "Individual")
        insertClient(fixture.studioB, "$prefix-client-b", "Rival Client", "Company")

        return fixture
    }

    private fun insertClient(
        studioId: String,
        id: String,
        name: String,
        type: String,
    ) {
        TestDatabase.database.inStudio(studioId) { db ->
            db
                .prepareStatement(
                    """
                    INSERT INTO client(id, studio_id, account_name, account_type, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 1000, 1000)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, id)
                    statement.setString(2, studioId)
                    statement.setString(3, name)
                    statement.setString(4, type)
                    statement.executeUpdate()
                }
        }
    }

    /** Business tables: keyed to a studio and expected to be behind a policy. */
    private fun scopedTables(db: Connection): List<String> =
        columnOwners(db, "studio_id") - setOf("studio_member", "auth_session")

    private fun columnOwners(
        db: Connection,
        column: String,
    ): List<String> =
        db
            .prepareStatement(
                """
                SELECT table_name
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND column_name = ?
                ORDER BY table_name
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, column)
                statement.executeQuery().use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1)) }
                }
            }

    private companion object {
        private var counter = 0
    }
}
