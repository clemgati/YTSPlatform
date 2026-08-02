package com.yellowtrack.platform.core.data.sync

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.solar.GeoCoordinates
import com.yellowtrack.platform.core.data.InMemoryDatabaseDriverFactory
import com.yellowtrack.platform.core.data.internal.toDomain
import com.yellowtrack.platform.core.data.sync.applyClientContactLink
import com.yellowtrack.platform.core.data.sync.applyContact
import com.yellowtrack.platform.core.data.sync.applyCrewMember
import com.yellowtrack.platform.core.data.sync.applyDeliverable
import com.yellowtrack.platform.core.data.sync.applyExpense
import com.yellowtrack.platform.core.data.sync.applyGearItem
import com.yellowtrack.platform.core.data.sync.applyInvoice
import com.yellowtrack.platform.core.data.sync.applyLead
import com.yellowtrack.platform.core.data.sync.applyMediaCopy
import com.yellowtrack.platform.core.data.sync.applyMileage
import com.yellowtrack.platform.core.data.sync.applyPackingEntry
import com.yellowtrack.platform.core.data.sync.applyPayment
import com.yellowtrack.platform.core.data.sync.applyStorageVolume
import com.yellowtrack.platform.core.database.DatabaseProvider
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
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.core.model.crew.CrewRole
import com.yellowtrack.platform.core.model.delivery.Deliverable
import com.yellowtrack.platform.core.model.delivery.DeliverableId
import com.yellowtrack.platform.core.model.delivery.DeliverableKind
import com.yellowtrack.platform.core.model.delivery.DeliverableStatus
import com.yellowtrack.platform.core.model.expense.DistanceUnit
import com.yellowtrack.platform.core.model.expense.Expense
import com.yellowtrack.platform.core.model.expense.ExpenseCategory
import com.yellowtrack.platform.core.model.expense.ExpenseId
import com.yellowtrack.platform.core.model.expense.Mileage
import com.yellowtrack.platform.core.model.expense.MileageId
import com.yellowtrack.platform.core.model.gear.GearCategory
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.GearStatus
import com.yellowtrack.platform.core.model.gear.PackingEntry
import com.yellowtrack.platform.core.model.gear.PackingEntryId
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.core.model.invoice.InvoiceStatus
import com.yellowtrack.platform.core.model.invoice.Payment
import com.yellowtrack.platform.core.model.invoice.PaymentId
import com.yellowtrack.platform.core.model.invoice.PaymentMethod
import com.yellowtrack.platform.core.model.lead.Lead
import com.yellowtrack.platform.core.model.lead.LeadId
import com.yellowtrack.platform.core.model.lead.LeadSource
import com.yellowtrack.platform.core.model.lead.LeadStatus
import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.MediaCopyId
import com.yellowtrack.platform.core.model.media.StorageKind
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.media.StorageVolumeId
import com.yellowtrack.platform.core.model.media.VolumeStatus
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
import kotlinx.datetime.LocalDate
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
    fun `every field of an Invoice survives being applied`() =
        runTest {
            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()
            database.applyClient(parentClient())
            database.applyProject(parentProject())

            val fixture =
                Invoice(
                    id = InvoiceId(INVOICE),
                    studioId = StudioId(STUDIO),
                    projectId = ProjectId(PROJECT),
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
                )

            database.applyInvoice(fixture)

            val row = assertNotNull(database.invoiceQueries.selectByIdForSync(INVOICE).executeAsOneOrNull())

            assertEveryFieldSurvived(
                Invoice.serializer(),
                fixture,
                row.toDomain(payments = emptyList()),
                notCarried = setOf("payments"),
            )
        }

    @Test
    fun `every field of a Payment survives being applied`() =
        runTest {
            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()
            database.applyClient(parentClient())
            database.applyProject(parentProject())
            database.applyInvoice(parentInvoice())

            val fixture =
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
                )

            database.applyPayment(fixture)

            val row =
                assertNotNull(
                    database.invoiceQueries
                        .selectPaymentByIdForSync("77777777-7777-7000-8000-000000000001")
                        .executeAsOneOrNull(),
                )

            assertEveryFieldSurvived(Payment.serializer(), fixture, row.toDomain())
        }

    @Test
    fun `every field of a CrewMember survives being applied`() =
        runTest {
            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()
            database.applyClient(parentClient())
            database.applyProject(parentProject())
            database.applySession(parentSession())

            val fixture =
                CrewMember(
                    id = CrewMemberId("aaaaaaaa-aaaa-7000-8000-000000000001"),
                    studioId = StudioId(STUDIO),
                    sessionId = SessionId(SESSION),
                    name = "Rosa Iyer",
                    role = CrewRole.SecondShooter,
                    phone = "07700 900456",
                    callTime = Instant.fromEpochMilliseconds(1_781_199_000_000),
                    notes = "Bringing her own 35mm.",
                    audit = audit(),
                )

            database.applyCrewMember(fixture)

            val row =
                assertNotNull(
                    database.crewMemberQueries
                        .selectByIdForSync("aaaaaaaa-aaaa-7000-8000-000000000001")
                        .executeAsOneOrNull(),
                )

            assertEveryFieldSurvived(CrewMember.serializer(), fixture, row.toDomain())
        }

    @Test
    fun `every field of a Deliverable survives being applied`() =
        runTest {
            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()
            database.applyClient(parentClient())
            database.applyProject(parentProject())

            val fixture =
                Deliverable(
                    id = DeliverableId("bbbbbbbb-bbbb-7000-8000-000000000001"),
                    studioId = StudioId(STUDIO),
                    projectId = ProjectId(PROJECT),
                    name = "Full gallery",
                    kind = DeliverableKind.Gallery,
                    status = DeliverableStatus.InProgress,
                    dueAt = Instant.fromEpochMilliseconds(1_781_900_000_000),
                    deliveredAt = Instant.fromEpochMilliseconds(1_781_950_000_000),
                    approvedAt = Instant.fromEpochMilliseconds(1_781_960_000_000),
                    revisionsUsed = 2,
                    notes = "Two rounds used; a third is chargeable.",
                    audit = audit(),
                )

            database.applyDeliverable(fixture)

            val row =
                assertNotNull(
                    database.deliverableQueries
                        .selectByIdForSync("bbbbbbbb-bbbb-7000-8000-000000000001")
                        .executeAsOneOrNull(),
                )

            assertEveryFieldSurvived(Deliverable.serializer(), fixture, row.toDomain())
        }

    @Test
    fun `every field of a GearItem survives being applied`() =
        runTest {
            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()

            val fixture =
                GearItem(
                    id = GearItemId(GEAR),
                    studioId = StudioId(STUDIO),
                    name = "Summilux 35mm",
                    category = GearCategory.Lens,
                    status = GearStatus.InService,
                    serialNumber = "4412-889",
                    purchasePrice = Money(minorUnits = 380_000, currency = CurrencyCode.GBP),
                    purchasedOn = LocalDate.parse("2024-03-11"),
                    lastServicedAt = Instant.fromEpochMilliseconds(1_781_000_000_000),
                    notes = "Focus ring stiff below ten degrees.",
                    audit = audit(),
                )

            database.applyGearItem(fixture)

            val row = assertNotNull(database.gearQueries.selectGearByIdForSync(GEAR).executeAsOneOrNull())

            assertEveryFieldSurvived(GearItem.serializer(), fixture, row.toDomain())
        }

    @Test
    fun `every field of a PackingEntry survives being applied`() =
        runTest {
            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()
            database.applyClient(parentClient())
            database.applyProject(parentProject())
            database.applySession(parentSession())
            database.applyGearItem(parentGear())

            val fixture =
                PackingEntry(
                    id = PackingEntryId("cccccccc-cccc-7000-8000-000000000001"),
                    studioId = StudioId(STUDIO),
                    sessionId = SessionId(SESSION),
                    gearItemId = GearItemId(GEAR),
                    isPacked = true,
                    isReturned = true,
                    audit = audit(),
                )

            database.applyPackingEntry(fixture)

            val row =
                assertNotNull(
                    database.gearQueries
                        .selectPackingByIdForSync("cccccccc-cccc-7000-8000-000000000001")
                        .executeAsOneOrNull(),
                )

            assertEveryFieldSurvived(PackingEntry.serializer(), fixture, row.toDomain())
        }

    @Test
    fun `every field of a StorageVolume survives being applied`() =
        runTest {
            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()

            val fixture =
                StorageVolume(
                    id = StorageVolumeId(VOLUME),
                    studioId = StudioId(STUDIO),
                    label = "Shuttle 1",
                    kind = StorageKind.Computer,
                    status = VolumeStatus.InUse,
                    isOffsite = true,
                    lastCheckedAt = Instant.fromEpochMilliseconds(1_781_400_000_000),
                    notes = "Kept at the studio manager's house.",
                    audit = audit(),
                )

            database.applyStorageVolume(fixture)

            val row = assertNotNull(database.storageVolumeQueries.selectByIdForSync(VOLUME).executeAsOneOrNull())

            assertEveryFieldSurvived(StorageVolume.serializer(), fixture, row.toDomain())
        }

    @Test
    fun `every field of a MediaCopy survives being applied`() =
        runTest {
            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()
            database.applyClient(parentClient())
            database.applyProject(parentProject())
            database.applySession(parentSession())
            database.applyStorageVolume(parentVolume())

            val fixture =
                MediaCopy(
                    id = MediaCopyId("dddddddd-dddd-7000-8000-000000000001"),
                    studioId = StudioId(STUDIO),
                    sessionId = SessionId(SESSION),
                    volumeId = StorageVolumeId(VOLUME),
                    volumeName = "Shuttle 1",
                    kind = StorageKind.Computer,
                    isOffsite = true,
                    path = "/Volumes/Shuttle1/2026/okafor",
                    copiedAt = Instant.fromEpochMilliseconds(1_781_300_000_000),
                    verifiedAt = Instant.fromEpochMilliseconds(1_781_310_000_000),
                    verifiedFileCount = 2_418,
                    verifiedBytes = 918_273_645L,
                    notes = "Checksums matched on both copies.",
                    audit = audit(),
                )

            database.applyMediaCopy(fixture)

            val row =
                assertNotNull(
                    database.mediaCopyQueries
                        .selectByIdForSync("dddddddd-dddd-7000-8000-000000000001")
                        .executeAsOneOrNull(),
                )

            assertEveryFieldSurvived(MediaCopy.serializer(), fixture, row.toDomain())
        }

    @Test
    fun `every field of a Lead survives being applied`() =
        runTest {
            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()
            database.applyClient(parentClient())
            database.applyProject(parentProject())

            val fixture =
                Lead(
                    id = LeadId("11111111-aaaa-7000-8000-000000000001"),
                    studioId = StudioId(STUDIO),
                    name = "Ada Okafor",
                    source = LeadSource.ClientReferral,
                    status = LeadStatus.New,
                    receivedAt = Instant.fromEpochMilliseconds(1_781_000_000_000),
                    email = "ada@harbourline.test",
                    phone = "07700 900123",
                    firstResponseAt = Instant.fromEpochMilliseconds(1_781_010_000_000),
                    serviceLine = ServiceLine.Wedding,
                    desiredDate = LocalDate.parse("2027-06-12"),
                    budgetLow = Money(minorUnits = 150_000, currency = CurrencyCode.GBP),
                    budgetHigh = Money(minorUnits = 250_000, currency = CurrencyCode.GBP),
                    referredBy = "Rosa Iyer",
                    lostReason = "Went with a cheaper quote",
                    convertedProjectId = ProjectId(PROJECT),
                    convertedClientId = ClientId(CLIENT),
                    notes = "Wants film, not digital.",
                    audit = audit(),
                )

            database.applyLead(fixture)

            val row =
                assertNotNull(
                    database.leadQueries.selectByIdForSync("11111111-aaaa-7000-8000-000000000001").executeAsOneOrNull(),
                )

            assertEveryFieldSurvived(Lead.serializer(), fixture, row.toDomain())
        }

    @Test
    fun `every field of an Expense survives being applied`() =
        runTest {
            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()
            database.applyClient(parentClient())
            database.applyProject(parentProject())

            val fixture =
                Expense(
                    id = ExpenseId("22222222-aaaa-7000-8000-000000000001"),
                    studioId = StudioId(STUDIO),
                    category = ExpenseCategory.Travel,
                    description = "Parking at the venue",
                    amount = Money(minorUnits = 1_200, currency = CurrencyCode.GBP),
                    incurredOn = LocalDate.parse("2026-08-01"),
                    projectId = ProjectId(PROJECT),
                    vendor = "Trebah Garden",
                    isTaxDeductible = true,
                    receiptReference = "R-4412",
                    notes = "All day, paid on arrival.",
                    audit = audit(),
                )

            database.applyExpense(fixture)

            val row =
                assertNotNull(
                    database.expenseQueries
                        .selectByIdForSync("22222222-aaaa-7000-8000-000000000001")
                        .executeAsOneOrNull(),
                )

            assertEveryFieldSurvived(Expense.serializer(), fixture, row.toDomain())
        }

    @Test
    fun `every field of a Mileage survives being applied`() =
        runTest {
            val database = DatabaseProvider(InMemoryDatabaseDriverFactory()).database()
            database.applyClient(parentClient())
            database.applyProject(parentProject())

            val fixture =
                Mileage(
                    id = MileageId("33333333-aaaa-7000-8000-000000000001"),
                    studioId = StudioId(STUDIO),
                    travelledOn = LocalDate.parse("2026-08-01"),
                    distance = 42.5,
                    unit = DistanceUnit.Miles,
                    ratePerUnit = Money(minorUnits = 45, currency = CurrencyCode.GBP),
                    projectId = ProjectId(PROJECT),
                    purpose = "Venue recce",
                    fromLocation = "Falmouth",
                    toLocation = "Mawnan Smith",
                    audit = audit(),
                )

            database.applyMileage(fixture)

            val row =
                assertNotNull(
                    database.expenseQueries
                        .selectMileageByIdForSync("33333333-aaaa-7000-8000-000000000001")
                        .executeAsOneOrNull(),
                )

            assertEveryFieldSurvived(Mileage.serializer(), fixture, row.toDomain())
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

    private fun parentGear() =
        GearItem(
            id = GearItemId(GEAR),
            studioId = StudioId(STUDIO),
            name = "Summilux 35mm",
            audit = audit(),
        )

    private fun parentVolume() =
        StorageVolume(
            id = StorageVolumeId(VOLUME),
            studioId = StudioId(STUDIO),
            label = "Shuttle 1",
            kind = StorageKind.Computer,
            audit = audit(),
        )

    private fun parentSession() =
        Session(
            id = SessionId(SESSION),
            studioId = StudioId(STUDIO),
            projectId = ProjectId(PROJECT),
            title = "Ceremony",
            kind = SessionKind.Shoot,
            status = SessionStatus.Scheduled,
            startsAt = Instant.fromEpochMilliseconds(1_781_200_000_000),
            endsAt = Instant.fromEpochMilliseconds(1_781_210_000_000),
            timeZoneId = "Europe/London",
            audit = audit(),
        )

    private fun parentInvoice() =
        Invoice(
            id = InvoiceId(INVOICE),
            studioId = StudioId(STUDIO),
            projectId = ProjectId(PROJECT),
            number = "2026-014",
            kind = InvoiceKind.Balance,
            status = InvoiceStatus.Draft,
            currency = CurrencyCode.GBP,
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
        const val INVOICE = "88888888-8888-7000-8000-000000000001"
        const val SESSION = "44444444-4444-7000-8000-000000000002"
        const val GEAR = "eeeeeeee-eeee-7000-8000-000000000001"
        const val VOLUME = "ffffffff-ffff-7000-8000-000000000001"

        val json = Json { encodeDefaults = true }
    }
}
