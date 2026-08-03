package com.yellowtrack.platform.feature.dashboard.presentation.mapper

import com.yellowtrack.platform.core.common.time.DateFormats
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.lead.Lead
import com.yellowtrack.platform.core.model.lead.LeadSource
import com.yellowtrack.platform.core.model.lead.LeadStatus
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardClient
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardEnquiry
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSession
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardStudioStatus
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSummary
import kotlinx.datetime.TimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Builds the dashboard from the domain.
 *
 * The dashboard aggregates across clients, projects, and sessions. It reads all three
 * from `core:data` rather than from the clients or sessions features, because features
 * must not depend on one another.
 */
internal fun toDashboardSummary(
    todaysSessions: List<Session>,
    projects: List<Project>,
    clients: List<Client>,
    studioStatus: DashboardStudioStatus,
    enquiriesAwaitingReply: List<Lead> = emptyList(),
    allEnquiries: List<Lead> = emptyList(),
    now: Instant,
): DashboardSummary {
    val clientsById = clients.associateBy { it.id }
    val projectsById = projects.associateBy { it.id }

    return DashboardSummary(
        todaysSessions =
            todaysSessions
                .filter { it.status != SessionStatus.Cancelled }
                .map { session ->
                    DashboardSession(
                        // Session → project → client. A session has no direct client link,
                        // because the booking, not the shoot day, is what a client owns.
                        clientName =
                            projectsById[session.projectId]
                                ?.let { clientsById[it.clientId]?.displayName }
                                .orEmpty(),
                        title = session.title,
                        // Rendered in the zone the session happens in, not the device's.
                        time = DateFormats.timeOfDay(session.startsAt, TimeZone.of(session.timeZoneId)),
                    )
                },
        recentClients =
            clients
                .sortedByDescending { it.audit.updatedAt }
                .take(MAX_RECENT_CLIENTS)
                .map { DashboardClient(name = it.displayName) },
        studioStatus = studioStatus,
        todayLabel = DateFormats.dayAndDate(now, TimeZone.currentSystemDefault()),
        enquiriesAwaitingReply =
            enquiriesAwaitingReply
                .take(MAX_WAITING_ENQUIRIES)
                .map { lead ->
                    val waiting = lead.timeWaiting(now)

                    DashboardEnquiry(
                        id = lead.id,
                        name = lead.name,
                        source = lead.source.readableLabel,
                        waitingLabel = waiting.describe(),
                        // A day is the point past which an enquiry is measurably less
                        // likely to book; most bookings go to whoever replied first.
                        isUrgent = waiting != null && waiting >= URGENT_AFTER,
                    )
                },
        // Newest first, and not truncated. This is where an enquiry is found again once it
        // has left the list above, so a cap would put the oldest mistakes out of reach.
        allEnquiries =
            allEnquiries
                .sortedByDescending { it.receivedAt ?: it.audit.createdAt }
                .map { lead ->
                    DashboardEnquiry(
                        id = lead.id,
                        name = lead.name,
                        source = lead.source.readableLabel,
                        waitingLabel = lead.timeWaiting(now).describe(),
                        isUrgent = false,
                        statusLabel = lead.statusLabel,
                    )
                },
    )
}

/**
 * Where the enquiry got to, said as a studio would say it.
 *
 * "Replied" rather than the stored status for anything answered but undecided: the
 * distinction between Contacted, ConsultScheduled and ProposalSent matters while working
 * the enquiry and not at all when looking for one to delete.
 */
private val Lead.statusLabel: String
    get() =
        when {
            status == LeadStatus.Won -> "Won"
            status == LeadStatus.Lost -> "Lost"
            firstResponseAt != null -> "Replied"
            else -> "Awaiting a reply"
        }

private fun Duration?.describe(): String =
    when {
        this == null -> "—"
        this < 1.hours -> "${inWholeMinutes.coerceAtLeast(1)} min"
        this < 1.days -> "$inWholeHours hr"
        else -> "$inWholeDays ${if (inWholeDays == 1L) "day" else "days"}"
    }

/** "ClientReferral" is a database value, not something to show a person. */
private val LeadSource.readableLabel: String
    get() =
        when (this) {
            LeadSource.Instagram -> "Instagram"
            LeadSource.TikTok -> "TikTok"
            LeadSource.Website -> "Website"
            LeadSource.GoogleSearch -> "Google search"
            LeadSource.ClientReferral -> "Client referral"
            LeadSource.VendorReferral -> "Vendor referral"
            LeadSource.RepeatClient -> "Repeat client"
            LeadSource.Directory -> "Directory"
            LeadSource.WalkIn -> "Walk-in"
            LeadSource.Other -> "Other"
        }

private const val MAX_RECENT_CLIENTS = 3
private const val MAX_WAITING_ENQUIRIES = 5
private val URGENT_AFTER = 1.days
