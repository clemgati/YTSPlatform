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
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.core.model.crew.CrewRole
import com.yellowtrack.platform.core.model.delivery.Deliverable
import com.yellowtrack.platform.core.model.delivery.DeliverableId
import com.yellowtrack.platform.core.model.delivery.DeliverableKind
import com.yellowtrack.platform.core.model.delivery.DeliverableStatus
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
import com.yellowtrack.platform.core.model.sync.SyncConflict
import com.yellowtrack.platform.core.model.sync.SyncConflictId
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
                                        created_at, updated_at, deleted_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                        version    = EXCLUDED.version
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

    /**
     * Conflicts travel to the device, and only in that direction.
     *
     * The server is the only party that ever sees both versions, so it is the only one that
     * can raise one. A device pushing a conflict would be asserting something it cannot
     * know, and [SyncRoutes] accepts no such list — which is why [upsert] here exists to
     * satisfy the interface and is reached by nothing.
     *
     * They are pulled at all because a conflict nobody is shown is the thing ADR 0008 called
     * worse than useless: it costs storage and implies a safety property it is not
     * delivering. The row has to reach the device before any screen can show it.
     */
    object Conflicts : SyncedEntity<SyncConflict> {
        override val table = "sync_conflict"

        override fun identify(entity: SyncConflict) = entity.id.value

        override fun studioOf(entity: SyncConflict) = entity.studioId.value

        override fun versionOf(entity: SyncConflict) = entity.audit.version

        override fun deletedAtOf(entity: SyncConflict) = entity.audit.deletedAt?.toEpochMilliseconds()

        override fun read(rows: ResultSet): SyncConflict =
            SyncConflict(
                id = SyncConflictId(rows.getString("id")),
                studioId = StudioId(rows.getString("studio_id")),
                entityTable = rows.getString("entity_table"),
                entityId = rows.getString("entity_id"),
                losingPayload = rows.getString("losing_payload"),
                winningPayload = rows.getString("winning_payload"),
                detectedAt = Instant.fromEpochMilliseconds(rows.getLong("detected_at")),
                resolvedAt = rows.getNullableLong("resolved_at")?.let(Instant::fromEpochMilliseconds),
                audit = rows.audit(),
            )

        override fun encode(entity: SyncConflict) = payloadJson.encodeToString(entity)

        override fun upsert(
            connection: Connection,
            entity: SyncConflict,
            version: Int,
        ): Unit = error("conflicts are raised by the server and never pushed to it")
    }

    companion object {
        /**
         * Every synchronised entity, in the order a device must apply them: parents before
         * children, conflicts last because they refer to rows that should exist first.
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
                CrewMembers,
                Deliverables,
                Invoices,
                Payments,
                Conflicts,
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
