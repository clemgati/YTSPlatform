package com.yellowtrack.platform.server.sync

import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientContact
import com.yellowtrack.platform.core.model.client.ClientContactRole
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.contact.ContactId
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.core.model.crew.CrewRole
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.server.TestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Reconciliation, against a real Postgres.
 *
 * Every property here is one whose failure is invisible from inside the application. A
 * cursor that skips a row does not throw; a conflict resolved silently looks like a
 * successful sync; an edit discarded on a phone in a bag is discovered weeks later, if at
 * all. So these tests are written against the outcomes a studio would eventually notice,
 * rather than against the shape of the code that produces them.
 */
class ReconcilerTest {
    private val reconciler = Reconciler(TestDatabase.database)

    // -- Pulling ---------------------------------------------------------------------------

    /**
     * Every entity that exists is in `all`, which is the list that drives pulling.
     *
     * `CrewMembers` and `Deliverables` were added, wired into the envelope, the routes and
     * the apply loop, covered field by field — and left out of this list, because the edit
     * that should have added them matched nothing. They could be pushed and would never come
     * back. Nothing failed; the rows simply never arrived, which is the shape of every
     * synchronisation bug worth fearing.
     */
    @Test
    fun `every synchronised entity is in the list that drives pulling`() {
        val declared =
            SyncedEntity::class
                .sealedSubclasses
                .mapNotNull { it.objectInstance }
                .map { it.table }
                .toSet()

        val pulled = SyncedEntity.all.map { it.table }.toSet()

        assertEquals(
            emptySet(),
            declared - pulled,
            "these entities exist and are never pulled: a device can push them and will never " +
                "receive them, and nothing anywhere reports it",
        )
    }

    @Test
    fun `a page carries the parents of everything in it, however the sequence fell`() {
        val studio = studio()
        push(studio, SyncedEntity.Clients, client(studio, "c1", "Ada Okafor"))
        push(studio, SyncedEntity.Projects, project(studio, "p1", "c1", "Autumn Shoot"))
        push(studio, SyncedEntity.Sessions, session(studio, "s1", "p1", "Shoot day"))
        push(studio, SyncedEntity.CrewMembers, crew(studio, "cr1", "s1", "Rosa Iyer"))

        // Everything above the crew member is edited afterwards, which is ordinary — a
        // client renamed, a booking retitled, a shoot retimed — and which moves all three
        // behind their own descendant in the sequence.
        push(studio, SyncedEntity.Clients, client(studio, "c1", "Ada Okafor-Iyer").bumpedClient())
        push(studio, SyncedEntity.Projects, project(studio, "p1", "c1", "Autumn Shoot, revised").bumpedProject())
        push(studio, SyncedEntity.Sessions, session(studio, "s1", "p1", "Shoot day, moved").bumped())

        // A page small enough to hold only the crew member, which is now lowest.
        val page = reconciler.pull(studio, since = 0, limit = 1)

        assertEquals(1, page.crew().size, "the page should begin with the row lowest in the sequence")
        assertEquals(
            1,
            page.sessions().size,
            "the crew member's session was not in the page, so a device enforcing foreign keys " +
                "could not apply it — and would fail identically on every retry, because the " +
                "cursor only advances once a page has been written",
        )
        assertEquals(1, page.projects().size, "the session's project has to come too")
        assertEquals(1, page.clients().size, "and the project's client, which is two hops away")
    }

    @Test
    fun `a parent pulled in early is still sent when its own sequence is reached`() {
        val studio = studio()
        push(studio, SyncedEntity.Clients, client(studio, "c1", "Ada Okafor"))
        push(studio, SyncedEntity.Projects, project(studio, "p1", "c1", "Autumn Shoot"))
        push(studio, SyncedEntity.Sessions, session(studio, "s1", "p1", "Shoot day"))
        push(studio, SyncedEntity.CrewMembers, crew(studio, "cr1", "s1", "Rosa Iyer"))

        push(studio, SyncedEntity.Clients, client(studio, "c1", "Ada Okafor-Iyer").bumpedClient())
        push(studio, SyncedEntity.Projects, project(studio, "p1", "c1", "Autumn Shoot, revised").bumpedProject())
        push(studio, SyncedEntity.Sessions, session(studio, "s1", "p1", "Shoot day, moved").bumped())

        val first = reconciler.pull(studio, since = 0, limit = 1)
        val second = reconciler.pull(studio, since = first.cursor, limit = 50)

        assertEquals(
            "Shoot day, moved",
            second.sessions().single().title,
            "the cursor advances by server_seq alone, so the session arrives again on its own " +
                "account — an idempotent upsert, and the price of pages that stand alone",
        )
    }

    @Test
    fun `a device that has never synced is sent everything the studio has`() {
        val studio = studio()
        push(studio, SyncedEntity.Clients, client(studio, "c1", "Ada Okafor"))
        push(studio, SyncedEntity.Projects, project(studio, "p1", "c1", "Autumn Shoot"))
        push(studio, SyncedEntity.Sessions, session(studio, "s1", "p1", "Shoot day"))

        val changes = reconciler.pull(studio, since = 0)

        assertEquals(1, changes.clients().size)
        assertEquals(1, changes.projects().size)
        assertEquals(1, changes.sessions().size)
        assertTrue(changes.cursor > 0, "a cursor of zero would make the device pull everything again")
    }

    @Test
    fun `pulling again from the cursor returns nothing, rather than the same rows`() {
        val studio = studio()
        push(studio, SyncedEntity.Clients, client(studio, "c1", "Ada Okafor"))

        val first = reconciler.pull(studio, since = 0)
        val second = reconciler.pull(studio, since = first.cursor)

        assertEquals(0, second.clients().size, "a cursor that does not advance is an infinite sync loop")
        assertEquals(first.cursor, second.cursor, "and an empty pull must leave the cursor where it was")
    }

    @Test
    fun `an edit made after a pull comes back on the next one`() {
        val studio = studio()
        val original = client(studio, "c1", "Ada Okafor")
        push(studio, SyncedEntity.Clients, original)

        val first = reconciler.pull(studio, since = 0)
        push(studio, SyncedEntity.Clients, original.renamedTo("Ada Okafor-Bell"))

        val second = reconciler.pull(studio, since = first.cursor)

        assertEquals(
            listOf("Ada Okafor-Bell"),
            second.clients().map { it.accountName },
            "an edited row must reappear, or the other device never learns about the change",
        )
    }

    @Test
    fun `a page boundary does not step the cursor past rows in another table`() {
        val studio = studio()
        // Interleaved on purpose: the three tables share one sequence, so paging them
        // separately and taking the highest sequence seen would skip whatever sat below it
        // in the tables that were not paged.
        push(studio, SyncedEntity.Clients, client(studio, "c1", "One"))
        push(studio, SyncedEntity.Projects, project(studio, "p1", "c1", "Two"))
        push(studio, SyncedEntity.Clients, client(studio, "c2", "Three"))
        push(studio, SyncedEntity.Projects, project(studio, "p2", "c1", "Four"))
        push(studio, SyncedEntity.Sessions, session(studio, "s1", "p1", "Five"))

        // Walk the whole studio two rows at a time and collect what arrives.
        val seen = mutableSetOf<String>()
        var cursor = 0L
        var pages = 0
        do {
            val page = reconciler.pull(studio, since = cursor, limit = 2)
            page.clients().forEach { seen += it.id.value }
            page.projects().forEach { seen += it.id.value }
            page.sessions().forEach { seen += it.id.value }
            cursor = page.cursor
            pages++
        } while (page.hasMore && pages < 20)

        assertEquals(
            setOf("c1", "c2", "p1", "p2", "s1").map { scoped(studio, it) }.toSet(),
            seen,
            "every row must arrive exactly once across the pages. A row skipped here is a row " +
                "that never reaches the device and is never asked for again",
        )
    }

    @Test
    fun `one studio never pulls another studio's rows`() {
        val mine = studio()
        val theirs = studio()
        push(mine, SyncedEntity.Clients, client(mine, "mine-1", "Ada Okafor"))
        push(theirs, SyncedEntity.Clients, client(theirs, "theirs-1", "Rival Client"))

        val changes = reconciler.pull(mine, since = 0)

        assertEquals(
            listOf("Ada Okafor"),
            changes.clients().map { it.accountName },
            "sync is the first place two studios' rows share a database, so this is the moment " +
                "the tenant boundary starts mattering",
        )
    }

    // -- Pushing ---------------------------------------------------------------------------

    @Test
    fun `a new row is applied without a conflict`() {
        val studio = studio()

        val result = push(studio, SyncedEntity.Clients, client(studio, "c1", "Ada Okafor"))

        assertEquals(PushOutcome.Applied, result.outcome)
        assertEquals(0, conflictCount(studio), "nothing was displaced, so nothing should be recorded")
    }

    @Test
    fun `an edit from a device that was up to date is applied`() {
        val studio = studio()
        val original = client(studio, "c1", "Ada Okafor")
        push(studio, SyncedEntity.Clients, original)

        val result = push(studio, SyncedEntity.Clients, original.renamedTo("Ada Okafor-Bell").bumped())

        assertEquals(PushOutcome.Applied, result.outcome)
        assertEquals(0, conflictCount(studio))
    }

    @Test
    fun `three edits made offline upload as one, and are not a conflict`() {
        val studio = studio()
        val original = client(studio, "c1", "Ada Okafor")
        push(studio, SyncedEntity.Clients, original)

        // The outbox re-reads the row at drain time rather than queuing a payload per edit
        // (ADR 0008 decision 6), so what arrives is the final state at version 4.
        val afterThreeEdits = original.renamedTo("Ada Okafor-Bell").copy(audit = original.audit.copy(version = 4))
        val result = push(studio, SyncedEntity.Clients, afterThreeEdits)

        assertEquals(
            PushOutcome.Applied,
            result.outcome,
            "a device that edited three times while offline has not conflicted with anybody",
        )
        assertEquals(0, conflictCount(studio))
    }

    // -- Conflicts ---------------------------------------------------------------------------

    @Test
    fun `two devices editing the same row conflict, and the loser is kept in full`() {
        val studio = studio()
        val original = session(studio, "s1", "p1", "Ceremony")
        seedProject(studio)
        push(studio, SyncedEntity.Sessions, original)

        // The laptop and the phone both started from version 1.
        val fromLaptop = original.retitled("Ceremony — 2pm").bumped()
        val fromPhone = original.retitled("Ceremony — 3pm").bumped()

        assertEquals(PushOutcome.Applied, push(studio, SyncedEntity.Sessions, fromLaptop).outcome)
        val second = push(studio, SyncedEntity.Sessions, fromPhone)

        assertEquals(
            PushOutcome.Conflicted,
            second.outcome,
            "the second device was working from a version the server had already moved past",
        )

        val conflict = conflicts(studio).single()
        assertEquals("session", conflict.entityTable)
        assertEquals(scoped(studio, "s1"), conflict.entityId)
        assertTrue(
            conflict.losingPayload.contains("Ceremony — 2pm"),
            "the discarded title must be readable. Last-write-wins is only defensible while the " +
                "work it threw away can still be got back — this is that",
        )
        assertTrue(conflict.winningPayload.contains("Ceremony — 3pm"))
    }

    @Test
    fun `the later arrival wins, whatever the clocks say`() {
        val studio = studio()
        seedProject(studio)
        val original = session(studio, "s1", "p1", "Ceremony")
        push(studio, SyncedEntity.Sessions, original)

        // The phone's clock is a day behind, which under a timestamp rule would make it
        // lose. Arrival order is what decides.
        val fromLaptop = original.retitled("From the laptop").bumped()
        val fromPhoneWithBadClock =
            original
                .retitled("From the phone")
                .bumped()
                .let {
                    it.copy(
                        audit = it.audit.copy(updatedAt = it.audit.updatedAt - kotlin.time.Duration.parse("1d")),
                    )
                }

        push(studio, SyncedEntity.Sessions, fromLaptop)
        push(studio, SyncedEntity.Sessions, fromPhoneWithBadClock)

        assertEquals(
            "From the phone",
            reconciler
                .pull(studio, since = 0)
                .sessions()
                .single()
                .title,
            "no client clock takes part in the decision, which is the whole of ADR 0008 decision 1",
        )
    }

    @Test
    fun `a conflict leaves the version ahead of both sides, so the next push is clean`() {
        val studio = studio()
        seedProject(studio)
        val original = session(studio, "s1", "p1", "Ceremony")
        push(studio, SyncedEntity.Sessions, original)

        push(studio, SyncedEntity.Sessions, original.retitled("A").bumped())
        val conflicted = push(studio, SyncedEntity.Sessions, original.retitled("B").bumped())

        assertTrue(
            conflicted.version > 2,
            "leaving both devices on the same version would make every future push between them " +
                "conflict forever",
        )

        val next = original.retitled("C").copy(audit = original.audit.copy(version = conflicted.version + 1))
        assertEquals(PushOutcome.Applied, push(studio, SyncedEntity.Sessions, next).outcome)
    }

    // -- Tombstones ----------------------------------------------------------------------------

    @Test
    fun `a delete beats an edit that raced it, and the edit is still kept`() {
        val studio = studio()
        seedProject(studio)
        val original = session(studio, "s1", "p1", "Ceremony")
        push(studio, SyncedEntity.Sessions, original)

        push(studio, SyncedEntity.Sessions, original.deletedNow())
        val edit = push(studio, SyncedEntity.Sessions, original.retitled("Still going ahead").bumped())

        assertEquals(
            PushOutcome.Conflicted,
            edit.outcome,
            "deleting is deliberate and an edit is more likely to be the accident, so the tombstone stands",
        )

        val stored = reconciler.pull(studio, since = 0).sessions().single()
        assertNotNull(stored.audit.deletedAt, "the row must still be deleted")
        assertTrue(
            conflicts(studio).single().losingPayload.contains("Still going ahead"),
            "and the edit that lost must be recoverable, because it was still somebody's work",
        )
    }

    @Test
    fun `a delete arriving over a live row is an ordinary write`() {
        val studio = studio()
        seedProject(studio)
        val original = session(studio, "s1", "p1", "Ceremony")
        push(studio, SyncedEntity.Sessions, original)

        val result = push(studio, SyncedEntity.Sessions, original.deletedNow())

        assertEquals(PushOutcome.Applied, result.outcome)
        assertEquals(0, conflictCount(studio), "nothing was displaced by a delete nobody was racing")
        assertNotNull(
            reconciler
                .pull(studio, since = 0)
                .sessions()
                .single()
                .audit.deletedAt,
        )
    }

    @Test
    fun `a deleted row still travels, because a tombstone that vanishes comes back from the dead`() {
        val studio = studio()
        val original = client(studio, "c1", "Ada Okafor")
        push(studio, SyncedEntity.Clients, original)
        val afterFirstSync = reconciler.pull(studio, since = 0).cursor

        push(studio, SyncedEntity.Clients, original.deletedNow())

        val changes = reconciler.pull(studio, since = afterFirstSync)
        assertNotNull(
            changes
                .clients()
                .single()
                .audit.deletedAt,
            "the other device has to be told about the delete; a tombstone that never arrives is a " +
                "row that reappears on the next sync",
        )
    }

    // -- Refusals ------------------------------------------------------------------------------

    @Test
    fun `a push for another studio's row is refused rather than stored`() {
        val mine = studio()
        val theirs = studio()

        val result = push(mine, SyncedEntity.Clients, client(theirs, "smuggled", "Planted Row"))

        assertEquals(PushOutcome.Rejected, result.outcome)
        assertEquals(0, reconciler.pull(theirs, since = 0).clients().size, "and nothing must land in their studio")
    }

    @Test
    fun `a client carrying contacts is refused rather than silently stripped`() {
        val studio = studio()
        val withContacts =
            client(studio, "c1", "Ada Okafor").copy(
                contacts =
                    listOf(
                        ClientContact(
                            contact =
                                Contact(
                                    id = ContactId("contact-1"),
                                    studioId = StudioId(studio),
                                    firstName = "Ada",
                                    lastName = "Okafor",
                                    audit = AuditMetadata.createdAt(NOW),
                                ),
                            role = ClientContactRole.Primary,
                        ),
                    ),
            )

        val result = push(studio, SyncedEntity.Clients, withContacts)

        assertEquals(
            PushOutcome.Rejected,
            result.outcome,
            "contacts are their own rows and their own sync units. Dropping them quietly would " +
                "leave a device believing it had uploaded something it had not",
        )
    }

    // -- Fixtures --------------------------------------------------------------------------------

    private data class RecordedConflict(
        val entityTable: String,
        val entityId: String,
        val losingPayload: String,
        val winningPayload: String,
    )

    private fun PulledChanges.crew() = rows[SyncedEntity.CrewMembers.table].orEmpty().filterIsInstance<CrewMember>()

    private fun PulledChanges.clients() = rows[SyncedEntity.Clients.table].orEmpty().filterIsInstance<Client>()

    private fun PulledChanges.projects() = rows[SyncedEntity.Projects.table].orEmpty().filterIsInstance<Project>()

    private fun PulledChanges.sessions() = rows[SyncedEntity.Sessions.table].orEmpty().filterIsInstance<Session>()

    private fun <T> push(
        studioId: String,
        entity: SyncedEntity<T>,
        value: T,
    ) = reconciler.push(studioId, entity, value)

    private fun conflicts(studioId: String): List<RecordedConflict> =
        TestDatabase.database.inStudio(studioId) { db ->
            db
                .prepareStatement(
                    "SELECT entity_table, entity_id, losing_payload, winning_payload " +
                        "FROM sync_conflict ORDER BY detected_at",
                ).use { statement ->
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                add(
                                    RecordedConflict(
                                        rows.getString(1),
                                        rows.getString(2),
                                        rows.getString(3),
                                        rows.getString(4),
                                    ),
                                )
                            }
                        }
                    }
                }
        }

    private fun conflictCount(studioId: String) = conflicts(studioId).size

    /** A studio of its own per test, since the shared database is not emptied between them. */
    private fun studio(): String {
        val id = "sync-studio-${counter++}"
        TestDatabase.connection().use { db ->
            db
                .prepareStatement("INSERT INTO studio(id, name, created_at, updated_at) VALUES (?, ?, 1000, 1000)")
                .use { statement ->
                    statement.setString(1, id)
                    statement.setString(2, "Studio $id")
                    statement.executeUpdate()
                }
        }
        return id
    }

    /** Sessions reference a project, which references a client. */
    private fun seedProject(studioId: String) {
        push(studioId, SyncedEntity.Clients, client(studioId, "c1", "Ada Okafor"))
        push(studioId, SyncedEntity.Projects, project(studioId, "p1", "c1", "Autumn Shoot"))
    }

    private fun client(
        studioId: String,
        id: String,
        name: String,
    ) = Client(
        id = ClientId(scoped(studioId, id)),
        studioId = StudioId(studioId),
        accountName = name,
        accountType = ClientAccountType.Individual,
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun project(
        studioId: String,
        id: String,
        clientId: String,
        name: String,
    ) = Project(
        id = ProjectId(scoped(studioId, id)),
        studioId = StudioId(studioId),
        clientId = ClientId(scoped(studioId, clientId)),
        name = name,
        serviceLine = ServiceLine.Wedding,
        status = ProjectStatus.Booked,
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun Client.bumpedClient() = copy(audit = audit.copy(version = audit.version + 1))

    private fun Project.bumpedProject() = copy(audit = audit.copy(version = audit.version + 1))

    private fun crew(
        studioId: String,
        id: String,
        sessionId: String,
        name: String,
    ) = CrewMember(
        id = CrewMemberId(scoped(studioId, id)),
        studioId = StudioId(studioId),
        sessionId = SessionId(scoped(studioId, sessionId)),
        name = name,
        role = CrewRole.SecondShooter,
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun session(
        studioId: String,
        id: String,
        projectId: String,
        title: String,
    ) = Session(
        id = SessionId(scoped(studioId, id)),
        studioId = StudioId(studioId),
        projectId = ProjectId(scoped(studioId, projectId)),
        title = title,
        kind = SessionKind.Shoot,
        status = SessionStatus.Confirmed,
        startsAt = NOW,
        endsAt = NOW + kotlin.time.Duration.parse("8h"),
        timeZoneId = "Europe/London",
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun Client.renamedTo(name: String) = copy(accountName = name)

    private fun Client.bumped() = copy(audit = audit.copy(version = audit.version + 1))

    private fun Client.deletedNow() = copy(audit = audit.deleted(NOW))

    private fun Session.retitled(title: String) = copy(title = title)

    private fun Session.bumped() = copy(audit = audit.copy(version = audit.version + 1))

    private fun Session.deletedNow() = copy(audit = audit.deleted(NOW))

    /**
     * Real ids are client-generated UUID v7 and are unique across studios. These are not,
     * so they are namespaced — otherwise two studios' fixtures collide on the primary key
     * and the upsert tries to update a row belonging to someone else, which row level
     * security refuses. Correctly, as it turns out.
     */
    private fun scoped(
        studioId: String,
        localId: String,
    ) = "$studioId/$localId"

    private companion object {
        val NOW: Instant = Instant.fromEpochMilliseconds(1_800_000_000_000)
        var counter = 0
    }
}
