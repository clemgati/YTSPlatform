package com.yellowtrack.platform.core.data

import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.database.DatabaseDriverFactory
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.database.YellowTrackDatabase
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
 * A fresh in-memory database per test, with foreign keys enforced as they now are in
 * production.
 *
 * "As in production" was not true until 0.7.0 — no real driver set the pragma, so the tests
 * were stricter than the thing they tested, and the one hazard that difference hid was a
 * child arriving a page before its parent.
 */
class InMemoryDatabaseDriverFactory : DatabaseDriverFactory {
    override suspend fun create(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        YellowTrackDatabase.Schema.awaitCreate(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
        return driver
    }
}

fun testDatabaseProvider(): DatabaseProvider = DatabaseProvider(InMemoryDatabaseDriverFactory())

val TEST_STUDIO_ID: StudioId = StudioId("00000000-0000-7000-8000-000000000001")

/** 2026-06-13T14:00:00Z. */
val TEST_NOW: Instant = Instant.fromEpochMilliseconds(1_781_100_000_000)

object Fixtures {
    fun contact(
        firstName: String = "Sarah",
        lastName: String = "Johnson",
        company: String? = null,
        emails: List<ContactMethod> = listOf(ContactMethod("sarah@example.com")),
        phones: List<ContactMethod> = listOf(ContactMethod("+1 555 0100")),
    ): Contact =
        Contact(
            id = ContactId.new(),
            studioId = TEST_STUDIO_ID,
            firstName = firstName,
            lastName = lastName,
            company = company,
            emails = emails,
            phones = phones,
            audit = AuditMetadata.createdAt(TEST_NOW),
        )

    fun client(
        accountName: String = "Sarah & Michael Johnson",
        accountType: ClientAccountType = ClientAccountType.Couple,
        contacts: List<ClientContact> = emptyList(),
        tags: List<String> = emptyList(),
    ): Client =
        Client(
            id = ClientId.new(),
            studioId = TEST_STUDIO_ID,
            accountName = accountName,
            accountType = accountType,
            contacts = contacts,
            tags = tags,
            audit = AuditMetadata.createdAt(TEST_NOW),
        )

    fun couple(accountName: String = "Sarah & Michael Johnson"): Client =
        client(
            accountName = accountName,
            contacts =
                listOf(
                    ClientContact(contact(firstName = "Sarah"), ClientContactRole.Primary),
                    ClientContact(contact(firstName = "Michael"), ClientContactRole.Partner),
                ),
        )

    fun project(
        clientId: ClientId,
        name: String = "Johnson Wedding",
        serviceLine: ServiceLine = ServiceLine.Wedding,
        status: ProjectStatus = ProjectStatus.Booked,
        contractValue: Money? = Money.ofMajor(4_500, CurrencyCode.USD),
    ): Project =
        Project(
            id = ProjectId.new(),
            studioId = TEST_STUDIO_ID,
            clientId = clientId,
            name = name,
            serviceLine = serviceLine,
            status = status,
            contractValue = contractValue,
            audit = AuditMetadata.createdAt(TEST_NOW),
        )

    fun session(
        projectId: ProjectId,
        title: String = "Wedding Day",
        kind: SessionKind = SessionKind.Shoot,
        status: SessionStatus = SessionStatus.Confirmed,
        startsAt: Instant = TEST_NOW,
        durationMinutes: Int = 600,
    ): Session =
        Session(
            id = SessionId.new(),
            studioId = TEST_STUDIO_ID,
            projectId = projectId,
            title = title,
            kind = kind,
            status = status,
            startsAt = startsAt,
            endsAt = Instant.fromEpochMilliseconds(startsAt.toEpochMilliseconds() + durationMinutes * 60_000L),
            timeZoneId = "America/New_York",
            locationName = "Harborline Estate",
            audit = AuditMetadata.createdAt(TEST_NOW),
        )
}
