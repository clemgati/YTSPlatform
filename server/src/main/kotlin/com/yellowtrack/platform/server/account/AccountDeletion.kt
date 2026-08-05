package com.yellowtrack.platform.server.account

import com.yellowtrack.platform.server.Database
import com.yellowtrack.platform.server.auth.Passwords
import com.yellowtrack.platform.server.sync.SyncedEntity
import java.sql.Connection
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** What a studio is told when it asks to be deleted. */
data class Deletion(
    /** When the records stop being recoverable. */
    val purgeAfter: Long,
)

/** What one run of the purge actually removed. */
data class PurgeReport(
    val studios: Int,
    val rows: Int,
) {
    val isEmpty: Boolean get() = studios == 0
}

/**
 * Deleting a studio, in two stages with a gap in between.
 *
 * A photography business's whole ledger — every client, contract, invoice and payment — sits
 * behind one button here. Deleting it immediately would be honest about what "delete" means
 * and would also mean a misclick, or somebody else's misclick on a signed-in laptop, ends a
 * business's records with no way back. There is no support desk to ring and no backup a
 * studio can restore for itself.
 *
 * So: [request] makes the studio unreachable at once, and [purge] removes it for good after
 * [retention]. Between the two, the studio is gone by every measure a caller can take — every
 * session revoked, every device signed out, the account and studio marked deleted so `whoami`
 * refuses the token — and the rows are still there for a person to put back.
 *
 * That gap is not a way of keeping data somebody asked to be rid of. It is bounded, it is
 * stated in the response, and the purge is what makes the promise true rather than a setting
 * nobody runs.
 */
class AccountDeletion(
    private val database: Database,
    private val retention: Duration = DEFAULT_RETENTION,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * Signs every device out and marks the studio deleted.
     *
     * The password is required and checked here rather than being taken on trust from the
     * bearer token. A token is what a borrowed laptop already has; this is the one action in
     * the application that cannot be undone by the person it happens to, so it asks the one
     * thing a borrowed laptop does not carry.
     *
     * Returns null when the password is wrong, and says nothing more about why.
     */
    fun request(
        accountId: String,
        studioId: String,
        password: String,
    ): Deletion? =
        database.unscoped { connection ->
            val hash =
                connection
                    .prepareStatement("SELECT password_hash FROM account WHERE id = ? AND deleted_at IS NULL")
                    .use { statement ->
                        statement.setString(1, accountId)
                        statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
                    }

            // An account with no password authenticates some other way, and this route
            // cannot check it. Refusing is right: the alternative is deleting a business on
            // the strength of a token alone.
            if (hash == null || !Passwords.verify(password, hash)) return@unscoped null

            val timestamp = now()

            // Sessions first. If anything below fails the transaction rolls back, but were
            // it the other way round a partial failure would leave a studio marked deleted
            // with devices still holding live tokens.
            connection
                .prepareStatement("UPDATE auth_session SET revoked_at = ? WHERE account_id = ? AND revoked_at IS NULL")
                .use { statement ->
                    statement.setLong(1, timestamp)
                    statement.setString(2, accountId)
                    statement.executeUpdate()
                }

            listOf(
                "UPDATE studio_member SET deleted_at = ?, updated_at = ? WHERE account_id = ? AND deleted_at IS NULL",
                "UPDATE account SET deleted_at = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL",
            ).forEach { sql ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, timestamp)
                    statement.setLong(2, timestamp)
                    statement.setString(3, accountId)
                    statement.executeUpdate()
                }
            }

            connection
                .prepareStatement(
                    "UPDATE studio SET deleted_at = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL",
                ).use { statement ->
                    statement.setLong(1, timestamp)
                    statement.setLong(2, timestamp)
                    statement.setString(3, studioId)
                    statement.executeUpdate()
                }

            Deletion(purgeAfter = timestamp + retention.inWholeMilliseconds)
        }

    /**
     * Removes for good every studio deleted longer ago than [retention].
     */
    fun purge(): PurgeReport {
        // Read before looking, so a long run cannot purge a studio deleted while it worked.
        val cutoff = now() - retention.inWholeMilliseconds

        val studios =
            database.unscoped { connection ->
                connection
                    .prepareStatement("SELECT id FROM studio WHERE deleted_at IS NOT NULL AND deleted_at <= ?")
                    .use { statement ->
                        statement.setLong(1, cutoff)
                        statement.executeQuery().use { rows ->
                            buildList { while (rows.next()) add(rows.getString(1)) }
                        }
                    }
            }

        // Each studio in its own scope, and this is load-bearing rather than tidy. Every
        // business table carries a policy of `studio_id = current_setting('app.studio_id')`,
        // and `unscoped` leaves that setting unset — so the comparison is against NULL, no
        // row matches, and `DELETE` removes nothing while reporting success. A purge written
        // the obvious way deletes the studio row, reports a number, and quietly leaves every
        // client and invoice behind.
        //
        // `studio_member` and `auth_session` are deliberately unguarded (ADR 0009 decision
        // 7) and `studio` and `account` have no `studio_id` at all, so all four are reached
        // from in here exactly as they would be from anywhere else.
        //
        // One transaction each, so a studio that fails to purge does not roll back the ones
        // already done — the next run picks it up again from a row still marked deleted.
        var rows = 0
        studios.forEach { studioId ->
            rows += database.inStudio(studioId) { connection -> purgeStudio(connection, studioId) }
        }

        return PurgeReport(studios = studios.size, rows = rows)
    }

    private fun purgeStudio(
        connection: Connection,
        studioId: String,
    ): Int {
        var removed = 0

        // Children first, or a foreign key refuses. The order comes from the `parents` each
        // entity already declares for sync paging rather than from a second list written out
        // by hand here — a hand-written one would be correct until somebody adds an entity
        // and would then fail in a purge nobody is watching.
        deletionOrder().forEach { entity ->
            connection.prepareStatement("DELETE FROM ${entity.table} WHERE studio_id = ?").use { statement ->
                statement.setString(1, studioId)
                removed += statement.executeUpdate()
            }
        }

        // Not entities, so not in the graph above, and each has to go before what it points at.
        listOf(
            "DELETE FROM auth_session WHERE studio_id = ?",
            "DELETE FROM studio_member WHERE studio_id = ?",
        ).forEach { sql ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, studioId)
                removed += statement.executeUpdate()
            }
        }

        // Accounts that belonged to this studio and to nothing else. A second studio's owner
        // keeps their account; today nobody is in two, and the day somebody is, this is
        // already the behaviour that does not delete a person out from under them.
        connection
            .prepareStatement(
                """
                DELETE FROM account
                WHERE deleted_at IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM studio_member WHERE studio_member.account_id = account.id)
                """.trimIndent(),
            ).use { statement -> removed += statement.executeUpdate() }

        connection.prepareStatement("DELETE FROM studio WHERE id = ?").use { statement ->
            statement.setString(1, studioId)
            removed += statement.executeUpdate()
        }

        return removed
    }

    /**
     * Every synced entity, children before their parents.
     *
     * A depth-first walk of the declared `parents`, emitting each entity after everything it
     * points at has been emitted, then reversed. Cycles cannot hang it — an entity already
     * being visited is skipped — and none exist today.
     */
    private fun deletionOrder(): List<SyncedEntity<*>> {
        val emitted = LinkedHashSet<SyncedEntity<*>>()
        val visiting = HashSet<SyncedEntity<*>>()

        fun visit(entity: SyncedEntity<*>) {
            if (entity in emitted || !visiting.add(entity)) return
            entity.parents.forEach { visit(it.entity) }
            visiting.remove(entity)
            emitted.add(entity)
        }

        SyncedEntity.all.forEach(::visit)
        return emitted.toList().asReversed()
    }

    companion object {
        /**
         * Long enough to notice and ask, short enough to be a promise rather than storage.
         * Thirty days is what people already expect from everything else that offers this.
         */
        val DEFAULT_RETENTION: Duration = 30.days

        fun retentionFromEnvironment(): Duration =
            System.getenv("DELETION_RETENTION_DAYS")?.toLongOrNull()?.days ?: DEFAULT_RETENTION
    }
}
