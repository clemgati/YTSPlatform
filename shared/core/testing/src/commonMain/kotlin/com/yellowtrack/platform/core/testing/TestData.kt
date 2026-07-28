package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientContact
import com.yellowtrack.platform.core.model.client.ClientContactRole
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.contact.ContactId
import com.yellowtrack.platform.core.model.contact.ContactMethod
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import kotlin.time.Instant

/**
 * Builders for domain objects in tests.
 *
 * Every parameter has a default, so a test states only the fields it is actually about.
 */
object TestData {
    val studioId: StudioId = StudioId("00000000-0000-7000-8000-000000000001")

    fun contact(
        id: ContactId = ContactId.new(),
        firstName: String = "Sarah",
        lastName: String = "Johnson",
        company: String? = null,
        emails: List<ContactMethod> = listOf(ContactMethod("sarah@example.com")),
        phones: List<ContactMethod> = listOf(ContactMethod("+1 555 0100")),
        now: Instant = TestAppClock.DEFAULT_NOW,
    ): Contact =
        Contact(
            id = id,
            studioId = studioId,
            firstName = firstName,
            lastName = lastName,
            company = company,
            emails = emails,
            phones = phones,
            audit = AuditMetadata.createdAt(now),
        )

    fun client(
        id: ClientId = ClientId.new(),
        accountName: String = "Sarah & Michael Johnson",
        accountType: ClientAccountType = ClientAccountType.Couple,
        contacts: List<ClientContact> = emptyList(),
        tags: List<String> = emptyList(),
        now: Instant = TestAppClock.DEFAULT_NOW,
    ): Client =
        Client(
            id = id,
            studioId = studioId,
            accountName = accountName,
            accountType = accountType,
            contacts = contacts,
            tags = tags,
            audit = AuditMetadata.createdAt(now),
        )

    /** A wedding couple: two contacts of equal standing on one account. */
    fun couple(
        accountName: String = "Sarah & Michael Johnson",
        now: Instant = TestAppClock.DEFAULT_NOW,
    ): Client =
        client(
            accountName = accountName,
            accountType = ClientAccountType.Couple,
            contacts =
                listOf(
                    ClientContact(
                        contact = contact(firstName = "Sarah", lastName = "Johnson", now = now),
                        role = ClientContactRole.Primary,
                    ),
                    ClientContact(
                        contact = contact(firstName = "Michael", lastName = "Johnson", now = now),
                        role = ClientContactRole.Partner,
                    ),
                ),
            now = now,
        )

    fun project(
        id: ProjectId = ProjectId.new(),
        clientId: ClientId,
        name: String = "Johnson Wedding",
        serviceLine: ServiceLine = ServiceLine.Wedding,
        status: ProjectStatus = ProjectStatus.Booked,
        contractValue: Money? = Money.ofMajor(4_500, CurrencyCode.USD),
        now: Instant = TestAppClock.DEFAULT_NOW,
    ): Project =
        Project(
            id = id,
            studioId = studioId,
            clientId = clientId,
            name = name,
            serviceLine = serviceLine,
            status = status,
            contractValue = contractValue,
            audit = AuditMetadata.createdAt(now),
        )

    fun session(
        id: SessionId = SessionId.new(),
        projectId: ProjectId,
        title: String = "Wedding Day",
        kind: SessionKind = SessionKind.Shoot,
        status: SessionStatus = SessionStatus.Confirmed,
        startsAt: Instant = TestAppClock.DEFAULT_NOW,
        durationMinutes: Int = 600,
        timeZoneId: String = "America/New_York",
        locationName: String? = "Harborline Estate",
        now: Instant = TestAppClock.DEFAULT_NOW,
    ): Session =
        Session(
            id = id,
            studioId = studioId,
            projectId = projectId,
            title = title,
            kind = kind,
            status = status,
            startsAt = startsAt,
            endsAt = Instant.fromEpochMilliseconds(startsAt.toEpochMilliseconds() + durationMinutes * 60_000L),
            timeZoneId = timeZoneId,
            locationName = locationName,
            audit = AuditMetadata.createdAt(now),
        )
}
