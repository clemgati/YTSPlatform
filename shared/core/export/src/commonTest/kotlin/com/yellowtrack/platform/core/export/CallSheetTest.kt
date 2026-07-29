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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The call sheet as the people working the day receive it.
 *
 * A call sheet answers four questions — where, when, who else, and what was promised — and
 * this document leaves the studio, so what it must *not* carry matters as much as what it
 * does.
 */
class CallSheetTest {
    private val zone = TimeZone.of("Europe/London")
    private val studioId = StudioId("studio-1")
    private val sessionId = SessionId("session-1")
    private val projectId = ProjectId("project-1")
    private val clientId = ClientId("client-1")
    private val createdAt = Instant.fromEpochMilliseconds(1_781_100_000_000)

    private fun at(
        date: String,
        time: String,
    ): Instant = LocalDateTime.parse("${date}T$time").toInstant(zone)

    private fun session(
        callTime: Instant? = at("2026-08-15", "12:30"),
        coordinates: GeoCoordinates? = null,
        locationName: String? = "Thornbury Manor",
        locationAddress: String? = "Thornbury, Cornwall",
        notes: String? = null,
        timeZoneId: String = zone.id,
    ) = Session(
        id = sessionId,
        studioId = studioId,
        projectId = projectId,
        title = "Wedding day",
        kind = SessionKind.Shoot,
        status = SessionStatus.Confirmed,
        startsAt = at("2026-08-15", "14:00"),
        endsAt = at("2026-08-15", "23:00"),
        timeZoneId = timeZoneId,
        locationName = locationName,
        locationAddress = locationAddress,
        coordinates = coordinates,
        callTime = callTime,
        notes = notes,
        audit = AuditMetadata.createdAt(createdAt),
    )

    private fun client(name: String = "Sarah & Michael Johnson") =
        Client(
            id = clientId,
            studioId = studioId,
            accountName = name,
            accountType = ClientAccountType.Couple,
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

    private fun crewMember(
        name: String,
        role: CrewRole = CrewRole.SecondShooter,
        phone: String? = null,
        callTime: Instant? = null,
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

    private fun shot(
        description: String,
        group: String? = null,
        people: String? = null,
        position: Int = 0,
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

    private fun sheet(
        session: Session = session(),
        crew: List<CrewMember> = emptyList(),
        shots: List<Shot> = emptyList(),
        client: Client? = client(),
    ) = buildCallSheet(session, project(), client, crew, shots)

    private fun Sheet.section(heading: String): SheetSection? = sections.firstOrNull { it.heading == heading }

    private fun Sheet.facts(heading: String): List<SheetFact> =
        section(heading)
            ?.blocks
            ?.filterIsInstance<SheetBlock.Facts>()
            ?.flatMap { it.facts }
            .orEmpty()

    // --- What must not be on it ------------------------------------------------------------

    @Test
    fun `nothing about the money reaches the people working the day`() {
        val rendered = sheet(crew = listOf(crewMember("Sam Ellis"))).toPlainText()

        listOf("invoice", "quote", "contract", "deposit", "balance", "$", "£").forEach { forbidden ->
            assertFalse(
                rendered.contains(forbidden, ignoreCase = true),
                "a second shooter has no business seeing what the wedding cost; found \"$forbidden\"",
            )
        }
    }

    // --- When ---------------------------------------------------------------------------------

    @Test
    fun `the call time is the figure the sheet leads with`() {
        val callTime = sheet().facts("The day").firstOrNull { it.label == "Call time" }

        assertNotNull(callTime)
        assertTrue(callTime.isEmphasised, "it is the one figure a person scans the sheet for")
    }

    @Test
    fun `a day with no call time simply omits it rather than inventing one`() {
        val facts = sheet(session = session(callTime = null)).facts("The day")

        assertTrue(facts.none { it.label == "Call time" })
        assertTrue(facts.any { it.label == "Shooting" }, "the shooting hours are still stated")
    }

    @Test
    fun `times render in the zone the shoot is in`() {
        val rendered = sheet().toPlainText()

        assertTrue(rendered.contains("2:00 PM"), "the local hour people are due, not the reader's")
    }

    @Test
    fun `the sheet always says which clock it means`() {
        val note = sheet().facts("The day").firstOrNull { it.label == "All times" }

        assertNotNull(note, "the reader is not the person who typed the times in")
        assertTrue(note.value.contains(zone.id))
    }

    @Test
    fun `a shoot in another zone names that zone rather than the sender's`() {
        val elsewhere = session(timeZoneId = "Asia/Tokyo", callTime = null)
        val facts =
            buildCallSheet(elsewhere, project(), client(), emptyList(), emptyList())
                .facts("The day")

        val note = assertNotNull(facts.firstOrNull { it.label == "All times" })
        assertTrue(note.value.contains("Asia/Tokyo"))
    }

    // --- Where ---------------------------------------------------------------------------------

    @Test
    fun `coordinates are on the sheet because a rural address is not the gate`() {
        val facts =
            sheet(session = session(coordinates = GeoCoordinates(50.2, -5.5)))
                .facts("Where")

        assertTrue(facts.any { it.label == "Coordinates" })
    }

    @Test
    fun `a session with no location at all has no Where section`() {
        val nowhere = session(locationName = null, locationAddress = null)

        assertNull(
            sheet(session = nowhere).section("Where"),
            "an empty heading reads as a mistake rather than as an answer",
        )
    }

    // --- Light ------------------------------------------------------------------------------

    @Test
    fun `the golden hours are on the sheet where the place is known`() {
        val facts =
            sheet(session = session(coordinates = GeoCoordinates(50.2, -5.5)))
                .facts("Light")

        assertTrue(
            facts.any { it.label == "Evening golden hour" && it.isEmphasised },
            "the person deciding whether to move forty people is standing in the field",
        )
    }

    @Test
    fun `a window nobody will be there for is printed but not emphasised`() {
        // Shooting 2 PM to 11 PM: the morning golden hour is hours before anyone arrives.
        val facts =
            sheet(session = session(coordinates = GeoCoordinates(50.2, -5.5)))
                .facts("Light")

        val morning = assertNotNull(facts.firstOrNull { it.label == "Morning golden hour" })
        assertFalse(
            morning.isEmphasised,
            "a 5:49 AM window in bold makes the evening one harder to find",
        )
    }

    @Test
    fun `a morning shoot emphasises the morning window`() {
        val morningShoot =
            session(coordinates = GeoCoordinates(50.2, -5.5), callTime = null).copy(
                startsAt = at("2026-08-15", "05:00"),
                endsAt = at("2026-08-15", "09:00"),
            )

        val facts = sheet(session = morningShoot).facts("Light")

        assertTrue(facts.first { it.label == "Morning golden hour" }.isEmphasised)
        assertFalse(facts.first { it.label == "Evening golden hour" }.isEmphasised)
    }

    @Test
    fun `no coordinates means no light section rather than a guess`() {
        assertNull(sheet().section("Light"))
    }

    @Test
    fun `inside the arctic circle the sheet says the sun does not set`() {
        // Tromsø in June: there is genuinely no sunset to print.
        val midnightSun =
            session(coordinates = GeoCoordinates(69.65, 18.96), callTime = null)
                .copy(
                    startsAt = at("2026-06-21", "14:00"),
                    endsAt = at("2026-06-21", "23:00"),
                )

        val light = sheet(session = midnightSun).section("Light")

        assertNotNull(light)
        val lines = light.blocks.filterIsInstance<SheetBlock.Lines>().flatMap { it.lines }
        assertEquals(listOf("The sun does not set here on this date."), lines)
    }

    // --- Crew ---------------------------------------------------------------------------------

    @Test
    fun `crew are listed in the order they arrive`() {
        val sheet =
            sheet(
                crew =
                    listOf(
                        crewMember("Sam Ellis", CrewRole.SecondShooter, callTime = at("2026-08-15", "13:30")),
                        crewMember("Priya Shah", CrewRole.MakeUp, callTime = at("2026-08-15", "09:00")),
                    ),
            )

        val names =
            sheet
                .section("Crew")
                ?.blocks
                ?.filterIsInstance<SheetBlock.Entries>()
                ?.flatMap { it.entries }
                ?.map { it.name }

        assertEquals(
            listOf("Priya Shah", "Sam Ellis"),
            names,
            "make-up is called hours before the photographer, and the sheet is read in arrival order",
        )
    }

    @Test
    fun `someone with no call time is due with everyone else and sorts last`() {
        val sheet =
            sheet(
                crew =
                    listOf(
                        crewMember("Alex Reed", CrewRole.Videographer),
                        crewMember("Priya Shah", CrewRole.MakeUp, callTime = at("2026-08-15", "09:00")),
                    ),
            )

        val entries =
            sheet
                .section("Crew")
                ?.blocks
                ?.filterIsInstance<SheetBlock.Entries>()
                ?.flatMap { it.entries }
                .orEmpty()

        assertEquals(listOf("Priya Shah", "Alex Reed"), entries.map { it.name })
        assertEquals("With the crew", entries.last().trailing)
    }

    @Test
    fun `a day nobody else is working says so rather than showing a blank heading`() {
        val crew = sheet().section("Crew")

        assertNotNull(crew)
        assertEquals(
            listOf("Nobody else is booked for this day."),
            crew.blocks.filterIsInstance<SheetBlock.Absent>().map { it.message },
        )
    }

    // --- Shot list ------------------------------------------------------------------------------

    @Test
    fun `shots stay in their groups and in the order they are worked`() {
        val sheet =
            sheet(
                shots =
                    listOf(
                        shot("Groom with his brothers", "Groom's side", position = 0),
                        shot("Bride with her grandmother", "Bride's family", people = "Grandma Ruth", position = 1),
                        shot("Bride with both parents", "Bride's family", people = "Sarah + Mum and Dad", position = 0),
                    ),
            )

        val groups =
            sheet
                .section("Shot list")
                ?.blocks
                ?.filterIsInstance<SheetBlock.Checklist>()
                ?.flatMap { it.groups }
                .orEmpty()

        assertEquals(listOf("Groom's side", "Bride's family"), groups.map { it.name })
        assertEquals(
            listOf("Bride with both parents — Sarah + Mum and Dad", "Bride with her grandmother — Grandma Ruth"),
            groups.last().items,
            "the people are the useful half — that is what gets called across a lawn",
        )
    }

    @Test
    fun `shots with no group are collected rather than dropped`() {
        val sheet = sheet(shots = listOf(shot("Detail of the rings")))

        val groups =
            sheet
                .section("Shot list")
                ?.blocks
                ?.filterIsInstance<SheetBlock.Checklist>()
                ?.flatMap { it.groups }
                .orEmpty()

        assertEquals(listOf(Shot.UNGROUPED), groups.map { it.name })
    }

    @Test
    fun `no shots promised means no shot list section`() {
        assertNull(sheet().section("Shot list"))
    }

    // --- Heading --------------------------------------------------------------------------------

    @Test
    fun `the subtitle names the client and the booking without repeating itself`() {
        val sheet = sheet(client = client(name = "Johnson Wedding"))

        assertEquals("Johnson Wedding", sheet.subtitle, "the same name twice is not two facts")
    }

    @Test
    fun `a session with no client or studio still has a usable title`() {
        val sheet = buildCallSheet(session(), null, null, emptyList(), emptyList())

        assertEquals("Call sheet — Wedding day", sheet.title)
        assertNull(sheet.subtitle)
    }
}
