package com.yellowtrack.platform.feature.sessions.presentation.details.mapper

import com.yellowtrack.platform.core.common.solar.SolarCalculator
import com.yellowtrack.platform.core.common.solar.SunEvents
import com.yellowtrack.platform.core.common.solar.SunWindow
import com.yellowtrack.platform.core.common.time.DateFormats
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.feature.sessions.presentation.details.model.LightRow
import com.yellowtrack.platform.feature.sessions.presentation.details.model.SessionDetailsModel
import com.yellowtrack.platform.feature.sessions.presentation.details.model.SessionLight
import com.yellowtrack.platform.feature.sessions.presentation.model.NewSession
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Duration

internal fun Session.toDetailsModel(
    project: Project?,
    client: Client?,
    deviceZone: TimeZone,
): SessionDetailsModel {
    val zone = TimeZone.of(timeZoneId)

    return SessionDetailsModel(
        id = id,
        title = title,
        kind = kind,
        status = status,
        clientName = client?.displayName.orEmpty(),
        projectName = project?.name.orEmpty(),
        dayLabel = DateFormats.dayAndDate(startsAt, zone),
        timeRange = DateFormats.timeRange(startsAt, endsAt, zone),
        durationLabel = duration.onSiteLabel(),
        callTimeLabel = callTime?.let { DateFormats.timeOfDay(it, zone) },
        locationName = locationName,
        locationAddress = locationAddress,
        coordinatesLabel = coordinates?.let { "${it.latitude}, ${it.longitude}" },
        timeZoneNote = timeZoneId.takeIf { zone != deviceZone },
        notes = notes?.lines().orEmpty().filter(String::isNotBlank),
        light = light(zone),
        editable = toEditableForm(zone),
        zoneId = timeZoneId,
    )
}

/**
 * The day's light, worked out for this place and rendered in this session's zone.
 *
 * The golden hours are marked because they are the reason anyone opens the panel; the
 * rest is the context that makes them legible. The sun's bearing at the start is included
 * because it answers what a photographer arriving on site actually wants to know — which
 * way the light is coming from, and therefore what will be backlit.
 */
private fun Session.light(zone: TimeZone): SessionLight? {
    val where = coordinates ?: return null
    val events = SolarCalculator.eventsOn(startsAt.toLocalDateTime(zone).date, where)

    fun at(instant: kotlin.time.Instant) = DateFormats.timeOfDay(instant, zone)

    fun window(window: SunWindow) = "${at(window.start)} – ${at(window.end)}"

    val rows =
        buildList {
            events.morningBlueHour?.let { add(LightRow("Blue hour", window(it))) }
            events.morningGoldenHour?.let { add(LightRow("Golden hour", window(it), isEmphasised = true)) }
            events.sunrise?.let { add(LightRow("Sunrise", at(it))) }
            add(LightRow("Solar noon", "${at(events.solarNoon)} • ${events.noonElevationDegrees.degrees()} up"))
            events.sunset?.let { add(LightRow("Sunset", at(it))) }
            events.eveningGoldenHour?.let { add(LightRow("Golden hour", window(it), isEmphasised = true)) }
            events.eveningBlueHour?.let { add(LightRow("Blue hour", window(it))) }
        }

    val atStart = SolarCalculator.positionAt(startsAt, where)

    return SessionLight(
        rows = rows,
        sunAtStart =
            if (atStart.isUp) {
                "At ${at(startsAt)} the sun is ${atStart.elevationDegrees.degrees()} up, " +
                    "${atStart.azimuthDegrees.compassPoint()}"
            } else {
                "The sun is below the horizon at ${at(startsAt)}"
            },
        note = events.polarNote(),
    )
}

private fun SunEvents.polarNote(): String? =
    when {
        isPolarDay -> "The sun does not set on this date at this latitude."
        isPolarNight -> "The sun does not rise on this date at this latitude."
        else -> null
    }

/**
 * A bearing as a person would say it.
 *
 * "In the west-north-west" is what someone standing in a field can act on; 292° is not,
 * unless they happen to be holding a compass and thinking in degrees.
 */
private fun Double.compassPoint(): String {
    val points =
        listOf(
            "north",
            "north-north-east",
            "north-east",
            "east-north-east",
            "east",
            "east-south-east",
            "south-east",
            "south-south-east",
            "south",
            "south-south-west",
            "south-west",
            "west-south-west",
            "west",
            "west-north-west",
            "north-west",
            "north-north-west",
        )

    val index = (((this % 360.0) + 360.0) % 360.0 / 22.5).roundToInt() % points.size

    return "in the ${points[index]}"
}

private fun Double.degrees(): String = "${roundToInt()}°"

private fun Duration.onSiteLabel(): String {
    val hours = inWholeMinutes / 60
    val minutes = inWholeMinutes % 60

    return when {
        hours == 0L -> "$minutes minutes"
        minutes == 0L -> "$hours hours"
        else -> "${hours}h ${minutes}m"
    }
}

private fun Session.toEditableForm(zone: TimeZone): NewSession {
    val start = startsAt.toLocalDateTime(zone)

    return NewSession(
        projectId = projectId,
        title = title,
        kind = kind,
        status = status,
        date = start.date.toString(),
        startTime = start.time.hourAndMinute(),
        endTime = endsAt.toLocalDateTime(zone).time.hourAndMinute(),
        callTime =
            callTime
                ?.toLocalDateTime(zone)
                ?.time
                ?.hourAndMinute()
                .orEmpty(),
        locationName = locationName.orEmpty(),
        locationAddress = locationAddress.orEmpty(),
        latitude = coordinates?.latitude?.toString().orEmpty(),
        longitude = coordinates?.longitude?.toString().orEmpty(),
        notes = notes.orEmpty(),
    )
}

private fun LocalTime.hourAndMinute(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
