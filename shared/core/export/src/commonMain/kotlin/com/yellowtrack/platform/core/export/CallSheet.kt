package com.yellowtrack.platform.core.export

import com.yellowtrack.platform.core.common.solar.SolarCalculator
import com.yellowtrack.platform.core.common.solar.SunWindow
import com.yellowtrack.platform.core.common.time.DateFormats
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.crew.CrewRole
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.shot.Shot
import com.yellowtrack.platform.core.model.studio.StudioProfile
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The day, written for the people working it.
 *
 * A call sheet answers four questions and nothing else: where to be, when to be there, who
 * else is coming, and what has been promised. Everything the application knows about the
 * money, the contract, and the client's file is deliberately absent — this document leaves
 * the studio, and a second shooter has no business seeing what the wedding cost.
 *
 * Times render in the shoot's own zone, not the reader's. A destination wedding's call
 * sheet read on a phone still set to London must say the local hour people are due, or it
 * is worse than no call sheet at all.
 */
fun buildCallSheet(
    session: Session,
    project: Project?,
    client: Client?,
    crew: List<CrewMember>,
    shots: List<Shot>,
    studio: StudioProfile? = null,
): Sheet {
    val zone = TimeZone.of(session.timeZoneId)

    return Sheet(
        title = "Call sheet — ${session.title}",
        subtitle =
            listOfNotNull(client?.displayName, project?.name, studio?.name?.takeIf { it.isNotBlank() })
                .distinct()
                .joinToString(" · ")
                .ifBlank { null },
        // A number to ring when someone cannot find the gate. Unlike an invoice, a call
        // sheet with no studio details still works — it goes to people who know who booked
        // them — so this is added when it exists rather than required.
        footer =
            studio
                ?.let { listOfNotNull(it.name.takeIf(String::isNotBlank), it.phone, it.email) }
                ?.takeIf { it.size > 1 }
                ?.joinToString(" · "),
        sections =
            listOfNotNull(
                daySection(session, zone),
                whereSection(session),
                lightSection(session, zone),
                crewSection(crew, zone),
                shotsSection(shots),
                notesSection(session),
            ),
    )
}

private fun daySection(
    session: Session,
    zone: TimeZone,
) = SheetSection(
    heading = "The day",
    blocks =
        listOf(
            SheetBlock.Facts(
                listOfNotNull(
                    SheetFact("Date", DateFormats.dayAndDate(session.startsAt, zone)),
                    SheetFact("Shooting", DateFormats.timeRange(session.startsAt, session.endsAt, zone)),
                    // The one figure a person scans for, so it is the one emphasised.
                    session.callTime?.let {
                        SheetFact("Call time", DateFormats.timeOfDay(it, zone), isEmphasised = true)
                    },
                    // Always stated, unlike on the screen. A screen is read by the person
                    // who set the times; this is read by a second shooter flying in, and
                    // conditioning the line on the *sender's* device zone would decide
                    // what a stranger needs to know from where the laptop happened to be.
                    SheetFact("All times", "${session.timeZoneId} — the zone the shoot is in"),
                ),
            ),
        ),
)

private fun whereSection(session: Session): SheetSection? {
    val facts =
        listOfNotNull(
            session.locationName?.let { SheetFact("Venue", it) },
            session.locationAddress?.let { SheetFact("Address", it) },
            // Coordinates are on the sheet because a rural venue's postal address is
            // routinely a mile from the gate people are meant to drive through.
            session.coordinates?.let { SheetFact("Coordinates", "${it.latitude}, ${it.longitude}") },
        )

    return if (facts.isEmpty()) null else SheetSection("Where", listOf(SheetBlock.Facts(facts)))
}

/**
 * The light, for the days it can be worked out.
 *
 * On the sheet rather than in the app only, because the person who needs to know that the
 * golden hour ends at 8:14 is standing in a field deciding whether to move forty people.
 */
private fun lightSection(
    session: Session,
    zone: TimeZone,
): SheetSection? {
    val coordinates = session.coordinates ?: return null
    val events = SolarCalculator.eventsOn(session.startsAt.toLocalDateTime(zone).date, coordinates)

    // Both golden hours are printed — a photographer may arrive early, and a window
    // omitted cannot be reconsidered on the day. Only the one that falls inside the hours
    // being shot is emphasised: on an afternoon wedding, a 5:49 AM window in bold makes
    // the 7:55 PM window harder to find, which is the one decision the sheet exists for.
    fun goldenHour(
        label: String,
        window: SunWindow?,
    ) = window?.let {
        SheetFact(
            label = label,
            value = DateFormats.timeRange(it.start, it.end, zone),
            isEmphasised = it.start < session.endsAt && it.end > session.startsAt,
        )
    }

    val facts =
        listOfNotNull(
            events.sunrise?.let { SheetFact("Sunrise", DateFormats.timeOfDay(it, zone)) },
            goldenHour("Morning golden hour", events.morningGoldenHour),
            goldenHour("Evening golden hour", events.eveningGoldenHour),
            events.sunset?.let { SheetFact("Sunset", DateFormats.timeOfDay(it, zone)) },
        )

    if (facts.isEmpty()) {
        // Inside the Arctic circle there is genuinely no sunrise to print, and a blank
        // section would read as a failure rather than as the answer.
        val note =
            when {
                events.isPolarDay -> "The sun does not set here on this date."
                events.isPolarNight -> "The sun does not rise here on this date."
                else -> return null
            }

        return SheetSection("Light", listOf(SheetBlock.Paragraphs(listOf(note))))
    }

    return SheetSection("Light", listOf(SheetBlock.Facts(facts)))
}

private fun crewSection(
    crew: List<CrewMember>,
    zone: TimeZone,
): SheetSection {
    if (crew.isEmpty()) {
        return SheetSection(
            heading = "Crew",
            blocks = listOf(SheetBlock.Absent("Nobody else is booked for this day.")),
        )
    }

    return SheetSection(
        heading = "Crew",
        blocks =
            listOf(
                SheetBlock.Entries(
                    // Earliest first, because a call sheet is read in the order people
                    // arrive: make-up hours before the photographer, video after.
                    crew
                        .sortedWith(compareBy({ it.callTime ?: DISTANT_FUTURE }, { it.name }))
                        .map { member ->
                            SheetEntry(
                                name = member.name,
                                detail =
                                    listOfNotNull(member.role.label, member.phone)
                                        .joinToString(" · "),
                                trailing =
                                    member.callTime
                                        ?.let { DateFormats.timeOfDay(it, zone) }
                                        ?: "With the crew",
                            )
                        },
                ),
            ),
    )
}

private fun shotsSection(shots: List<Shot>): SheetSection? {
    if (shots.isEmpty()) return null

    val groups =
        shots
            .groupBy { it.groupOrUngrouped }
            .map { (name, inGroup) ->
                SheetGroup(
                    name = name,
                    items =
                        inGroup
                            .sortedBy { it.position }
                            .map { shot ->
                                // The people are the useful half: "Bride with her
                                // grandmother — Grandma Ruth" is what gets called across
                                // a lawn, and the description alone is not.
                                listOfNotNull(shot.description, shot.people).joinToString(" — ")
                            },
                )
            }

    return SheetSection("Shot list", listOf(SheetBlock.Checklist(groups)))
}

private fun notesSection(session: Session): SheetSection? {
    val lines =
        session.notes
            ?.lines()
            .orEmpty()
            .filter(String::isNotBlank)

    return if (lines.isEmpty()) null else SheetSection("Notes", listOf(SheetBlock.Paragraphs(lines)))
}

/** Sorts people with no call time to the end rather than to the front. */
private val DISTANT_FUTURE = kotlin.time.Instant.DISTANT_FUTURE

private val CrewRole.label: String
    get() =
        when (this) {
            CrewRole.SecondShooter -> "Second shooter"
            CrewRole.Assistant -> "Assistant"
            CrewRole.Videographer -> "Videographer"
            CrewRole.MakeUp -> "Hair & make-up"
            CrewRole.Stylist -> "Stylist"
            CrewRole.Planner -> "Planner"
            CrewRole.Venue -> "Venue contact"
            CrewRole.Other -> "Crew"
        }
