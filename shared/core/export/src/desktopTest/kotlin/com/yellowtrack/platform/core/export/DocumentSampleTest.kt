package com.yellowtrack.platform.core.export

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.solar.GeoCoordinates
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.core.model.crew.CrewRole
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
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.core.model.shot.Shot
import com.yellowtrack.platform.core.model.shot.ShotId
import com.yellowtrack.platform.core.model.studio.StudioProfile
import com.yellowtrack.platform.core.model.studio.StudioProfileId
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Writes real documents to disk so a person can open them.
 *
 * This document is read by someone who has never seen the application, on a phone, the
 * night before a wedding. The only way to know it reads well is to look at it, and the
 * assertion here is deliberately weak — it exists so the files get written.
 *
 * Set `-Dyellowtrack.render.dir` to choose where they land.
 */
class DocumentSampleTest {
    private val zone = TimeZone.of("Europe/London")
    private val studioId = StudioId("studio-1")
    private val sessionId = SessionId.new()
    private val projectId = ProjectId.new()
    private val clientId = ClientId.new()
    private val createdAt = Instant.fromEpochMilliseconds(1_781_100_000_000)

    private fun at(time: String) = LocalDateTime.parse("2026-08-15T$time").toInstant(zone)

    @Test
    fun `writes a wedding call sheet`() {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()

        val sheet =
            buildCallSheet(
                session = session(),
                project = project(),
                client = client(),
                crew = crew(),
                shots = shots(),
                studio = studioProfile(),
            )

        val html = File(outputDir, "call-sheet.html")
        val text = File(outputDir, "call-sheet.txt")
        html.writeText(sheet.toHtml())
        text.writeText(sheet.toPlainText())

        assertTrue(html.length() > 0)
        assertTrue(text.length() > 0)
        println("Wrote ${html.absolutePath}")
        println("Wrote ${text.absolutePath}")
    }

    @Test
    fun `writes an invoice`() {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()

        val sheet =
            buildInvoice(
                invoice = invoice(),
                project = project(),
                client = client(),
                studio = studioProfile(),
                now = createdAt,
                zone = zone,
            )

        val html = File(outputDir, "invoice.html")
        val text = File(outputDir, "invoice.txt")
        html.writeText(sheet.toHtml())
        text.writeText(sheet.toPlainText())

        assertTrue(html.length() > 0)
        println("Wrote ${html.absolutePath}")
        println("Wrote ${text.absolutePath}")
    }

    private fun studioProfile() =
        StudioProfile(
            id = StudioProfileId.new(),
            studioId = studioId,
            name = "Yellow Track Studios",
            address = "12 Harbour Road\nFalmouth\nTR11 3AA",
            email = "hello@yellowtrack.example",
            phone = "07700 900000",
            website = "yellowtrack.example",
            taxNumber = "GB123456789",
            paymentInstructions =
                "Bank transfer to Yellow Track Studios\nSort code 00-00-00 · Account 12345678\n" +
                    "Please quote the invoice number.",
            documentFooter = "Payment due within 14 days. Late payments accrue interest at 8% above base rate.",
            audit = AuditMetadata.createdAt(createdAt),
        )

    private fun invoice() =
        Invoice(
            id = InvoiceId.new(),
            studioId = studioId,
            projectId = projectId,
            number = "INV-004",
            kind = InvoiceKind.Balance,
            status = InvoiceStatus.Sent,
            currency = CurrencyCode.USD,
            lines =
                listOf(
                    LineItem("Wedding coverage, ten hours", Money(400_000L, CurrencyCode.USD)),
                    LineItem("Second shooter", Money(75_000L, CurrencyCode.USD)),
                    LineItem(
                        "Fine art album, 30 spreads",
                        Money(120_000L, CurrencyCode.USD),
                        taxRateBasisPoints = 2_000,
                    ),
                    LineItem("Retouched images beyond the package", Money(5_000L, CurrencyCode.USD), quantity = 24),
                ),
            payments =
                listOf(
                    Payment(
                        id = PaymentId.new(),
                        studioId = studioId,
                        invoiceId = InvoiceId.new(),
                        amount = Money(250_000L, CurrencyCode.USD),
                        paidAt = createdAt - 60.days,
                        method = PaymentMethod.BankTransfer,
                        audit = AuditMetadata.createdAt(createdAt),
                    ),
                ),
            issuedAt = createdAt,
            dueAt = createdAt + 14.days,
            notes = "Thank you — it was a wonderful day to photograph.",
            audit = AuditMetadata.createdAt(createdAt),
        )

    private fun session() =
        Session(
            id = sessionId,
            studioId = studioId,
            projectId = projectId,
            title = "Wedding day",
            kind = SessionKind.Shoot,
            status = SessionStatus.Confirmed,
            startsAt = at("14:00"),
            endsAt = at("23:00"),
            timeZoneId = zone.id,
            locationName = "Thornbury Manor",
            locationAddress = "Thornbury, Cornwall, TR12 7QP",
            coordinates = GeoCoordinates(latitude = 50.2, longitude = -5.5),
            callTime = at("12:30"),
            notes = "Family formals on the south lawn before the light goes.\nParking is through the second gate.",
            audit = AuditMetadata.createdAt(createdAt),
        )

    private fun project() =
        Project(
            id = projectId,
            studioId = studioId,
            clientId = clientId,
            name = "Johnson Wedding",
            serviceLine = ServiceLine.Wedding,
            status = ProjectStatus.Booked,
            audit = AuditMetadata.createdAt(createdAt),
        )

    private fun client() =
        Client(
            id = clientId,
            studioId = studioId,
            accountName = "Sarah & Michael Johnson",
            accountType = ClientAccountType.Couple,
            audit = AuditMetadata.createdAt(createdAt),
        )

    private fun crew() =
        listOf(
            crewMember("Priya Shah", CrewRole.MakeUp, "07700 900123", at("09:00")),
            crewMember("Sam Ellis", CrewRole.SecondShooter, "07700 900456", at("13:30")),
            crewMember("Alex Reed", CrewRole.Videographer, null, null),
        )

    private fun crewMember(
        name: String,
        role: CrewRole,
        phone: String?,
        callTime: Instant?,
    ) = CrewMember(
        id = CrewMemberId.new(),
        studioId = studioId,
        sessionId = sessionId,
        name = name,
        role = role,
        phone = phone,
        callTime = callTime,
        audit = AuditMetadata.createdAt(createdAt),
    )

    private fun shots() =
        listOf(
            shot("Bride with both parents", "Bride's family", "Sarah + Mum and Dad", 0),
            shot("Bride with her grandmother", "Bride's family", "Grandma Ruth", 1),
            shot("Groom with his brothers", "Groom's side", "Michael + Tom + Alex", 0),
            shot("Detail of the rings", null, null, 0),
        )

    private fun shot(
        description: String,
        group: String?,
        people: String?,
        position: Int,
    ) = Shot(
        id = ShotId.new(),
        studioId = studioId,
        sessionId = sessionId,
        description = description,
        group = group,
        people = people,
        position = position,
        audit = AuditMetadata.createdAt(createdAt),
    )
}
