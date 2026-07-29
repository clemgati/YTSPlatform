package com.yellowtrack.platform.feature.clients.presentation.details.mapper

import com.yellowtrack.platform.core.common.time.DateFormats
import com.yellowtrack.platform.core.model.client.ClientContactRole
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientDetailsModel
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientSessionHistoryItem
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientUpcomingSession
import com.yellowtrack.platform.feature.clients.presentation.list.mapper.initials
import com.yellowtrack.platform.feature.clients.presentation.model.NewClient
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import com.yellowtrack.platform.core.model.client.Client as DomainClient
import com.yellowtrack.platform.core.model.session.Session as DomainSession
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientContact as ContactDetails

internal fun DomainClient.toClientDetailsModel(
    sessions: List<DomainSession>,
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
        notes = notes?.lines().orEmpty().filter(String::isNotBlank),
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
