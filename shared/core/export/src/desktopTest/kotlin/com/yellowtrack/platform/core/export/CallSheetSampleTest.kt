package com.yellowtrack.platform.core.export

import com.yellowtrack.platform.core.common.solar.GeoCoordinates
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.core.model.crew.CrewRole
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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Writes a real call sheet to disk so a person can open it.
 *
 * This document is read by someone who has never seen the application, on a phone, the
 * night before a wedding. The only way to know it reads well is to look at it, and the
 * assertion here is deliberately weak — it exists so the files get written.
 *
 * Set `-Dyellowtrack.render.dir` to choose where they land.
 */
class CallSheetSampleTest {
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
