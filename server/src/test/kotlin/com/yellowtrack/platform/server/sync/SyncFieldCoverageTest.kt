package com.yellowtrack.platform.server.sync

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.solar.GeoCoordinates
import com.yellowtrack.platform.core.model.billing.LineItem
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
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.core.model.invoice.InvoiceStatus
import com.yellowtrack.platform.core.model.invoice.Payment
import com.yellowtrack.platform.core.model.invoice.PaymentId
import com.yellowtrack.platform.core.model.invoice.PaymentMethod
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.server.TestDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Instant

/**
 * Every field of a synchronised model survives the journey to Postgres and back.
 *
 * The wire contract is typed, and `SyncApi` says that buys a compile error in both halves
 * when a model changes. It does not, for the case that actually happens. Adding
 * `callSheetUrl: String? = null` to `Session` compiles cleanly here and in `core:data`:
 * nothing obliges the upsert to write it or `read` to load it, so the field serialises onto
 * the wire and is dropped at both ends. Silently, on a device that reports a successful
 * sync.
 *
 * Structural drift — a renamed type, a changed list — the compiler does catch. Field
 * additions are the common change and pass straight through, which is why this exists
 * before the remaining eighteen entities rather than after.
 *
 * Two halves, and both are needed:
 *
 *  - the fixture must set *every* field to something distinguishable, or a field left null
 *    round-trips as null and proves nothing;
 *  - the round trip must return what went in, field by field.
 *
 * A field genuinely not carried has to be named in `notCarried`, with a reason. That makes
 * the exemption a decision somebody wrote down rather than an absence nobody noticed.
 */
class SyncFieldCoverageTest {
    @Test
    fun `every field of a Client crosses, except the children that are their own rows`() {
        assertEveryFieldCrosses(
            entity = SyncedEntity.Clients,
            fixture =
                Client(
                    id = ClientId("11111111-1111-7000-8000-000000000001"),
                    studioId = StudioId(STUDIO),
                    accountName = "Harbourline Photography",
                    accountType = ClientAccountType.Company,
                    contacts = emptyList(),
                    notes = "Prefers email, never rings before ten.",
                    tags = listOf("wedding", "repeat"),
                    audit = audit(),
                ),
            // ADR 0008 decision 5: contacts are their own rows with their own ids and
            // reconcile by union, precisely so a stale parent cannot discard a contact
            // recorded elsewhere. `Clients.read` returns them empty on purpose, and a push
            // carrying them is refused rather than quietly stripped.
            notCarried = mapOf("contacts" to "child rows, synchronised separately"),
        )
    }

    @Test
    fun `every field of a Contact crosses`() {
        assertEveryFieldCrosses(
            entity = SyncedEntity.Contacts,
            fixture =
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
                ),
        )
    }

    @Test
    fun `every field of a ClientContactLink crosses`() {
        assertEveryFieldCrosses(
            entity = SyncedEntity.ClientContactLinks,
            fixture =
                ClientContactLink(
                    id = ClientContactLinkId("55555555-5555-7000-8000-000000000001"),
                    studioId = StudioId(STUDIO),
                    clientId = ClientId("11111111-1111-7000-8000-000000000001"),
                    contactId = ContactId(CONTACT),
                    role = ClientContactRole.Planner,
                    audit = audit(),
                ),
        )
    }

    @Test
    fun `every field of a Project crosses`() {
        assertEveryFieldCrosses(
            entity = SyncedEntity.Projects,
            fixture =
                Project(
                    id = ProjectId("22222222-2222-7000-8000-000000000001"),
                    studioId = StudioId(STUDIO),
                    clientId = ClientId("11111111-1111-7000-8000-000000000001"),
                    name = "Okafor — Wedding",
                    serviceLine = ServiceLine.Wedding,
                    status = ProjectStatus.Booked,
                    serviceTemplateId = ServiceTemplateId("33333333-3333-7000-8000-000000000001"),
                    contractValue = Money(minorUnits = 240_000, currency = CurrencyCode.GBP),
                    enquiredAt = Instant.fromEpochMilliseconds(1_781_000_000_000),
                    bookedAt = Instant.fromEpochMilliseconds(1_781_100_000_000),
                    notes = "Second shooter agreed, invoice in two parts.",
                    audit = audit(),
                ),
        )
    }

    @Test
    fun `every field of a Session crosses`() {
        assertEveryFieldCrosses(
            entity = SyncedEntity.Sessions,
            fixture =
                Session(
                    id = SessionId("44444444-4444-7000-8000-000000000001"),
                    studioId = StudioId(STUDIO),
                    projectId = ProjectId("22222222-2222-7000-8000-000000000001"),
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
                ),
        )
    }

    @Test
    fun `every field of an Invoice crosses, except the payments that are their own rows`() {
        assertEveryFieldCrosses(
            entity = SyncedEntity.Invoices,
            fixture =
                Invoice(
                    id = InvoiceId(INVOICE),
                    studioId = StudioId(STUDIO),
                    projectId = ProjectId("22222222-2222-7000-8000-000000000001"),
                    number = "2026-014",
                    kind = InvoiceKind.Balance,
                    status = InvoiceStatus.Sent,
                    currency = CurrencyCode.GBP,
                    lines =
                        listOf(
                            LineItem(
                                description = "Wedding coverage, ten hours",
                                unitPrice = Money(minorUnits = 180_000, currency = CurrencyCode.GBP),
                                quantity = 1,
                                taxRateBasisPoints = 2_000,
                            ),
                        ),
                    payments = emptyList(),
                    issuedAt = Instant.fromEpochMilliseconds(1_781_000_000_000),
                    dueAt = Instant.fromEpochMilliseconds(1_781_900_000_000),
                    notes = "Balance due two weeks before the date.",
                    audit = audit(),
                ),
            // ADR 0008 decision 5, and the case it was written for. `lines` is not exempt:
            // it is a JSON column on the invoice, so it travels with the document and
            // reconciles with it.
            notCarried = mapOf("payments" to "child rows, synchronised separately"),
        )
    }

    @Test
    fun `every field of a Payment crosses`() {
        assertEveryFieldCrosses(
            entity = SyncedEntity.Payments,
            fixture =
                Payment(
                    id = PaymentId("77777777-7777-7000-8000-000000000001"),
                    studioId = StudioId(STUDIO),
                    invoiceId = InvoiceId(INVOICE),
                    amount = Money(minorUnits = 90_000, currency = CurrencyCode.GBP),
                    paidAt = Instant.fromEpochMilliseconds(1_781_500_000_000),
                    method = PaymentMethod.BankTransfer,
                    reference = "FP-8841",
                    notes = "Retainer, paid on the day of booking.",
                    audit = audit(),
                ),
        )
    }

    // -- The mechanism -----------------------------------------------------------------------

    private fun <T> assertEveryFieldCrosses(
        entity: SyncedEntity<T>,
        fixture: T,
        notCarried: Map<String, String> = emptyMap(),
    ) {
        val before = entity.asJson(fixture)

        assertFixtureIsExhaustive(entity.table, before, notCarried.keys)

        TestDatabase.connection().use { db ->
            db.autoCommit = false
            try {
                seedParents(db)
                entity.upsert(db, fixture, entity.versionOf(fixture))

                val after = entity.asJson(entity.readBack(db, entity.identify(fixture)))

                (before.keys - notCarried.keys).forEach { field ->
                    assertEquals(
                        before[field],
                        after[field],
                        "'$field' did not survive the round trip through ${entity.table}. It is on " +
                            "the wire and in the model, so it was written by neither the upsert nor " +
                            "read back — a device would sync it successfully and lose it.",
                    )
                }

                // An exemption for a field that no longer exists is a stale decision pointing at
                // nothing, and reads as coverage.
                notCarried.keys.forEach { field ->
                    assertTrue(
                        field in before.keys,
                        "'$field' is exempted from ${entity.table} but is no longer a field of it",
                    )
                }
            } finally {
                db.rollback()
            }
        }
    }

    /**
     * Fails when the fixture leaves a field null, blank or empty.
     *
     * Without this the test rots into a tautology: a field nobody thought to set round-trips
     * as null whether or not the mapper carries it, and the day it starts mattering is the
     * day somebody relies on it.
     */
    private fun assertFixtureIsExhaustive(
        table: String,
        json: JsonObject,
        exempt: Set<String>,
    ) {
        val unset =
            json.filterKeys { it !in exempt }.filter { (_, value) ->
                when (value) {
                    is JsonNull -> true
                    is JsonArray -> value.isEmpty()
                    is JsonPrimitive -> value.isString && value.content.isBlank()
                    else -> false
                }
            }

        if (unset.isNotEmpty()) {
            fail(
                "the $table fixture leaves ${unset.keys} unset, so this test cannot tell whether " +
                    "the mapper carries them. Give each one a distinguishable value, or name it in " +
                    "notCarried with the reason it does not travel.",
            )
        }
    }

    private fun <T> SyncedEntity<T>.asJson(entity: T): JsonObject =
        Json.parseToJsonElement(encode(entity)) as JsonObject

    private fun <T> SyncedEntity<T>.readBack(
        db: Connection,
        id: String,
    ): T =
        db.prepareStatement("SELECT * FROM $table WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { rows ->
                assertTrue(rows.next(), "the upsert into $table wrote nothing")
                read(rows)
            }
        }

    /**
     * Foreign keys, not fixtures: a session needs its project and a project needs its client.
     * Rolled back with everything else.
     */
    private fun seedParents(db: Connection) {
        db.createStatement().use { statement ->
            statement.execute(
                """
                INSERT INTO studio (id, name, created_at, updated_at)
                VALUES ('$STUDIO', 'Harbourline', 0, 0)
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
            )
        }

        SyncedEntity.Clients.upsert(
            db,
            Client(
                id = ClientId("11111111-1111-7000-8000-000000000001"),
                studioId = StudioId(STUDIO),
                accountName = "Harbourline Photography",
                accountType = ClientAccountType.Company,
                audit = audit(),
            ),
            version = 1,
        )

        SyncedEntity.Contacts.upsert(
            db,
            Contact(
                id = ContactId(CONTACT),
                studioId = StudioId(STUDIO),
                firstName = "Ada",
                lastName = "Okafor",
                audit = audit(),
            ),
            version = 1,
        )

        SyncedEntity.Projects.upsert(
            db,
            Project(
                id = ProjectId("22222222-2222-7000-8000-000000000001"),
                studioId = StudioId(STUDIO),
                clientId = ClientId("11111111-1111-7000-8000-000000000001"),
                name = "Okafor — Wedding",
                serviceLine = ServiceLine.Wedding,
                status = ProjectStatus.Booked,
                audit = audit(),
            ),
            version = 1,
        )

        SyncedEntity.Invoices.upsert(
            db,
            Invoice(
                id = InvoiceId(INVOICE),
                studioId = StudioId(STUDIO),
                projectId = ProjectId("22222222-2222-7000-8000-000000000001"),
                number = "2026-014",
                kind = InvoiceKind.Balance,
                status = InvoiceStatus.Draft,
                currency = CurrencyCode.GBP,
                audit = audit(),
            ),
            version = 1,
        )
    }

    private fun audit() =
        AuditMetadata(
            createdAt = Instant.fromEpochMilliseconds(1_781_000_000_000),
            updatedAt = Instant.fromEpochMilliseconds(1_781_100_000_000),
            deletedAt = Instant.fromEpochMilliseconds(1_781_150_000_000),
            version = 7,
        )

    private companion object {
        const val STUDIO = "99999999-9999-7000-8000-000000000001"
        const val CONTACT = "66666666-6666-7000-8000-000000000001"
        const val INVOICE = "88888888-8888-7000-8000-000000000001"
    }
}
