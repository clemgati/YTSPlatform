package com.yellowtrack.platform.core.data.sync

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.solar.GeoCoordinates
import com.yellowtrack.platform.core.data.InMemoryDatabaseDriverFactory
import com.yellowtrack.platform.core.data.internal.toDomain
import com.yellowtrack.platform.core.data.sync.applyClientContactLink
import com.yellowtrack.platform.core.data.sync.applyContact
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientContactLink
import com.yellowtrack.platform.core.model.client.ClientContactLinkId
import com.yellowtrack.platform.core.model.client.ClientContactRole
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.contact.ContactId
import com.yellowtrack.platform.core.model.contact.ContactMethod
import com.yellowtrack.platform.core.model.contact.ContactMethodLabel
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Instant

/**
 * The device half of field coverage: a row that arrived from the server keeps every field.
 *
 * `SyncFieldCoverageTest` on the server proves a field survives Postgres. It can say nothing
 * about what happens next, and `SyncApply` writes its columns by hand too — so a field can
 * cross the wire intact and be dropped on the way into SQLite, which looks identical to a
 * successful sync from every side.
 *
 * Same shape as its server counterpart, and for the same reason: the fixture must set every
 * field, or a field nobody thought to set round-trips as null and proves nothing.
 */
class SyncApplyFieldCoverageTest {
    @Test
    fun `every field of a Client survives being applied`() =
        runTest {
            val fixture =
                Client(
                    id = ClientId(CLIENT),
                    studioId = StudioId(STUDIO),
                    accountName = "Harbourline Photography",
                    accountType = ClientAccountType.Company,
                    contacts = emptyList(),
                    notes = "Prefers email, never rings before ten.",
                    tags = listOf("wedding", "repeat"),
                    audit = audit(),
                )

            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()
            database.applyClient(fixture)

            val row = assertNotNull(database.clientQueries.selectById(CLIENT).executeAsOneOrNull())

            assertEveryFieldSurvived(
                serializer = Client.serializer(),
                before = fixture,
                after = row.toDomain(contacts = emptyList()),
                // ADR 0008 decision 5: contacts are their own rows, reconciled by union, so
                // that a stale parent cannot discard one recorded elsewhere.
                notCarried = setOf("contacts"),
            )
        }

    @Test
    fun `every field of a Project survives being applied`() =
        runTest {
            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()
            database.applyClient(parentClient())

            val fixture =
                Project(
                    id = ProjectId(PROJECT),
                    studioId = StudioId(STUDIO),
                    clientId = ClientId(CLIENT),
                    name = "Okafor — Wedding",
                    serviceLine = ServiceLine.Wedding,
                    status = ProjectStatus.Booked,
                    serviceTemplateId = ServiceTemplateId("33333333-3333-7000-8000-000000000001"),
                    contractValue = Money(minorUnits = 240_000, currency = CurrencyCode.GBP),
                    enquiredAt = Instant.fromEpochMilliseconds(1_781_000_000_000),
                    bookedAt = Instant.fromEpochMilliseconds(1_781_100_000_000),
                    notes = "Second shooter agreed, invoice in two parts.",
                    audit = audit(),
                )

            database.applyProject(fixture)

            val row = assertNotNull(database.projectQueries.selectById(PROJECT).executeAsOneOrNull())

            assertEveryFieldSurvived(Project.serializer(), fixture, row.toDomain())
        }

    @Test
    fun `every field of a Session survives being applied`() =
        runTest {
            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()
            database.applyClient(parentClient())
            database.applyProject(parentProject())

            val fixture =
                Session(
                    id = SessionId("44444444-4444-7000-8000-000000000001"),
                    studioId = StudioId(STUDIO),
                    projectId = ProjectId(PROJECT),
                    title = "Ceremony",
                    kind = SessionKind.Shoot,
                    status = SessionStatus.Scheduled,
                    startsAt = Instant.fromEpochMilliseconds(1_781_200_000_000),
                    endsAt = Instant.fromEpochMilliseconds(1_781_210_000_000),
                    timeZoneId = "Europe/London",
                    locationName = "Trebah Garden",
                    locationAddress = "Mawnan Smith, Falmouth TR11 5JZ",
                    coordinates = GeoCoordinates(latitude = 50.1093, longitude = -5.1108),
                    callTime = Instant.fromEpochMilliseconds(1_781_199_000_000),
                    notes = "Golden hour is the point; do not overrun the ceremony.",
                    audit = audit(),
                )

            database.applySession(fixture)

            val row =
                assertNotNull(
                    database.sessionQueries.selectById("44444444-4444-7000-8000-000000000001").executeAsOneOrNull(),
                )

            assertEveryFieldSurvived(Session.serializer(), fixture, row.toDomain())
        }

    @Test
    fun `every field of a Contact survives being applied`() =
        runTest {
            val fixture =
                Contact(
                    id = ContactId(CONTACT),
                    studioId = StudioId(STUDIO),
                    firstName = "Ada",
                    lastName = "Okafor",
                    company = "Harbourline",
                    jobTitle = "Producer",
                    emails = listOf(ContactMethod(value = "ada@harbourline.test", label = ContactMethodLabel.Work)),
                    phones = listOf(ContactMethod(value = "07700 900123", label = ContactMethodLabel.Mobile)),
                    notes = "Deaf in the left ear; stand on the right.",
                    audit = audit(),
                )

            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()
            database.applyContact(fixture)

            val row = assertNotNull(database.contactQueries.selectById(CONTACT).executeAsOneOrNull())

            assertEveryFieldSurvived(Contact.serializer(), fixture, row.toDomain())
        }

    @Test
    fun `every field of a ClientContactLink survives being applied`() =
        runTest {
            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()
            database.applyClient(parentClient())
            database.applyContact(parentContact())

            val fixture =
                ClientContactLink(
                    id = ClientContactLinkId("55555555-5555-7000-8000-000000000001"),
                    studioId = StudioId(STUDIO),
                    clientId = ClientId(CLIENT),
                    contactId = ContactId(CONTACT),
                    role = ClientContactRole.Planner,
                    audit = audit(),
                )

            database.applyClientContactLink(fixture)

            val row =
                assertNotNull(
                    database.clientQueries
                        .selectClientContactLinkById("55555555-5555-7000-8000-000000000001")
                        .executeAsOneOrNull(),
                )

            assertEveryFieldSurvived(ClientContactLink.serializer(), fixture, row.toDomain())
        }

    @Test
    fun `a tombstone arrives as a tombstone`() =
        runTest {
            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()
            database.applyClient(parentClient())

            assertNotNull(
                database.clientQueries.selectById(CLIENT).executeAsOneOrNull(),
                "the live row should be there before it is deleted",
            )

            database.applyClient(
                parentClient().copy(
                    audit = audit().copy(deletedAt = Instant.fromEpochMilliseconds(1_781_150_000_000), version = 8),
                ),
            )

            assertNull(
                database.clientQueries.selectById(CLIENT).executeAsOneOrNull(),
                "an apply that drops deleted_at is a delete that does not travel, and the row comes " +
                    "back from the dead on a device that had already removed it",
            )
        }

    // -- The mechanism -----------------------------------------------------------------------

    private fun <T> assertEveryFieldSurvived(
        serializer: KSerializer<T>,
        before: T,
        after: T,
        notCarried: Set<String> = emptySet(),
    ) {
        val sent = json.encodeToJsonElement(serializer, before) as JsonObject
        val stored = json.encodeToJsonElement(serializer, after) as JsonObject
        val name = serializer.descriptor.serialName.substringAfterLast('.')

        // Top-level fields only. A nested object such as `audit` is compared whole, so a
        // dropped sub-field still fails the comparison below — but this loop will not notice
        // the fixture leaving one of them unset.

        val unset =
            sent.filterKeys { it !in notCarried }.filter { (_, value) ->
                when (value) {
                    is JsonNull -> true
                    is JsonArray -> value.isEmpty()
                    is JsonPrimitive -> value.isString && value.content.isBlank()
                    else -> false
                }
            }

        if (unset.isNotEmpty()) {
            fail(
                "the $name fixture leaves ${unset.keys} unset, so this test cannot tell whether " +
                    "applying it carries them. Give each a distinguishable value, or name it in " +
                    "notCarried with the reason it does not travel.",
            )
        }

        (sent.keys - notCarried).forEach { field ->
            assertEquals(
                sent[field],
                stored[field],
                "'$field' arrived from the server and was lost writing $name to the device. The " +
                    "sync reports success and the field is simply not there.",
            )
        }

        notCarried.forEach { field ->
            assertTrue(field in sent.keys, "'$field' is exempted from $name but is no longer a field of it")
        }
    }

    private fun parentClient() =
        Client(
            id = ClientId(CLIENT),
            studioId = StudioId(STUDIO),
            accountName = "Harbourline Photography",
            accountType = ClientAccountType.Company,
            audit = audit(),
        )

    private fun parentContact() =
        Contact(
            id = ContactId(CONTACT),
            studioId = StudioId(STUDIO),
            firstName = "Ada",
            lastName = "Okafor",
            audit = audit(),
        )

    private fun parentProject() =
        Project(
            id = ProjectId(PROJECT),
            studioId = StudioId(STUDIO),
            clientId = ClientId(CLIENT),
            name = "Okafor — Wedding",
            serviceLine = ServiceLine.Wedding,
            status = ProjectStatus.Booked,
            audit = audit(),
        )

    /**
     * A live row, not a tombstone: every `selectById` filters `deleted_at IS NULL`, so a
     * deleted fixture cannot be read back through the queries the application uses. That
     * `deleted_at` is carried is proved by `a tombstone arrives as a tombstone` instead.
     */
    private fun audit() =
        AuditMetadata(
            createdAt = Instant.fromEpochMilliseconds(1_781_000_000_000),
            updatedAt = Instant.fromEpochMilliseconds(1_781_100_000_000),
            deletedAt = null,
            version = 7,
        )

    private companion object {
        const val STUDIO = "99999999-9999-7000-8000-000000000001"
        const val CLIENT = "11111111-1111-7000-8000-000000000001"
        const val PROJECT = "22222222-2222-7000-8000-000000000001"
        const val CONTACT = "66666666-6666-7000-8000-000000000001"

        val json = Json { encodeDefaults = true }
    }
}
