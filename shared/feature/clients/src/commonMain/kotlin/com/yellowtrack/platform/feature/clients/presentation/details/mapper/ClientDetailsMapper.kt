package com.yellowtrack.platform.feature.clients.presentation.details.mapper

import com.yellowtrack.platform.core.common.money.formatted
import com.yellowtrack.platform.core.common.time.DateFormats
import com.yellowtrack.platform.core.model.client.ClientContactRole
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.feature.clients.presentation.details.model.BookingSummary
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientDetailsModel
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientRemoval
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientSessionHistoryItem
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientUpcomingSession
import com.yellowtrack.platform.feature.clients.presentation.details.model.NewProject
import com.yellowtrack.platform.feature.clients.presentation.list.mapper.initials
import com.yellowtrack.platform.feature.clients.presentation.model.NewClient
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import com.yellowtrack.platform.core.model.client.Client as DomainClient
import com.yellowtrack.platform.core.model.session.Session as DomainSession
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientContact as ContactDetails

internal fun DomainClient.toClientDetailsModel(
    sessions: List<DomainSession>,
    projects: List<Project>,
    now: Instant,
): ClientDetailsModel {
    val relevant = sessions.filter { it.status != SessionStatus.Cancelled }

    val upcoming =
        relevant
            .filter { it.startsAt > now }
            .minByOrNull(DomainSession::startsAt)

    val history =
        relevant
            .filter { it.startsAt <= now }
            .sortedByDescending(DomainSession::startsAt)

    return ClientDetailsModel(
        id = id,
        displayName = displayName,
        initials = initials(),
        tags = tags,
        contact = toContactDetails(),
        upcomingSession = upcoming?.toUpcomingSession(),
        sessionHistory = history.map { it.toHistoryItem() },
        // Newest enquiry first: the job most recently asked about is the one being
        // discussed, and older bookings are history to scroll to.
        bookings =
            projects
                .sortedByDescending { it.enquiredAt ?: it.audit.createdAt }
                .map { it.toBookingSummary() },
        notes = notes?.lines().orEmpty().filter(String::isNotBlank),
        // Measured against the bookings themselves rather than the sessions above, which
        // are filtered to the ones worth showing: a client whose only booking was
        // cancelled still has a booking, and removing the account would orphan it.
        removal =
            when {
                projects.isEmpty() -> ClientRemoval.Available
                else -> ClientRemoval.HeldByBookings(projects.size)
            },
        editable = toEditableForm(),
    )
}

/** The account's own values, as the form holds them. */
private fun DomainClient.toEditableForm(): NewClient =
    NewClient(
        accountName = accountName,
        accountType = accountType,
        contactFirstName = primaryContact?.firstName.orEmpty(),
        contactLastName = primaryContact?.lastName.orEmpty(),
        company = primaryContact?.company.orEmpty(),
        email = primaryContact?.primaryEmail.orEmpty(),
        phone = primaryContact?.primaryPhone.orEmpty(),
        notes = notes.orEmpty(),
    )

/**
 * Flattens the account's people into the single contact card the detail screen shows.
 *
 * The primary contact supplies the phone and email; the company is taken from whichever
 * contact carries one, since on a commercial account that may be the billing contact
 * rather than the person who briefs the shoot.
 */
private fun DomainClient.toContactDetails(): ContactDetails =
    ContactDetails(
        phone = primaryContact?.primaryPhone,
        email = primaryContact?.primaryEmail,
        instagram = null,
        company =
            contacts.firstNotNullOfOrNull { it.contact.company }
                ?: contactsInRole(ClientContactRole.Billing).firstNotNullOfOrNull { it.company },
    )

private fun DomainSession.toUpcomingSession(): ClientUpcomingSession {
    val zone = TimeZone.of(timeZoneId)

    return ClientUpcomingSession(
        title = title,
        date = DateFormats.dayAndDate(startsAt, zone),
        time = DateFormats.timeOfDay(startsAt, zone),
        location = locationName,
    )
}

private fun DomainSession.toHistoryItem(): ClientSessionHistoryItem =
    ClientSessionHistoryItem(
        title = title,
        date = DateFormats.fullDate(startsAt, TimeZone.of(timeZoneId)),
    )

private fun Project.toBookingSummary(): BookingSummary =
    BookingSummary(
        id = id,
        name = name,
        serviceLine = serviceLine.name,
        status = status,
        value = contractValue?.formatted(),
        // The status already says "Booked"; this says since when, without repeating it.
        bookedLabel =
            bookedAt?.let { "since ${DateFormats.shortDate(it, TimeZone.currentSystemDefault())}" },
        editable =
            NewProject(
                name = name,
                serviceLine = serviceLine,
                status = status,
                contractValue = contractValue?.toPlainString().orEmpty(),
                notes = notes.orEmpty(),
            ),
    )
