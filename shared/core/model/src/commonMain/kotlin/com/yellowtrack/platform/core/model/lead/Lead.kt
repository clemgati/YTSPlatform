package com.yellowtrack.platform.core.model.lead

import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.service.ServiceLine
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * An enquiry, before it is a booking.
 *
 * A lead deliberately carries its own name and contact details rather than requiring a
 * [com.yellowtrack.platform.core.model.contact.Contact]. Someone messaging "how much for
 * a wedding in June?" is not yet a client, and forcing every enquiry to create a client
 * account fills the client list with people who never booked — which then corrupts every
 * count and conversion figure derived from it.
 *
 * Converting a won lead is what creates the contact, the client, and the project.
 *
 * @param firstResponseAt when the studio first replied. The single strongest predictor of
 *   whether an enquiry books, and the reason this field exists as a first-class column
 *   rather than being inferred from a message log.
 */
@Serializable
data class Lead(
    val id: LeadId,
    override val studioId: StudioId,
    val name: String,
    val source: LeadSource,
    val status: LeadStatus,
    val receivedAt: Instant,
    val email: String? = null,
    val phone: String? = null,
    val firstResponseAt: Instant? = null,
    val serviceLine: ServiceLine? = null,
    val desiredDate: LocalDate? = null,
    val budgetLow: Money? = null,
    val budgetHigh: Money? = null,
    /** Who sent them, when [source] is a referral. Worth thanking, and worth tracking. */
    val referredBy: String? = null,
    val lostReason: String? = null,
    /** Set when the lead is won and becomes a booking. */
    val convertedProjectId: ProjectId? = null,
    val convertedClientId: ClientId? = null,
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    /** How long the studio took to reply. Null while still unanswered. */
    val responseTime: Duration?
        get() = firstResponseAt?.let { it - receivedAt }

    /** An enquiry that is still open and has never been replied to. */
    val isAwaitingFirstResponse: Boolean
        get() = firstResponseAt == null && status.isOpen

    /** How long an unanswered enquiry has been waiting, for surfacing on the dashboard. */
    fun timeWaiting(now: Instant): Duration? = if (isAwaitingFirstResponse) now - receivedAt else null

    val budgetRange: ClosedRange<Long>?
        get() =
            when {
                budgetLow != null && budgetHigh != null -> budgetLow.minorUnits..budgetHigh.minorUnits
                else -> null
            }
}
