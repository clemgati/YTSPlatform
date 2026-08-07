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
import com.yellowtrack.platform.core.model.codb.CodbProfile
import com.yellowtrack.platform.core.model.codb.CodbProfileId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.contact.ContactId
import com.yellowtrack.platform.core.model.contact.ContactMethod
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.contract.ContractStatus
import com.yellowtrack.platform.core.model.contract.UsageLicense
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
import com.yellowtrack.platform.core.model.gear.LightSetup
import com.yellowtrack.platform.core.model.gear.LightingRecipe
import com.yellowtrack.platform.core.model.gear.LightingRecipeId
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
import com.yellowtrack.platform.core.model.post.PostProductionTask
import com.yellowtrack.platform.core.model.post.PostProductionTaskId
import com.yellowtrack.platform.core.model.post.PostTaskKind
import com.yellowtrack.platform.core.model.post.PostTaskStatus
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.core.model.quote.QuoteId
import com.yellowtrack.platform.core.model.quote.QuoteStatus
import com.yellowtrack.platform.core.model.release.ReleaseKind
import com.yellowtrack.platform.core.model.release.ReleaseStatus
import com.yellowtrack.platform.core.model.release.TalentRelease
import com.yellowtrack.platform.core.model.release.TalentReleaseId
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.core.model.shot.Shot
import com.yellowtrack.platform.core.model.shot.ShotId
import com.yellowtrack.platform.core.model.studio.StudioProfile
import com.yellowtrack.platform.core.model.studio.StudioProfileId
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.time.Instant

/**
 * One reference from a child row to a parent row.
 *
 * [idOf] returns null where the reference is optional and absent, which is not the same as
 * a parent that is missing: nothing needs fetching either way.
 */
class ParentRef<T>(
    val entity: SyncedEntity<*>,
    val idOf: (T) -> String?,
)

/**
 * The three entities in the 0.7.0 slice, and how each crosses between a Postgres row and
 * the shared model.
 *
 * ADR 0008 scoped synchronisation to `Client`, `Project` and `Session` before the other
 * eighteen, because sync is the one feature here whose bugs are invisible and proving the
 * mechanism against real conflicts on three entities is worth more than building it
 * eighteen times before finding out.
 *
 * The types are `core:model`'s own, which is the whole argument of ADR 0007: adding a field
 * to `Session` is a compile error here rather than a field that quietly stops crossing the
 * wire.
 *
 * ## Sync operates on rows, not on aggregates
 *
 * `Client` carries a `contacts` list, and that list is *not* what this synchronises.
 * Contacts live in `contact` and `client_contact`, which are their own rows with their own
 * ids, and ADR 0008 decision 5 has child collections reconcile by union on those ids rather
 * than travelling inside their parent — precisely so a stale parent cannot discard a child
 * recorded elsewhere.
 *
 * So a pushed `Client` must arrive with no contacts, and [SyncedEntity.Clients] refuses one
 * that does not. Silently dropping them would be the more convenient choice and would mean
 * a device believing it had uploaded something it had not.
 */

sealed interface SyncedEntity<T> {
    /** Matches `sync_conflict.entity_table`, and the table this reads and writes. */
    val table: String

    fun identify(entity: T): String

    fun studioOf(entity: T): String

    fun versionOf(entity: T): Int

    fun deletedAtOf(entity: T): Long?

    fun read(rows: ResultSet): T

    /**
     * The rows this one references, so a page can be applied on its own.
     *
     * Ordering within a page is not enough. The server pages by `server_seq` and an edit
     * bumps it, so a session created before its crew but retimed afterwards sorts *after*
     * its own crew member — and a device syncing from scratch receives the child a page
     * early. With foreign keys enforced that fails, and fails identically on every retry,
     * because the cursor only advances once a page has been written.
     *
     * Declaring the reference lets `pull` close each page over its parents. A parent sent
     * early is sent again when its own sequence is reached, which costs one idempotent
     * upsert and is the whole price of the arrangement.
     */
    val parents: List<ParentRef<T>> get() = emptyList()

    /** Serialises for `sync_conflict`, where both sides of a discarded edit are kept. */
    fun encode(entity: T): String

    /**
     * Writes [entity], overwriting any existing row.
     *
     * [version] is passed separately because reconciliation decides it: a push that
     * conflicts still has to leave the row on a version *ahead* of both sides, or two
     * devices sitting on the same number would conflict with each other forever.
     */
    fun upsert(
        connection: Connection,
        entity: T,
        version: Int,
    )

    object Clients : SyncedEntity<Client> {
        override val table = "client"

        override fun identify(entity: Client) = entity.id.value

        override fun studioOf(entity: Client) = entity.studioId.value

        override fun versionOf(entity: Client) = entity.audit.version

        override fun deletedAtOf(entity: Client) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): Client =
            Client(
                id = ClientId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                accountName = rows.getString("account_name"),
                accountType = enumOrDefault(rows.getString("account_type"), ClientAccountType.Individual),
                // Deliberately empty: contacts are their own rows and their own sync units.
                contacts = emptyList(),
                notes = rows.getString("notes"),
                tags = decodeTags(rows.getString("tags")),
                audit = rows.audit(),
            )

        override fun encode(entity: Client) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: Client,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO client(id, studio_id, account_name, account_type, notes, tags,
                                       created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        account_name = EXCLUDED.account_name,
                        account_type = EXCLUDED.account_type,
                        notes        = EXCLUDED.notes,
                        tags         = EXCLUDED.tags,
                        updated_at   = EXCLUDED.updated_at,
                        deleted_at   = EXCLUDED.deleted_at,
                        version      = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.accountName)
                    statement.setString(4, entity.accountType.name)
                    statement.setString(5, entity.notes)
                    statement.setString(6, payloadJson.encodeToString(entity.tags))
                    statement.setLong(7, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(8, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(9, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(10, version)
                    statement.executeUpdate()
                }
        }

        /** Why a `Client` with contacts is refused rather than quietly stripped. */
        fun rejectionReason(entity: Client): String? =
            if (entity.contacts.isEmpty()) {
                null
            } else {
                "contacts do not travel inside a client. They are their own rows, synchronised " +
                    "as contact and client_contact, and reconcile by union on their own ids"
            }
    }

    /**
     * A person. Their own row, and their own sync unit.
     *
     * Contacts are shared between client accounts — a planner works with several studios'
     * couples — which is the second reason they cannot travel inside a `Client`: there is no
     * single parent to travel inside.
     */
    object Contacts : SyncedEntity<Contact> {
        override val table = "contact"

        override fun identify(entity: Contact) = entity.id.value

        override fun studioOf(entity: Contact) = entity.studioId.value

        override fun versionOf(entity: Contact) = entity.audit.version

        override fun deletedAtOf(entity: Contact) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): Contact =
            Contact(
                id = ContactId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                firstName = rows.getString("first_name"),
                lastName = rows.getString("last_name"),
                company = rows.getString("company"),
                jobTitle = rows.getString("job_title"),
                emails = decodeContactMethods(rows.getString("emails")),
                phones = decodeContactMethods(rows.getString("phones")),
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: Contact) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: Contact,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO contact(id, studio_id, first_name, last_name, company, job_title,
                                        emails, phones, notes,
                                        created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        first_name = EXCLUDED.first_name,
                        last_name  = EXCLUDED.last_name,
                        company    = EXCLUDED.company,
                        job_title  = EXCLUDED.job_title,
                        emails     = EXCLUDED.emails,
                        phones     = EXCLUDED.phones,
                        notes      = EXCLUDED.notes,
                        updated_at = EXCLUDED.updated_at,
                        deleted_at = EXCLUDED.deleted_at,
                        version    = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.firstName)
                    statement.setString(4, entity.lastName)
                    statement.setString(5, entity.company)
                    statement.setString(6, entity.jobTitle)
                    statement.setString(7, payloadJson.encodeToString(entity.emails))
                    statement.setString(8, payloadJson.encodeToString(entity.phones))
                    statement.setString(9, entity.notes)
                    statement.setLong(10, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(11, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(12, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(13, version)
                    statement.executeUpdate()
                }
        }
    }

    /**
     * That a person is attached to an account, in a role.
     *
     * This is decision 5 in practice. Two devices each adding a contact to one client write
     * two of these, with different ids, and both survive — where a `Client` carrying its
     * contacts would have had the later save discard the earlier one's work.
     */
    object ClientContactLinks : SyncedEntity<ClientContactLink> {
        override val table = "client_contact"

        override val parents by lazy {
            listOf(
                ParentRef<ClientContactLink>(Clients) { it.clientId.value },
                ParentRef<ClientContactLink>(Contacts) { it.contactId.value },
            )
        }

        override fun identify(entity: ClientContactLink) = entity.id.value

        override fun studioOf(entity: ClientContactLink) = entity.studioId.value

        override fun versionOf(entity: ClientContactLink) = entity.audit.version

        override fun deletedAtOf(entity: ClientContactLink) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): ClientContactLink =
            ClientContactLink(
                id = ClientContactLinkId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                clientId = ClientId(rows.getString("client_id")),
                contactId = ContactId(rows.getString("contact_id")),
                role = enumOrDefault(rows.getString("role"), ClientContactRole.Primary),
                audit = rows.audit(),
            )

        override fun encode(entity: ClientContactLink) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: ClientContactLink,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO client_contact(id, studio_id, client_id, contact_id, role,
                                               created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        client_id  = EXCLUDED.client_id,
                        contact_id = EXCLUDED.contact_id,
                        role       = EXCLUDED.role,
                        updated_at = EXCLUDED.updated_at,
                        deleted_at = EXCLUDED.deleted_at,
                        version    = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.clientId.value)
                    statement.setString(4, entity.contactId.value)
                    statement.setString(5, entity.role.name)
                    statement.setLong(6, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(7, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(8, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(9, version)
                    statement.executeUpdate()
                }
        }
    }

    /**
     * An invoice, without its payments.
     *
     * `lines` travels *with* the invoice, in a JSON column, and so reconciles by
     * last-write-wins along with the rest of the document. Payments do not: they are their
     * own rows, and ADR 0008 decision 5 exists for exactly this case — a stale device's copy
     * of an invoice must not be able to discard a payment recorded on another. A lost line
     * is retyped from the quote; a lost payment is discovered during a tax return, if at all.
     *
     * The ADR lists line items alongside payments as reconciling by union. They cannot: they
     * are not rows. See the note added to that decision.
     */
    object Invoices : SyncedEntity<Invoice> {
        override val table = "invoice"

        override val parents by lazy { listOf(ParentRef<Invoice>(Projects) { it.projectId.value }) }

        override fun identify(entity: Invoice) = entity.id.value

        override fun studioOf(entity: Invoice) = entity.studioId.value

        override fun versionOf(entity: Invoice) = entity.audit.version

        override fun deletedAtOf(entity: Invoice) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): Invoice =
            Invoice(
                id = InvoiceId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                projectId = ProjectId(rows.getString("project_id")),
                number = rows.getString("number"),
                kind = enumOrDefault(rows.getString("kind"), InvoiceKind.Full),
                status = enumOrDefault(rows.getString("status"), InvoiceStatus.Draft),
                currency = CurrencyCode(rows.getString("currency")),
                lines = decodeLines(rows.getString("lines")),
                // Deliberately empty: payments are their own rows and their own sync units.
                payments = emptyList(),
                issuedAt = rows.getNullableLong("issued_at")?.let { Instant.fromEpochMilliseconds(it) },
                dueAt = rows.getNullableLong("due_at")?.let { Instant.fromEpochMilliseconds(it) },
                notes = rows.getString("notes"),
                lastEmailedAt = rows.getNullableLong("last_emailed_at")?.let { Instant.fromEpochMilliseconds(it) },
                lastEmailedTo = rows.getString("last_emailed_to"),
                audit = rows.audit(),
            )

        override fun encode(entity: Invoice) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: Invoice,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO invoice(id, studio_id, project_id, number, kind, status, currency,
                                        lines, issued_at, due_at, notes,
                                        created_at, updated_at, deleted_at, version,
                                        last_emailed_at, last_emailed_to)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        project_id = EXCLUDED.project_id,
                        number     = EXCLUDED.number,
                        kind       = EXCLUDED.kind,
                        status     = EXCLUDED.status,
                        currency   = EXCLUDED.currency,
                        lines      = EXCLUDED.lines,
                        issued_at  = EXCLUDED.issued_at,
                        due_at     = EXCLUDED.due_at,
                        notes      = EXCLUDED.notes,
                        updated_at = EXCLUDED.updated_at,
                        deleted_at = EXCLUDED.deleted_at,
                        version    = EXCLUDED.version,
                        last_emailed_at = EXCLUDED.last_emailed_at,
                        last_emailed_to = EXCLUDED.last_emailed_to
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.projectId.value)
                    statement.setString(4, entity.number)
                    statement.setString(5, entity.kind.name)
                    statement.setString(6, entity.status.name)
                    statement.setString(7, entity.currency.code)
                    statement.setString(8, payloadJson.encodeToString(entity.lines))
                    statement.setNullableLong(9, entity.issuedAt?.toEpochMilliseconds())
                    statement.setNullableLong(10, entity.dueAt?.toEpochMilliseconds())
                    statement.setString(11, entity.notes)
                    statement.setLong(12, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(13, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(14, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(15, version)
                    statement.setNullableLong(16, entity.lastEmailedAt?.toEpochMilliseconds())
                    statement.setString(17, entity.lastEmailedTo)
                    statement.executeUpdate()
                }
        }

        /** Why an `Invoice` carrying payments is refused rather than quietly stripped. */
        fun rejectionReason(entity: Invoice): String? =
            if (entity.payments.isEmpty()) {
                null
            } else {
                "payments do not travel inside an invoice. They are their own rows and reconcile " +
                    "by union on their own ids, so that a stale invoice cannot discard one taken " +
                    "on another device"
            }
    }

    /**
     * Money actually received. The case ADR 0008 decision 5 was written for.
     */
    object Payments : SyncedEntity<Payment> {
        override val table = "payment"

        override val parents by lazy { listOf(ParentRef<Payment>(Invoices) { it.invoiceId.value }) }

        override fun identify(entity: Payment) = entity.id.value

        override fun studioOf(entity: Payment) = entity.studioId.value

        override fun versionOf(entity: Payment) = entity.audit.version

        override fun deletedAtOf(entity: Payment) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): Payment =
            Payment(
                id = PaymentId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                invoiceId = InvoiceId(rows.getString("invoice_id")),
                amount = Money(rows.getLong("amount_minor"), CurrencyCode(rows.getString("amount_currency"))),
                paidAt = Instant.fromEpochMilliseconds(rows.getLong("paid_at")),
                method = enumOrDefault(rows.getString("method"), PaymentMethod.BankTransfer),
                reference = rows.getString("reference"),
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: Payment) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: Payment,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO payment(id, studio_id, invoice_id, amount_minor, amount_currency,
                                        paid_at, method, reference, notes,
                                        created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        invoice_id      = EXCLUDED.invoice_id,
                        amount_minor    = EXCLUDED.amount_minor,
                        amount_currency = EXCLUDED.amount_currency,
                        paid_at         = EXCLUDED.paid_at,
                        method          = EXCLUDED.method,
                        reference       = EXCLUDED.reference,
                        notes           = EXCLUDED.notes,
                        updated_at      = EXCLUDED.updated_at,
                        deleted_at      = EXCLUDED.deleted_at,
                        version         = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.invoiceId.value)
                    statement.setLong(4, entity.amount.minorUnits)
                    statement.setString(5, entity.amount.currency.code)
                    statement.setLong(6, entity.paidAt.toEpochMilliseconds())
                    statement.setString(7, entity.method.name)
                    statement.setString(8, entity.reference)
                    statement.setString(9, entity.notes)
                    statement.setLong(10, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(11, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(12, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(13, version)
                    statement.executeUpdate()
                }
        }
    }

    /** Who is on a shoot day. A child of the session, and its own row. */
    object CrewMembers : SyncedEntity<CrewMember> {
        override val table = "crew_member"

        override val parents by lazy { listOf(ParentRef<CrewMember>(Sessions) { it.sessionId.value }) }

        override fun identify(entity: CrewMember) = entity.id.value

        override fun studioOf(entity: CrewMember) = entity.studioId.value

        override fun versionOf(entity: CrewMember) = entity.audit.version

        override fun deletedAtOf(entity: CrewMember) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): CrewMember =
            CrewMember(
                id = CrewMemberId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                sessionId = SessionId(rows.getString("session_id")),
                name = rows.getString("name"),
                role = enumOrDefault(rows.getString("role"), CrewRole.SecondShooter),
                phone = rows.getString("phone"),
                callTime = rows.getNullableLong("call_time")?.let { Instant.fromEpochMilliseconds(it) },
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: CrewMember) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: CrewMember,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO crew_member(id, studio_id, session_id, name, role, phone, call_time,
                                            notes, created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        session_id = EXCLUDED.session_id,
                        name       = EXCLUDED.name,
                        role       = EXCLUDED.role,
                        phone      = EXCLUDED.phone,
                        call_time  = EXCLUDED.call_time,
                        notes      = EXCLUDED.notes,
                        updated_at = EXCLUDED.updated_at,
                        deleted_at = EXCLUDED.deleted_at,
                        version    = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.sessionId.value)
                    statement.setString(4, entity.name)
                    statement.setString(5, entity.role.name)
                    statement.setString(6, entity.phone)
                    statement.setNullableLong(7, entity.callTime?.toEpochMilliseconds())
                    statement.setString(8, entity.notes)
                    statement.setLong(9, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(10, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(11, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(12, version)
                    statement.executeUpdate()
                }
        }
    }

    /** What the client is owed, and whether it has gone. A child of the project. */
    object Deliverables : SyncedEntity<Deliverable> {
        override val table = "deliverable"

        override val parents by lazy { listOf(ParentRef<Deliverable>(Projects) { it.projectId.value }) }

        override fun identify(entity: Deliverable) = entity.id.value

        override fun studioOf(entity: Deliverable) = entity.studioId.value

        override fun versionOf(entity: Deliverable) = entity.audit.version

        override fun deletedAtOf(entity: Deliverable) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): Deliverable =
            Deliverable(
                id = DeliverableId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                projectId = ProjectId(rows.getString("project_id")),
                name = rows.getString("name"),
                kind = enumOrDefault(rows.getString("kind"), DeliverableKind.Gallery),
                status = enumOrDefault(rows.getString("status"), DeliverableStatus.NotStarted),
                dueAt = rows.getNullableLong("due_at")?.let { Instant.fromEpochMilliseconds(it) },
                deliveredAt = rows.getNullableLong("delivered_at")?.let { Instant.fromEpochMilliseconds(it) },
                approvedAt = rows.getNullableLong("approved_at")?.let { Instant.fromEpochMilliseconds(it) },
                revisionsUsed = rows.getInt("revisions_used"),
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: Deliverable) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: Deliverable,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO deliverable(id, studio_id, project_id, name, kind, status, due_at,
                                            delivered_at, approved_at, revisions_used, notes,
                                            created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        project_id     = EXCLUDED.project_id,
                        name           = EXCLUDED.name,
                        kind           = EXCLUDED.kind,
                        status         = EXCLUDED.status,
                        due_at         = EXCLUDED.due_at,
                        delivered_at   = EXCLUDED.delivered_at,
                        approved_at    = EXCLUDED.approved_at,
                        revisions_used = EXCLUDED.revisions_used,
                        notes          = EXCLUDED.notes,
                        updated_at     = EXCLUDED.updated_at,
                        deleted_at     = EXCLUDED.deleted_at,
                        version        = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.projectId.value)
                    statement.setString(4, entity.name)
                    statement.setString(5, entity.kind.name)
                    statement.setString(6, entity.status.name)
                    statement.setNullableLong(7, entity.dueAt?.toEpochMilliseconds())
                    statement.setNullableLong(8, entity.deliveredAt?.toEpochMilliseconds())
                    statement.setNullableLong(9, entity.approvedAt?.toEpochMilliseconds())
                    statement.setLong(10, entity.revisionsUsed.toLong())
                    statement.setString(11, entity.notes)
                    statement.setLong(12, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(13, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(14, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(15, version)
                    statement.executeUpdate()
                }
        }
    }

    /** Kit the studio owns. No parent; packing entries hang off it. */
    object GearItems : SyncedEntity<GearItem> {
        override val table = "gear_item"

        override fun identify(entity: GearItem) = entity.id.value

        override fun studioOf(entity: GearItem) = entity.studioId.value

        override fun versionOf(entity: GearItem) = entity.audit.version

        override fun deletedAtOf(entity: GearItem) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): GearItem =
            GearItem(
                id = GearItemId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                name = rows.getString("name"),
                category = enumOrDefault(rows.getString("category"), GearCategory.Other),
                status = enumOrDefault(rows.getString("status"), GearStatus.InService),
                serialNumber = rows.getString("serial_number"),
                purchasePrice =
                    moneyOf(rows.getNullableLong("purchase_price_minor"), rows.getString("purchase_currency")),
                purchasedOn = rows.getString("purchased_on")?.let { LocalDate.parse(it) },
                lastServicedAt = rows.getNullableLong("last_serviced_at")?.let { Instant.fromEpochMilliseconds(it) },
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: GearItem) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: GearItem,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO gear_item(id, studio_id, name, category, status, serial_number,
                                          purchase_price_minor, purchase_currency, purchased_on,
                                          last_serviced_at, notes,
                                          created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        name                 = EXCLUDED.name,
                        category             = EXCLUDED.category,
                        status               = EXCLUDED.status,
                        serial_number        = EXCLUDED.serial_number,
                        purchase_price_minor = EXCLUDED.purchase_price_minor,
                        purchase_currency    = EXCLUDED.purchase_currency,
                        purchased_on         = EXCLUDED.purchased_on,
                        last_serviced_at     = EXCLUDED.last_serviced_at,
                        notes                = EXCLUDED.notes,
                        updated_at           = EXCLUDED.updated_at,
                        deleted_at           = EXCLUDED.deleted_at,
                        version              = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.name)
                    statement.setString(4, entity.category.name)
                    statement.setString(5, entity.status.name)
                    statement.setString(6, entity.serialNumber)
                    statement.setNullableLong(7, entity.purchasePrice?.minorUnits)
                    statement.setString(8, entity.purchasePrice?.currency?.code)
                    statement.setString(9, entity.purchasedOn?.toString())
                    statement.setNullableLong(10, entity.lastServicedAt?.toEpochMilliseconds())
                    statement.setString(11, entity.notes)
                    statement.setLong(12, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(13, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(14, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(15, version)
                    statement.executeUpdate()
                }
        }
    }

    /** What went in the bag for a shoot, and what came back. */
    object PackingEntries : SyncedEntity<PackingEntry> {
        override val table = "packing_entry"

        override val parents by lazy {
            listOf(
                ParentRef<PackingEntry>(Sessions) { it.sessionId.value },
                ParentRef<PackingEntry>(GearItems) { it.gearItemId.value },
            )
        }

        override fun identify(entity: PackingEntry) = entity.id.value

        override fun studioOf(entity: PackingEntry) = entity.studioId.value

        override fun versionOf(entity: PackingEntry) = entity.audit.version

        override fun deletedAtOf(entity: PackingEntry) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): PackingEntry =
            PackingEntry(
                id = PackingEntryId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                sessionId = SessionId(rows.getString("session_id")),
                gearItemId = GearItemId(rows.getString("gear_item_id")),
                isPacked = rows.getBoolean("is_packed"),
                isReturned = rows.getBoolean("is_returned"),
                audit = rows.audit(),
            )

        override fun encode(entity: PackingEntry) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: PackingEntry,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO packing_entry(id, studio_id, session_id, gear_item_id, is_packed,
                                              is_returned, created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        session_id   = EXCLUDED.session_id,
                        gear_item_id = EXCLUDED.gear_item_id,
                        is_packed    = EXCLUDED.is_packed,
                        is_returned  = EXCLUDED.is_returned,
                        updated_at   = EXCLUDED.updated_at,
                        deleted_at   = EXCLUDED.deleted_at,
                        version      = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.sessionId.value)
                    statement.setString(4, entity.gearItemId.value)
                    statement.setBoolean(5, entity.isPacked)
                    statement.setBoolean(6, entity.isReturned)
                    statement.setLong(7, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(8, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(9, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(10, version)
                    statement.executeUpdate()
                }
        }
    }

    /** A disk or card the studio keeps footage on. No parent. */
    object StorageVolumes : SyncedEntity<StorageVolume> {
        override val table = "storage_volume"

        override fun identify(entity: StorageVolume) = entity.id.value

        override fun studioOf(entity: StorageVolume) = entity.studioId.value

        override fun versionOf(entity: StorageVolume) = entity.audit.version

        override fun deletedAtOf(entity: StorageVolume) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): StorageVolume =
            StorageVolume(
                id = StorageVolumeId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                label = rows.getString("label"),
                kind = enumOrDefault(rows.getString("kind"), StorageKind.CameraCard),
                status = enumOrDefault(rows.getString("status"), VolumeStatus.InUse),
                isOffsite = rows.getBoolean("is_offsite"),
                lastCheckedAt = rows.getNullableLong("last_checked_at")?.let { Instant.fromEpochMilliseconds(it) },
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: StorageVolume) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: StorageVolume,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO storage_volume(id, studio_id, label, kind, status, is_offsite,
                                               last_checked_at, notes,
                                               created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        label           = EXCLUDED.label,
                        kind            = EXCLUDED.kind,
                        status          = EXCLUDED.status,
                        is_offsite      = EXCLUDED.is_offsite,
                        last_checked_at = EXCLUDED.last_checked_at,
                        notes           = EXCLUDED.notes,
                        updated_at      = EXCLUDED.updated_at,
                        deleted_at      = EXCLUDED.deleted_at,
                        version         = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.label)
                    statement.setString(4, entity.kind.name)
                    statement.setString(5, entity.status.name)
                    statement.setBoolean(6, entity.isOffsite)
                    statement.setNullableLong(7, entity.lastCheckedAt?.toEpochMilliseconds())
                    statement.setString(8, entity.notes)
                    statement.setLong(9, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(10, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(11, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(12, version)
                    statement.executeUpdate()
                }
        }
    }

    /**
     * Where a shoot's footage was copied to, and whether that copy was checked.
     *
     * `volumeId` is optional — a copy can name a volume the studio has not catalogued — so
     * the parent reference returns null rather than a missing row, and nothing is fetched.
     */
    object MediaCopies : SyncedEntity<MediaCopy> {
        override val table = "media_copy"

        override val parents by lazy {
            listOf(
                ParentRef<MediaCopy>(Sessions) { it.sessionId.value },
                ParentRef<MediaCopy>(StorageVolumes) { it.volumeId?.value },
            )
        }

        override fun identify(entity: MediaCopy) = entity.id.value

        override fun studioOf(entity: MediaCopy) = entity.studioId.value

        override fun versionOf(entity: MediaCopy) = entity.audit.version

        override fun deletedAtOf(entity: MediaCopy) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): MediaCopy =
            MediaCopy(
                id = MediaCopyId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                sessionId = SessionId(rows.getString("session_id")),
                volumeId = rows.getString("volume_id")?.let { StorageVolumeId(it) },
                volumeName = rows.getString("volume_name"),
                kind = enumOrDefault(rows.getString("kind"), StorageKind.CameraCard),
                isOffsite = rows.getBoolean("is_offsite"),
                path = rows.getString("path"),
                copiedAt = rows.getNullableLong("copied_at")?.let { Instant.fromEpochMilliseconds(it) },
                verifiedAt = rows.getNullableLong("verified_at")?.let { Instant.fromEpochMilliseconds(it) },
                verifiedFileCount = rows.getNullableLong("verified_file_count")?.toInt(),
                verifiedBytes = rows.getNullableLong("verified_bytes"),
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: MediaCopy) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: MediaCopy,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO media_copy(id, studio_id, session_id, volume_id, volume_name, kind,
                                           is_offsite, path, copied_at, verified_at,
                                           verified_file_count, verified_bytes, notes,
                                           created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        session_id          = EXCLUDED.session_id,
                        volume_id           = EXCLUDED.volume_id,
                        volume_name         = EXCLUDED.volume_name,
                        kind                = EXCLUDED.kind,
                        is_offsite          = EXCLUDED.is_offsite,
                        path                = EXCLUDED.path,
                        copied_at           = EXCLUDED.copied_at,
                        verified_at         = EXCLUDED.verified_at,
                        verified_file_count = EXCLUDED.verified_file_count,
                        verified_bytes      = EXCLUDED.verified_bytes,
                        notes               = EXCLUDED.notes,
                        updated_at          = EXCLUDED.updated_at,
                        deleted_at          = EXCLUDED.deleted_at,
                        version             = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.sessionId.value)
                    statement.setString(4, entity.volumeId?.value)
                    statement.setString(5, entity.volumeName)
                    statement.setString(6, entity.kind.name)
                    statement.setBoolean(7, entity.isOffsite)
                    statement.setString(8, entity.path)
                    statement.setNullableLong(9, entity.copiedAt?.toEpochMilliseconds())
                    statement.setNullableLong(10, entity.verifiedAt?.toEpochMilliseconds())
                    statement.setNullableLong(11, entity.verifiedFileCount?.toLong())
                    statement.setNullableLong(12, entity.verifiedBytes)
                    statement.setString(13, entity.notes)
                    statement.setLong(14, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(15, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(16, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(17, version)
                    statement.executeUpdate()
                }
        }
    }

    /**
     * An enquiry, before it is a client.
     *
     * Its project and client references are optional and point *forward*: a lead acquires
     * them when it converts. They are declared as parents so a page carrying a converted
     * lead also carries what it converted into.
     */
    object Leads : SyncedEntity<Lead> {
        override val table = "lead"

        override val parents by lazy {
            listOf(
                ParentRef<Lead>(Clients) { it.convertedClientId?.value },
                ParentRef<Lead>(Projects) { it.convertedProjectId?.value },
            )
        }

        override fun identify(entity: Lead) = entity.id.value

        override fun studioOf(entity: Lead) = entity.studioId.value

        override fun versionOf(entity: Lead) = entity.audit.version

        override fun deletedAtOf(entity: Lead) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): Lead =
            Lead(
                id = LeadId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                name = rows.getString("name"),
                source = enumOrDefault(rows.getString("source"), LeadSource.ClientReferral),
                status = enumOrDefault(rows.getString("status"), LeadStatus.New),
                receivedAt = Instant.fromEpochMilliseconds(rows.getLong("received_at")),
                email = rows.getString("email"),
                phone = rows.getString("phone"),
                firstResponseAt = rows.getNullableLong("first_response_at")?.let { Instant.fromEpochMilliseconds(it) },
                serviceLine =
                    rows.getString("service_line")?.let { name ->
                        ServiceLine.entries.firstOrNull { it.name == name }
                    },
                desiredDate = rows.getString("desired_date")?.let { LocalDate.parse(it) },
                budgetLow = moneyOf(rows.getNullableLong("budget_low_minor"), rows.getString("budget_currency")),
                budgetHigh = moneyOf(rows.getNullableLong("budget_high_minor"), rows.getString("budget_currency")),
                referredBy = rows.getString("referred_by"),
                lostReason = rows.getString("lost_reason"),
                convertedProjectId = rows.getString("converted_project_id")?.let { ProjectId(it) },
                convertedClientId = rows.getString("converted_client_id")?.let { ClientId(it) },
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: Lead) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: Lead,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO lead(id, studio_id, name, source, status, received_at, email, phone,
                                     first_response_at, service_line, desired_date, budget_low_minor,
                                     budget_high_minor, budget_currency, referred_by, lost_reason,
                                     converted_project_id, converted_client_id, notes,
                                     created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        name                 = EXCLUDED.name,
                        source               = EXCLUDED.source,
                        status               = EXCLUDED.status,
                        received_at          = EXCLUDED.received_at,
                        email                = EXCLUDED.email,
                        phone                = EXCLUDED.phone,
                        first_response_at    = EXCLUDED.first_response_at,
                        service_line         = EXCLUDED.service_line,
                        desired_date         = EXCLUDED.desired_date,
                        budget_low_minor     = EXCLUDED.budget_low_minor,
                        budget_high_minor    = EXCLUDED.budget_high_minor,
                        budget_currency      = EXCLUDED.budget_currency,
                        referred_by          = EXCLUDED.referred_by,
                        lost_reason          = EXCLUDED.lost_reason,
                        converted_project_id = EXCLUDED.converted_project_id,
                        converted_client_id  = EXCLUDED.converted_client_id,
                        notes                = EXCLUDED.notes,
                        updated_at           = EXCLUDED.updated_at,
                        deleted_at           = EXCLUDED.deleted_at,
                        version              = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.name)
                    statement.setString(4, entity.source.name)
                    statement.setString(5, entity.status.name)
                    statement.setLong(6, entity.receivedAt.toEpochMilliseconds())
                    statement.setString(7, entity.email)
                    statement.setString(8, entity.phone)
                    statement.setNullableLong(9, entity.firstResponseAt?.toEpochMilliseconds())
                    statement.setString(10, entity.serviceLine?.name)
                    statement.setString(11, entity.desiredDate?.toString())
                    statement.setNullableLong(12, entity.budgetLow?.minorUnits)
                    statement.setNullableLong(13, entity.budgetHigh?.minorUnits)
                    statement.setString(14, (entity.budgetLow ?: entity.budgetHigh)?.currency?.code)
                    statement.setString(15, entity.referredBy)
                    statement.setString(16, entity.lostReason)
                    statement.setString(17, entity.convertedProjectId?.value)
                    statement.setString(18, entity.convertedClientId?.value)
                    statement.setString(19, entity.notes)
                    statement.setLong(20, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(21, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(22, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(23, version)
                    statement.executeUpdate()
                }
        }
    }

    /** Money out. Attached to a project when it belongs to one, and to the studio otherwise. */
    object Expenses : SyncedEntity<Expense> {
        override val table = "expense"

        override val parents by lazy { listOf(ParentRef<Expense>(Projects) { it.projectId?.value }) }

        override fun identify(entity: Expense) = entity.id.value

        override fun studioOf(entity: Expense) = entity.studioId.value

        override fun versionOf(entity: Expense) = entity.audit.version

        override fun deletedAtOf(entity: Expense) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): Expense =
            Expense(
                id = ExpenseId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                category = enumOrDefault(rows.getString("category"), ExpenseCategory.Other),
                description = rows.getString("description"),
                amount = Money(rows.getLong("amount_minor"), CurrencyCode(rows.getString("amount_currency"))),
                incurredOn = LocalDate.parse(rows.getString("incurred_on")),
                projectId = rows.getString("project_id")?.let { ProjectId(it) },
                vendor = rows.getString("vendor"),
                isTaxDeductible = rows.getBoolean("is_tax_deductible"),
                receiptReference = rows.getString("receipt_reference"),
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: Expense) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: Expense,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO expense(id, studio_id, category, description, amount_minor,
                                        amount_currency, incurred_on, project_id, vendor,
                                        is_tax_deductible, receipt_reference, notes,
                                        created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        category          = EXCLUDED.category,
                        description       = EXCLUDED.description,
                        amount_minor      = EXCLUDED.amount_minor,
                        amount_currency   = EXCLUDED.amount_currency,
                        incurred_on       = EXCLUDED.incurred_on,
                        project_id        = EXCLUDED.project_id,
                        vendor            = EXCLUDED.vendor,
                        is_tax_deductible = EXCLUDED.is_tax_deductible,
                        receipt_reference = EXCLUDED.receipt_reference,
                        notes             = EXCLUDED.notes,
                        updated_at        = EXCLUDED.updated_at,
                        deleted_at        = EXCLUDED.deleted_at,
                        version           = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.category.name)
                    statement.setString(4, entity.description)
                    statement.setLong(5, entity.amount.minorUnits)
                    statement.setString(6, entity.amount.currency.code)
                    statement.setString(7, entity.incurredOn.toString())
                    statement.setString(8, entity.projectId?.value)
                    statement.setString(9, entity.vendor)
                    statement.setBoolean(10, entity.isTaxDeductible)
                    statement.setString(11, entity.receiptReference)
                    statement.setString(12, entity.notes)
                    statement.setLong(13, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(14, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(15, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(16, version)
                    statement.executeUpdate()
                }
        }
    }

    /** Miles driven, which are money out by another name at tax time. */
    object Mileages : SyncedEntity<Mileage> {
        override val table = "mileage"

        override val parents by lazy { listOf(ParentRef<Mileage>(Projects) { it.projectId?.value }) }

        override fun identify(entity: Mileage) = entity.id.value

        override fun studioOf(entity: Mileage) = entity.studioId.value

        override fun versionOf(entity: Mileage) = entity.audit.version

        override fun deletedAtOf(entity: Mileage) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): Mileage =
            Mileage(
                id = MileageId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                travelledOn = LocalDate.parse(rows.getString("travelled_on")),
                distance = rows.getDouble("distance"),
                unit = enumOrDefault(rows.getString("unit"), DistanceUnit.Miles),
                ratePerUnit = Money(rows.getLong("rate_minor"), CurrencyCode(rows.getString("rate_currency"))),
                projectId = rows.getString("project_id")?.let { ProjectId(it) },
                purpose = rows.getString("purpose"),
                fromLocation = rows.getString("from_location"),
                toLocation = rows.getString("to_location"),
                audit = rows.audit(),
            )

        override fun encode(entity: Mileage) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: Mileage,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO mileage(id, studio_id, travelled_on, distance, unit, rate_minor,
                                        rate_currency, project_id, purpose, from_location, to_location,
                                        created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        travelled_on  = EXCLUDED.travelled_on,
                        distance      = EXCLUDED.distance,
                        unit          = EXCLUDED.unit,
                        rate_minor    = EXCLUDED.rate_minor,
                        rate_currency = EXCLUDED.rate_currency,
                        project_id    = EXCLUDED.project_id,
                        purpose       = EXCLUDED.purpose,
                        from_location = EXCLUDED.from_location,
                        to_location   = EXCLUDED.to_location,
                        updated_at    = EXCLUDED.updated_at,
                        deleted_at    = EXCLUDED.deleted_at,
                        version       = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.travelledOn.toString())
                    statement.setDouble(4, entity.distance)
                    statement.setString(5, entity.unit.name)
                    statement.setLong(6, entity.ratePerUnit.minorUnits)
                    statement.setString(7, entity.ratePerUnit.currency.code)
                    statement.setString(8, entity.projectId?.value)
                    statement.setString(9, entity.purpose)
                    statement.setString(10, entity.fromLocation)
                    statement.setString(11, entity.toLocation)
                    statement.setLong(12, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(13, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(14, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(15, version)
                    statement.executeUpdate()
                }
        }
    }

    /** A price offered, before it is an invoice. Its lines travel with it, as an invoice's do. */
    object Quotes : SyncedEntity<Quote> {
        override val table = "quote"

        override val parents by lazy { listOf(ParentRef<Quote>(Projects) { it.projectId.value }) }

        override fun identify(entity: Quote) = entity.id.value

        override fun studioOf(entity: Quote) = entity.studioId.value

        override fun versionOf(entity: Quote) = entity.audit.version

        override fun deletedAtOf(entity: Quote) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): Quote =
            Quote(
                id = QuoteId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                projectId = ProjectId(rows.getString("project_id")),
                number = rows.getString("number"),
                status = enumOrDefault(rows.getString("status"), QuoteStatus.Draft),
                currency = CurrencyCode(rows.getString("currency")),
                lines = decodeLines(rows.getString("lines")),
                issuedAt = rows.getNullableLong("issued_at")?.let { Instant.fromEpochMilliseconds(it) },
                validUntil = rows.getNullableLong("valid_until")?.let { Instant.fromEpochMilliseconds(it) },
                acceptedAt = rows.getNullableLong("accepted_at")?.let { Instant.fromEpochMilliseconds(it) },
                declinedAt = rows.getNullableLong("declined_at")?.let { Instant.fromEpochMilliseconds(it) },
                notes = rows.getString("notes"),
                terms = rows.getString("terms"),
                lastEmailedAt = rows.getNullableLong("last_emailed_at")?.let { Instant.fromEpochMilliseconds(it) },
                lastEmailedTo = rows.getString("last_emailed_to"),
                audit = rows.audit(),
            )

        override fun encode(entity: Quote) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: Quote,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO quote(id, studio_id, project_id, number, status, currency, lines,
                                      issued_at, valid_until, accepted_at, declined_at, notes, terms,
                                      created_at, updated_at, deleted_at, version,
                                      last_emailed_at, last_emailed_to)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        project_id  = EXCLUDED.project_id,
                        number      = EXCLUDED.number,
                        status      = EXCLUDED.status,
                        currency    = EXCLUDED.currency,
                        lines       = EXCLUDED.lines,
                        issued_at   = EXCLUDED.issued_at,
                        valid_until = EXCLUDED.valid_until,
                        accepted_at = EXCLUDED.accepted_at,
                        declined_at = EXCLUDED.declined_at,
                        notes       = EXCLUDED.notes,
                        terms       = EXCLUDED.terms,
                        updated_at  = EXCLUDED.updated_at,
                        deleted_at  = EXCLUDED.deleted_at,
                        version     = EXCLUDED.version,
                        last_emailed_at = EXCLUDED.last_emailed_at,
                        last_emailed_to = EXCLUDED.last_emailed_to
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.projectId.value)
                    statement.setString(4, entity.number)
                    statement.setString(5, entity.status.name)
                    statement.setString(6, entity.currency.code)
                    statement.setString(7, payloadJson.encodeToString(entity.lines))
                    statement.setNullableLong(8, entity.issuedAt?.toEpochMilliseconds())
                    statement.setNullableLong(9, entity.validUntil?.toEpochMilliseconds())
                    statement.setNullableLong(10, entity.acceptedAt?.toEpochMilliseconds())
                    statement.setNullableLong(11, entity.declinedAt?.toEpochMilliseconds())
                    statement.setString(12, entity.notes)
                    statement.setString(13, entity.terms)
                    statement.setLong(14, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(15, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(16, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(17, version)
                    statement.setNullableLong(18, entity.lastEmailedAt?.toEpochMilliseconds())
                    statement.setString(19, entity.lastEmailedTo)
                    statement.executeUpdate()
                }
        }
    }

    /** What was agreed, and whether it was signed. `usageLicense` is a document in one column. */
    object Contracts : SyncedEntity<Contract> {
        override val table = "contract"

        override val parents by lazy { listOf(ParentRef<Contract>(Projects) { it.projectId.value }) }

        override fun identify(entity: Contract) = entity.id.value

        override fun studioOf(entity: Contract) = entity.studioId.value

        override fun versionOf(entity: Contract) = entity.audit.version

        override fun deletedAtOf(entity: Contract) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): Contract =
            Contract(
                id = ContractId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                projectId = ProjectId(rows.getString("project_id")),
                title = rows.getString("title"),
                status = enumOrDefault(rows.getString("status"), ContractStatus.Draft),
                sentAt = rows.getNullableLong("sent_at")?.let { Instant.fromEpochMilliseconds(it) },
                signedAt = rows.getNullableLong("signed_at")?.let { Instant.fromEpochMilliseconds(it) },
                signerName = rows.getString("signer_name"),
                signerEmail = rows.getString("signer_email"),
                retainerAmount = moneyOf(rows.getNullableLong("retainer_minor"), rows.getString("retainer_currency")),
                isRetainerRefundable = rows.getBoolean("is_retainer_refundable"),
                turnaroundDays = rows.getNullableLong("turnaround_days")?.toInt(),
                revisionRounds = rows.getNullableLong("revision_rounds")?.toInt(),
                cancellationTerms = rows.getString("cancellation_terms"),
                rescheduleTerms = rows.getString("reschedule_terms"),
                weatherClause = rows.getString("weather_clause"),
                usageLicense =
                    rows.getString("usage_license")?.let {
                        runCatching { payloadJson.decodeFromString<UsageLicense>(it) }.getOrNull()
                    },
                documentReference = rows.getString("document_reference"),
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: Contract) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: Contract,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO contract(id, studio_id, project_id, title, status, sent_at, signed_at,
                                         signer_name, signer_email, retainer_minor, retainer_currency,
                                         is_retainer_refundable, turnaround_days, revision_rounds,
                                         cancellation_terms, reschedule_terms, weather_clause,
                                         usage_license, document_reference, notes,
                                         created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        project_id             = EXCLUDED.project_id,
                        title                  = EXCLUDED.title,
                        status                 = EXCLUDED.status,
                        sent_at                = EXCLUDED.sent_at,
                        signed_at              = EXCLUDED.signed_at,
                        signer_name            = EXCLUDED.signer_name,
                        signer_email           = EXCLUDED.signer_email,
                        retainer_minor         = EXCLUDED.retainer_minor,
                        retainer_currency      = EXCLUDED.retainer_currency,
                        is_retainer_refundable = EXCLUDED.is_retainer_refundable,
                        turnaround_days        = EXCLUDED.turnaround_days,
                        revision_rounds        = EXCLUDED.revision_rounds,
                        cancellation_terms     = EXCLUDED.cancellation_terms,
                        reschedule_terms       = EXCLUDED.reschedule_terms,
                        weather_clause         = EXCLUDED.weather_clause,
                        usage_license          = EXCLUDED.usage_license,
                        document_reference     = EXCLUDED.document_reference,
                        notes                  = EXCLUDED.notes,
                        updated_at             = EXCLUDED.updated_at,
                        deleted_at             = EXCLUDED.deleted_at,
                        version                = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.projectId.value)
                    statement.setString(4, entity.title)
                    statement.setString(5, entity.status.name)
                    statement.setNullableLong(6, entity.sentAt?.toEpochMilliseconds())
                    statement.setNullableLong(7, entity.signedAt?.toEpochMilliseconds())
                    statement.setString(8, entity.signerName)
                    statement.setString(9, entity.signerEmail)
                    statement.setNullableLong(10, entity.retainerAmount?.minorUnits)
                    statement.setString(11, entity.retainerAmount?.currency?.code)
                    statement.setBoolean(12, entity.isRetainerRefundable)
                    statement.setNullableLong(13, entity.turnaroundDays?.toLong())
                    statement.setNullableLong(14, entity.revisionRounds?.toLong())
                    statement.setString(15, entity.cancellationTerms)
                    statement.setString(16, entity.rescheduleTerms)
                    statement.setString(17, entity.weatherClause)
                    statement.setString(18, entity.usageLicense?.let { payloadJson.encodeToString(it) })
                    statement.setString(19, entity.documentReference)
                    statement.setString(20, entity.notes)
                    statement.setLong(21, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(22, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(23, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(24, version)
                    statement.executeUpdate()
                }
        }
    }

    /** One frame on the list for a shoot day. `group` is `group_name` in SQL, which is a keyword. */
    object Shots : SyncedEntity<Shot> {
        override val table = "shot"

        override val parents by lazy { listOf(ParentRef<Shot>(Sessions) { it.sessionId.value }) }

        override fun identify(entity: Shot) = entity.id.value

        override fun studioOf(entity: Shot) = entity.studioId.value

        override fun versionOf(entity: Shot) = entity.audit.version

        override fun deletedAtOf(entity: Shot) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): Shot =
            Shot(
                id = ShotId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                sessionId = SessionId(rows.getString("session_id")),
                description = rows.getString("description"),
                group = rows.getString("group_name"),
                people = rows.getString("people"),
                position = rows.getInt("position"),
                isCaptured = rows.getBoolean("is_captured"),
                capturedAt = rows.getNullableLong("captured_at")?.let { Instant.fromEpochMilliseconds(it) },
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: Shot) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: Shot,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO shot(id, studio_id, session_id, description, group_name, people,
                                     position, is_captured, captured_at, notes,
                                     created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        session_id  = EXCLUDED.session_id,
                        description = EXCLUDED.description,
                        group_name  = EXCLUDED.group_name,
                        people      = EXCLUDED.people,
                        position    = EXCLUDED.position,
                        is_captured = EXCLUDED.is_captured,
                        captured_at = EXCLUDED.captured_at,
                        notes       = EXCLUDED.notes,
                        updated_at  = EXCLUDED.updated_at,
                        deleted_at  = EXCLUDED.deleted_at,
                        version     = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.sessionId.value)
                    statement.setString(4, entity.description)
                    statement.setString(5, entity.group)
                    statement.setString(6, entity.people)
                    statement.setLong(7, entity.position.toLong())
                    statement.setBoolean(8, entity.isCaptured)
                    statement.setNullableLong(9, entity.capturedAt?.toEpochMilliseconds())
                    statement.setString(10, entity.notes)
                    statement.setLong(11, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(12, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(13, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(14, version)
                    statement.executeUpdate()
                }
        }
    }

    /** Work after the shoot: culling, editing, albums. */
    object PostProductionTasks : SyncedEntity<PostProductionTask> {
        override val table = "post_task"

        override val parents by lazy { listOf(ParentRef<PostProductionTask>(Projects) { it.projectId.value }) }

        override fun identify(entity: PostProductionTask) = entity.id.value

        override fun studioOf(entity: PostProductionTask) = entity.studioId.value

        override fun versionOf(entity: PostProductionTask) = entity.audit.version

        override fun deletedAtOf(entity: PostProductionTask) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): PostProductionTask =
            PostProductionTask(
                id = PostProductionTaskId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                projectId = ProjectId(rows.getString("project_id")),
                name = rows.getString("name"),
                kind = enumOrDefault(rows.getString("kind"), PostTaskKind.Edit),
                status = enumOrDefault(rows.getString("status"), PostTaskStatus.ToDo),
                estimatedHours = rows.getNullableDouble("estimated_hours"),
                actualHours = rows.getNullableDouble("actual_hours"),
                completedAt = rows.getNullableLong("completed_at")?.let { Instant.fromEpochMilliseconds(it) },
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: PostProductionTask) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: PostProductionTask,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO post_task(id, studio_id, project_id, name, kind, status,
                                          estimated_hours, actual_hours, completed_at, notes,
                                          created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        project_id      = EXCLUDED.project_id,
                        name            = EXCLUDED.name,
                        kind            = EXCLUDED.kind,
                        status          = EXCLUDED.status,
                        estimated_hours = EXCLUDED.estimated_hours,
                        actual_hours    = EXCLUDED.actual_hours,
                        completed_at    = EXCLUDED.completed_at,
                        notes           = EXCLUDED.notes,
                        updated_at      = EXCLUDED.updated_at,
                        deleted_at      = EXCLUDED.deleted_at,
                        version         = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.projectId.value)
                    statement.setString(4, entity.name)
                    statement.setString(5, entity.kind.name)
                    statement.setString(6, entity.status.name)
                    statement.setNullableDouble(7, entity.estimatedHours)
                    statement.setNullableDouble(8, entity.actualHours)
                    statement.setNullableLong(9, entity.completedAt?.toEpochMilliseconds())
                    statement.setString(10, entity.notes)
                    statement.setLong(11, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(12, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(13, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(14, version)
                    statement.executeUpdate()
                }
        }
    }

    /** Permission from the person in the photograph. */
    object TalentReleases : SyncedEntity<TalentRelease> {
        override val table = "talent_release"

        override val parents by lazy { listOf(ParentRef<TalentRelease>(Sessions) { it.sessionId.value }) }

        override fun identify(entity: TalentRelease) = entity.id.value

        override fun studioOf(entity: TalentRelease) = entity.studioId.value

        override fun versionOf(entity: TalentRelease) = entity.audit.version

        override fun deletedAtOf(entity: TalentRelease) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): TalentRelease =
            TalentRelease(
                id = TalentReleaseId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                sessionId = SessionId(rows.getString("session_id")),
                personName = rows.getString("person_name"),
                kind = enumOrDefault(rows.getString("kind"), ReleaseKind.Adult),
                status = enumOrDefault(rows.getString("status"), ReleaseStatus.Pending),
                signedAt = rows.getNullableLong("signed_at")?.let { Instant.fromEpochMilliseconds(it) },
                guardianName = rows.getString("guardian_name"),
                email = rows.getString("email"),
                documentReference = rows.getString("document_reference"),
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: TalentRelease) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: TalentRelease,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO talent_release(id, studio_id, session_id, person_name, kind, status,
                                               signed_at, guardian_name, email, document_reference,
                                               notes, created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        session_id         = EXCLUDED.session_id,
                        person_name        = EXCLUDED.person_name,
                        kind               = EXCLUDED.kind,
                        status             = EXCLUDED.status,
                        signed_at          = EXCLUDED.signed_at,
                        guardian_name      = EXCLUDED.guardian_name,
                        email              = EXCLUDED.email,
                        document_reference = EXCLUDED.document_reference,
                        notes              = EXCLUDED.notes,
                        updated_at         = EXCLUDED.updated_at,
                        deleted_at         = EXCLUDED.deleted_at,
                        version            = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.sessionId.value)
                    statement.setString(4, entity.personName)
                    statement.setString(5, entity.kind.name)
                    statement.setString(6, entity.status.name)
                    statement.setNullableLong(7, entity.signedAt?.toEpochMilliseconds())
                    statement.setString(8, entity.guardianName)
                    statement.setString(9, entity.email)
                    statement.setString(10, entity.documentReference)
                    statement.setString(11, entity.notes)
                    statement.setLong(12, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(13, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(14, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(15, version)
                    statement.executeUpdate()
                }
        }
    }

    /** A remembered lighting set-up. Its lights are a document in one column. */
    object LightingRecipes : SyncedEntity<LightingRecipe> {
        override val table = "lighting_recipe"

        override fun identify(entity: LightingRecipe) = entity.id.value

        override fun studioOf(entity: LightingRecipe) = entity.studioId.value

        override fun versionOf(entity: LightingRecipe) = entity.audit.version

        override fun deletedAtOf(entity: LightingRecipe) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): LightingRecipe =
            LightingRecipe(
                id = LightingRecipeId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                name = rows.getString("name"),
                lights =
                    rows
                        .getString("lights")
                        ?.let {
                            runCatching { payloadJson.decodeFromString<List<LightSetup>>(it) }.getOrNull()
                        }.orEmpty(),
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: LightingRecipe) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: LightingRecipe,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO lighting_recipe(id, studio_id, name, lights, notes,
                                                created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        name       = EXCLUDED.name,
                        lights     = EXCLUDED.lights,
                        notes      = EXCLUDED.notes,
                        updated_at = EXCLUDED.updated_at,
                        deleted_at = EXCLUDED.deleted_at,
                        version    = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.name)
                    statement.setString(4, payloadJson.encodeToString(entity.lights))
                    statement.setString(5, entity.notes)
                    statement.setLong(6, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(7, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(8, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(9, version)
                    statement.executeUpdate()
                }
        }
    }

    /**
     * Who the studio is, on paper. Exactly one row per studio.
     *
     * Its id *is* the studio's, which is what makes it synchronisable at all: both databases
     * carry a unique index on `studio_id`, so two devices each generating an id would give
     * the server two rows it cannot hold. See migration 15.
     */
    object StudioProfiles : SyncedEntity<StudioProfile> {
        override val table = "studio_profile"

        override fun identify(entity: StudioProfile) = entity.id.value

        override fun studioOf(entity: StudioProfile) = entity.studioId.value

        override fun versionOf(entity: StudioProfile) = entity.audit.version

        override fun deletedAtOf(entity: StudioProfile) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): StudioProfile =
            StudioProfile(
                id = StudioProfileId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                name = rows.getString("name"),
                address = rows.getString("address"),
                email = rows.getString("email"),
                phone = rows.getString("phone"),
                website = rows.getString("website"),
                taxNumber = rows.getString("tax_number"),
                paymentInstructions = rows.getString("payment_instructions"),
                documentFooter = rows.getString("document_footer"),
                currency = CurrencyCode(rows.getString("currency")),
                audit = rows.audit(),
            )

        override fun encode(entity: StudioProfile) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: StudioProfile,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO studio_profile(id, studio_id, name, address, email, phone, website,
                                               tax_number, payment_instructions, document_footer,
                                               currency, created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        name                 = EXCLUDED.name,
                        address              = EXCLUDED.address,
                        email                = EXCLUDED.email,
                        phone                = EXCLUDED.phone,
                        website              = EXCLUDED.website,
                        tax_number           = EXCLUDED.tax_number,
                        payment_instructions = EXCLUDED.payment_instructions,
                        document_footer      = EXCLUDED.document_footer,
                        currency             = EXCLUDED.currency,
                        updated_at           = EXCLUDED.updated_at,
                        deleted_at           = EXCLUDED.deleted_at,
                        version              = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.name)
                    statement.setString(4, entity.address)
                    statement.setString(5, entity.email)
                    statement.setString(6, entity.phone)
                    statement.setString(7, entity.website)
                    statement.setString(8, entity.taxNumber)
                    statement.setString(9, entity.paymentInstructions)
                    statement.setString(10, entity.documentFooter)
                    statement.setString(11, entity.currency.code)
                    statement.setLong(12, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(13, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(14, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(15, version)
                    statement.executeUpdate()
                }
        }
    }

    /** What a day has to earn. One row per studio, keyed the same way. */
    object CodbProfiles : SyncedEntity<CodbProfile> {
        override val table = "codb_profile"

        override fun identify(entity: CodbProfile) = entity.id.value

        override fun studioOf(entity: CodbProfile) = entity.studioId.value

        override fun versionOf(entity: CodbProfile) = entity.audit.version

        override fun deletedAtOf(entity: CodbProfile) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): CodbProfile {
            val currency = CurrencyCode(rows.getString("currency"))

            return CodbProfile(
                id = CodbProfileId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                currency = currency,
                targetAnnualSalary = Money(rows.getLong("target_annual_salary_minor"), currency),
                billableDaysPerYear = rows.getInt("billable_days_per_year"),
                taxRateBasisPoints = rows.getInt("tax_rate_basis_points"),
                annualOverheadOverride = rows.getNullableLong("annual_overhead_minor")?.let { Money(it, currency) },
                desiredProfitMarginBasisPoints = rows.getInt("profit_margin_basis_points"),
                audit = rows.audit(),
            )
        }

        override fun encode(entity: CodbProfile) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: CodbProfile,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO codb_profile(id, studio_id, currency, target_annual_salary_minor,
                                             billable_days_per_year, tax_rate_basis_points,
                                             annual_overhead_minor, profit_margin_basis_points,
                                             created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        currency                   = EXCLUDED.currency,
                        target_annual_salary_minor = EXCLUDED.target_annual_salary_minor,
                        billable_days_per_year     = EXCLUDED.billable_days_per_year,
                        tax_rate_basis_points      = EXCLUDED.tax_rate_basis_points,
                        annual_overhead_minor      = EXCLUDED.annual_overhead_minor,
                        profit_margin_basis_points = EXCLUDED.profit_margin_basis_points,
                        updated_at                 = EXCLUDED.updated_at,
                        deleted_at                 = EXCLUDED.deleted_at,
                        version                    = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.currency.code)
                    statement.setLong(4, entity.targetAnnualSalary.minorUnits)
                    statement.setLong(5, entity.billableDaysPerYear.toLong())
                    statement.setLong(6, entity.taxRateBasisPoints.toLong())
                    statement.setNullableLong(7, entity.annualOverheadOverride?.minorUnits)
                    statement.setLong(8, entity.desiredProfitMarginBasisPoints.toLong())
                    statement.setLong(9, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(10, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(11, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(12, version)
                    statement.executeUpdate()
                }
        }
    }

    /**
     * What the studio sells, as a starting point for a booking.
     *
     * The four the application seeds take an id derived from the studio and the template's
     * name, because seeding runs once per *device*: generated ids gave two devices two full
     * sets of the same four templates. A template the studio has renamed keeps whatever id
     * it had, which is right — it has become theirs. See migration 16.
     */
    object ServiceTemplates : SyncedEntity<ServiceTemplate> {
        override val table = "service_template"

        override fun identify(entity: ServiceTemplate) = entity.id.value

        override fun studioOf(entity: ServiceTemplate) = entity.studioId.value

        override fun versionOf(entity: ServiceTemplate) = entity.audit.version

        override fun deletedAtOf(entity: ServiceTemplate) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): ServiceTemplate =
            ServiceTemplate(
                id = ServiceTemplateId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                name = rows.getString("name"),
                serviceLine = enumOrDefault(rows.getString("service_line"), ServiceLine.Portrait),
                defaultSessionDurationMinutes = rows.getInt("default_session_duration_min"),
                defaultSessionCount = rows.getInt("default_session_count"),
                basePrice = moneyOf(rows.getNullableLong("base_price_minor"), rows.getString("base_price_currency")),
                defaultDeliverableCount = rows.getNullableLong("default_deliverable_count")?.toInt(),
                defaultTurnaroundDays = rows.getNullableLong("default_turnaround_days")?.toInt(),
                defaultRevisionRounds = rows.getNullableLong("default_revision_rounds")?.toInt(),
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: ServiceTemplate) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: ServiceTemplate,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO service_template(id, studio_id, name, service_line,
                                                 default_session_duration_min, default_session_count,
                                                 base_price_minor, base_price_currency,
                                                 default_deliverable_count, default_turnaround_days,
                                                 default_revision_rounds, notes,
                                                 created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        name                         = EXCLUDED.name,
                        service_line                 = EXCLUDED.service_line,
                        default_session_duration_min = EXCLUDED.default_session_duration_min,
                        default_session_count        = EXCLUDED.default_session_count,
                        base_price_minor             = EXCLUDED.base_price_minor,
                        base_price_currency          = EXCLUDED.base_price_currency,
                        default_deliverable_count    = EXCLUDED.default_deliverable_count,
                        default_turnaround_days      = EXCLUDED.default_turnaround_days,
                        default_revision_rounds      = EXCLUDED.default_revision_rounds,
                        notes                        = EXCLUDED.notes,
                        updated_at                   = EXCLUDED.updated_at,
                        deleted_at                   = EXCLUDED.deleted_at,
                        version                      = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.name)
                    statement.setString(4, entity.serviceLine.name)
                    statement.setLong(5, entity.defaultSessionDurationMinutes.toLong())
                    statement.setLong(6, entity.defaultSessionCount.toLong())
                    statement.setNullableLong(7, entity.basePrice?.minorUnits)
                    statement.setString(8, entity.basePrice?.currency?.code)
                    statement.setNullableLong(9, entity.defaultDeliverableCount?.toLong())
                    statement.setNullableLong(10, entity.defaultTurnaroundDays?.toLong())
                    statement.setNullableLong(11, entity.defaultRevisionRounds?.toLong())
                    statement.setString(12, entity.notes)
                    statement.setLong(13, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(14, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(15, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(16, version)
                    statement.executeUpdate()
                }
        }
    }

    object Projects : SyncedEntity<Project> {
        override val table = "project"

        override val parents by lazy { listOf(ParentRef<Project>(Clients) { it.clientId.value }) }

        override fun identify(entity: Project) = entity.id.value

        override fun studioOf(entity: Project) = entity.studioId.value

        override fun versionOf(entity: Project) = entity.audit.version

        override fun deletedAtOf(entity: Project) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): Project =
            Project(
                id = ProjectId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                clientId = ClientId(rows.getString("client_id")),
                name = rows.getString("name"),
                serviceLine = enumOrDefault(rows.getString("service_line"), ServiceLine.Other),
                status = enumOrDefault(rows.getString("status"), ProjectStatus.Enquiry),
                serviceTemplateId = rows.getString("service_template_id")?.let(::ServiceTemplateId),
                contractValue =
                    moneyOf(
                        rows.getNullableLong("contract_value_minor"),
                        rows.getString("contract_currency"),
                    ),
                enquiredAt = rows.getNullableLong("enquired_at")?.let(Instant::fromEpochMilliseconds),
                bookedAt = rows.getNullableLong("booked_at")?.let(Instant::fromEpochMilliseconds),
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: Project) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: Project,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO project(id, studio_id, client_id, name, service_line, status,
                                        service_template_id, contract_value_minor, contract_currency,
                                        enquired_at, booked_at, notes,
                                        created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        client_id            = EXCLUDED.client_id,
                        name                 = EXCLUDED.name,
                        service_line         = EXCLUDED.service_line,
                        status               = EXCLUDED.status,
                        service_template_id  = EXCLUDED.service_template_id,
                        contract_value_minor = EXCLUDED.contract_value_minor,
                        contract_currency    = EXCLUDED.contract_currency,
                        enquired_at          = EXCLUDED.enquired_at,
                        booked_at            = EXCLUDED.booked_at,
                        notes                = EXCLUDED.notes,
                        updated_at           = EXCLUDED.updated_at,
                        deleted_at           = EXCLUDED.deleted_at,
                        version              = EXCLUDED.version
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.clientId.value)
                    statement.setString(4, entity.name)
                    statement.setString(5, entity.serviceLine.name)
                    statement.setString(6, entity.status.name)
                    statement.setString(7, entity.serviceTemplateId?.value)
                    statement.setNullableLong(8, entity.contractValue?.minorUnits)
                    statement.setString(9, entity.contractValue?.currency?.code)
                    statement.setNullableLong(10, entity.enquiredAt?.toEpochMilliseconds())
                    statement.setNullableLong(11, entity.bookedAt?.toEpochMilliseconds())
                    statement.setString(12, entity.notes)
                    statement.setLong(13, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(14, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(15, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(16, version)
                    statement.executeUpdate()
                }
        }
    }

    object Sessions : SyncedEntity<Session> {
        override val table = "session"

        override val parents by lazy { listOf(ParentRef<Session>(Projects) { it.projectId.value }) }

        override fun identify(entity: Session) = entity.id.value

        override fun studioOf(entity: Session) = entity.studioId.value

        override fun versionOf(entity: Session) = entity.audit.version

        override fun deletedAtOf(entity: Session) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): Session =
            Session(
                id = SessionId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                projectId = ProjectId(rows.getString("project_id")),
                title = rows.getString("title"),
                kind = enumOrDefault(rows.getString("kind"), SessionKind.Shoot),
                status = enumOrDefault(rows.getString("status"), SessionStatus.Scheduled),
                startsAt = Instant.fromEpochMilliseconds(rows.getLong("starts_at")),
                endsAt = Instant.fromEpochMilliseconds(rows.getLong("ends_at")),
                timeZoneId = rows.getString("time_zone_id"),
                locationName = rows.getString("location_name"),
                locationAddress = rows.getString("location_address"),
                // Both or neither: half a coordinate is not a place, and a stray latitude
                // would put the shoot on the Greenwich meridian.
                coordinates =
                    rows.getNullableDouble("latitude")?.let { latitude ->
                        rows.getNullableDouble("longitude")?.let { longitude ->
                            GeoCoordinates(latitude = latitude, longitude = longitude)
                        }
                    },
                callTime = rows.getNullableLong("call_time")?.let(Instant::fromEpochMilliseconds),
                notes = rows.getString("notes"),
                audit = rows.audit(),
            )

        override fun encode(entity: Session) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: Session,
            version: Int,
        ) {
            connection
                .prepareStatement(
                    """
                    INSERT INTO session(id, studio_id, project_id, title, kind, status,
                                        starts_at, ends_at, time_zone_id, location_name,
                                        location_address, call_time, notes,
                                        created_at, updated_at, deleted_at, version,
                                        latitude, longitude)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        project_id       = EXCLUDED.project_id,
                        title            = EXCLUDED.title,
                        kind             = EXCLUDED.kind,
                        status           = EXCLUDED.status,
                        starts_at        = EXCLUDED.starts_at,
                        ends_at          = EXCLUDED.ends_at,
                        time_zone_id     = EXCLUDED.time_zone_id,
                        location_name    = EXCLUDED.location_name,
                        location_address = EXCLUDED.location_address,
                        call_time        = EXCLUDED.call_time,
                        notes            = EXCLUDED.notes,
                        updated_at       = EXCLUDED.updated_at,
                        deleted_at       = EXCLUDED.deleted_at,
                        version          = EXCLUDED.version,
                        latitude         = EXCLUDED.latitude,
                        longitude        = EXCLUDED.longitude
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entity.id.value)
                    statement.setString(2, entity.studioId.value)
                    statement.setString(3, entity.projectId.value)
                    statement.setString(4, entity.title)
                    statement.setString(5, entity.kind.name)
                    statement.setString(6, entity.status.name)
                    statement.setLong(7, entity.startsAt.toEpochMilliseconds())
                    statement.setLong(8, entity.endsAt.toEpochMilliseconds())
                    statement.setString(9, entity.timeZoneId)
                    statement.setString(10, entity.locationName)
                    statement.setString(11, entity.locationAddress)
                    statement.setNullableLong(12, entity.callTime?.toEpochMilliseconds())
                    statement.setString(13, entity.notes)
                    statement.setLong(14, entity.audit.createdAt.toEpochMilliseconds())
                    statement.setLong(15, entity.audit.updatedAt.toEpochMilliseconds())
                    statement.setNullableLong(16, entity.audit.deletedAt?.toEpochMilliseconds())
                    statement.setInt(17, version)
                    statement.setNullableDouble(18, entity.coordinates?.latitude)
                    statement.setNullableDouble(19, entity.coordinates?.longitude)
                    statement.executeUpdate()
                }
        }
    }

    companion object {
        /**
         * Every synchronised entity, in the order a device must apply them: parents before
         * children.
         *
         * `client_contact` references `client` and `contact`, so applying the link first
         * fails a foreign key on a studio's very first sync. Ordered here rather than by
         * `server_seq` because an edit bumps that — a client updated after its own link was
         * created sorts *after* it — and the ordering that matters is structural, not
         * chronological.
         *
         * Lazy, not eager. These objects are nested in the interface that owns this
         * companion, so touching one initialises the interface, which builds this list,
         * which touches the object still being initialised — and the entry lands as null.
         * Deferring to first use lets every object finish first.
         */
        val all: List<SyncedEntity<*>> by lazy {
            listOf(
                Clients,
                Contacts,
                ClientContactLinks,
                Projects,
                Sessions,
                GearItems,
                PackingEntries,
                StorageVolumes,
                MediaCopies,
                CrewMembers,
                Leads,
                Expenses,
                Mileages,
                Quotes,
                Contracts,
                Shots,
                PostProductionTasks,
                TalentReleases,
                LightingRecipes,
                StudioProfiles,
                CodbProfiles,
                ServiceTemplates,
                Deliverables,
                Invoices,
                Payments,
            )
        }
    }
}

/**
 * The JSON the conflict payloads are written in.
 *
 * Separate from `apiJson` on purpose: this one is not a wire format anybody negotiates, it
 * is a record kept so a studio can read back the work reconciliation threw away, possibly
 * months later and possibly on a newer build. Unknown keys are tolerated for that reason.
 */
internal val payloadJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

private fun ResultSet.audit(): AuditMetadata =
    AuditMetadata(
        createdAt = Instant.fromEpochMilliseconds(getLong("created_at")),
        updatedAt = Instant.fromEpochMilliseconds(getLong("updated_at")),
        deletedAt = getNullableLong("deleted_at")?.let(Instant::fromEpochMilliseconds),
        version = getLong("version").toInt(),
    )

/**
 * Reads an enum stored by name, falling back rather than throwing.
 *
 * A row written by a newer build must not crash an older one. Once devices sync that stops
 * being hypothetical, and a crash on read is far worse than a stale value.
 */
private inline fun <reified T : Enum<T>> enumOrDefault(
    name: String,
    default: T,
): T = enumValues<T>().firstOrNull { it.name == name } ?: default

/**
 * Money spans two columns and is present only when both are. A row with an amount and no
 * currency is malformed, and is read as absent rather than silently given a default one.
 */
private fun moneyOf(
    minorUnits: Long?,
    currency: String?,
): Money? = if (minorUnits != null && currency != null) Money(minorUnits, CurrencyCode(currency)) else null

/** Contact methods live in a JSON column, the same as on the device. */
private fun decodeContactMethods(raw: String?): List<ContactMethod> =
    raw
        ?.let { runCatching { payloadJson.decodeFromString<List<ContactMethod>>(it) }.getOrNull() }
        .orEmpty()

private fun decodeLines(raw: String?): List<LineItem> =
    raw?.let { runCatching { payloadJson.decodeFromString<List<LineItem>>(it) }.getOrNull() }.orEmpty()

private fun decodeTags(raw: String?): List<String> =
    raw?.let { runCatching { payloadJson.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()

// JDBC returns 0 for a null numeric and expects a separate question about it, which is a
// trap worth wrapping once rather than remembering at thirty call sites.
internal fun ResultSet.getNullableLong(column: String): Long? = getLong(column).takeUnless { wasNull() }

internal fun ResultSet.getNullableDouble(column: String): Double? = getDouble(column).takeUnless { wasNull() }

internal fun PreparedStatement.setNullableLong(
    index: Int,
    value: Long?,
) = if (value == null) setNull(index, java.sql.Types.BIGINT) else setLong(index, value)

internal fun PreparedStatement.setNullableDouble(
    index: Int,
    value: Double?,
) = if (value == null) setNull(index, java.sql.Types.DOUBLE) else setDouble(index, value)
