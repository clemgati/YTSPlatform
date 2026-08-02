package com.yellowtrack.platform.core.model.sync

import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientContactLink
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.delivery.Deliverable
import com.yellowtrack.platform.core.model.expense.Expense
import com.yellowtrack.platform.core.model.expense.Mileage
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.PackingEntry
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.Payment
import com.yellowtrack.platform.core.model.lead.Lead
import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.session.Session
import kotlinx.serialization.Serializable

/**
 * The synchronisation contract, defined once and compiled into both sides.
 *
 * It was defined twice until this file existed — once in the server's routes and once in
 * `core:data` — which is precisely the arrangement ADR 0007 chose a Kotlin server to avoid.
 * Two definitions of one contract agree right up until somebody edits one of them, and the
 * disagreement shows up as a field that silently stops crossing rather than as a build
 * failure.
 *
 * These carry `core:model` types rather than opaque payloads. A generic envelope would
 * extend to the remaining eighteen entities without a code change and would throw away the
 * only thing that dependency buys: adding a field to `Session` is a compile error in both
 * halves at once. Growing this by one list per entity is the cost of keeping that.
 *
 * Transport-shaped rather than domain-shaped, and kept in its own package for that reason —
 * ADR 0008 was wary of the domain model acquiring concerns about how data moves. These
 * describe an envelope, not a photography business.
 */
@Serializable
data class SyncPullResponse(
    /** Where the device should resume. Unchanged when nothing came back. */
    val cursor: Long,
    /** Whether more remains beyond this page, so the device knows to come again. */
    val hasMore: Boolean,
    val clients: List<Client> = emptyList(),
    /**
     * People, and their attachments to accounts — ADR 0008 decision 5.
     *
     * A `Client` arrives with no contacts. These are what carry them, as rows with their
     * own ids, so two devices that each added a contact keep both.
     */
    val contacts: List<Contact> = emptyList(),
    val clientContactLinks: List<ClientContactLink> = emptyList(),
    val projects: List<Project> = emptyList(),
    val sessions: List<Session> = emptyList(),
    /**
     * Invoices, and the money against them — ADR 0008 decision 5 again.
     *
     * An invoice arrives with no payments. Its `lines` do travel with it, because they are a
     * JSON column rather than rows and cannot union; a lost line is retyped from the quote,
     * whereas a lost payment is found during a tax return, if at all.
     */
    val invoices: List<Invoice> = emptyList(),
    val payments: List<Payment> = emptyList(),
    /** Children of a session and of a project respectively, and rows in their own right. */
    val crewMembers: List<CrewMember> = emptyList(),
    val deliverables: List<Deliverable> = emptyList(),
    /** Kit and storage, and what was taken or copied where. */
    val gearItems: List<GearItem> = emptyList(),
    val packingEntries: List<PackingEntry> = emptyList(),
    val storageVolumes: List<StorageVolume> = emptyList(),
    val mediaCopies: List<MediaCopy> = emptyList(),
    /** Enquiries in, and money out. */
    val leads: List<Lead> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val mileages: List<Mileage> = emptyList(),
    /**
     * Work reconciliation discarded, travelling down only.
     *
     * There is no matching list on [SyncPushRequest]: the server is the only party that
     * ever sees both versions, so a device asserting a conflict would be claiming something
     * it cannot know.
     */
    val conflicts: List<SyncConflict> = emptyList(),
)

@Serializable
data class SyncPushRequest(
    val clients: List<Client> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val clientContactLinks: List<ClientContactLink> = emptyList(),
    val projects: List<Project> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val crewMembers: List<CrewMember> = emptyList(),
    val deliverables: List<Deliverable> = emptyList(),
    val gearItems: List<GearItem> = emptyList(),
    val packingEntries: List<PackingEntry> = emptyList(),
    val storageVolumes: List<StorageVolume> = emptyList(),
    val mediaCopies: List<MediaCopy> = emptyList(),
    val leads: List<Lead> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val mileages: List<Mileage> = emptyList(),
) {
    val isEmpty: Boolean
        get() =
            clients.isEmpty() &&
                contacts.isEmpty() &&
                clientContactLinks.isEmpty() &&
                projects.isEmpty() &&
                sessions.isEmpty() &&
                invoices.isEmpty() &&
                payments.isEmpty() &&
                crewMembers.isEmpty() &&
                deliverables.isEmpty() &&
                gearItems.isEmpty() &&
                packingEntries.isEmpty() &&
                storageVolumes.isEmpty() &&
                mediaCopies.isEmpty() &&
                leads.isEmpty() &&
                expenses.isEmpty() &&
                mileages.isEmpty()
}

/** What became of one pushed row. */
@Serializable
enum class SyncPushOutcome {
    /** Stored. Nothing was discarded. */
    Applied,

    /**
     * Stored, and something was discarded to store it — or refused because a tombstone beat
     * it. Either way a conflict now holds both versions.
     */
    Conflicted,

    /** Not stored, and nothing was lost, because the push should not have been made. */
    Rejected,
}

@Serializable
data class SyncPushResult(
    val entityTable: String,
    val entityId: String,
    val outcome: SyncPushOutcome,
    /** The version now on the server, so the device can stop being behind. */
    val version: Int,
    val detail: String? = null,
)

@Serializable
data class SyncPushResponse(
    val results: List<SyncPushResult> = emptyList(),
) {
    /** So a device can say "three of your changes were also made elsewhere" without counting. */
    val conflicted: Int get() = results.count { it.outcome == SyncPushOutcome.Conflicted }
}
