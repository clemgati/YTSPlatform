package com.yellowtrack.platform.feature.sessions.presentation.details.mapper

import com.yellowtrack.platform.core.common.solar.SolarCalculator
import com.yellowtrack.platform.core.common.solar.SunEvents
import com.yellowtrack.platform.core.common.solar.SunWindow
import com.yellowtrack.platform.core.common.time.DateFormats
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.PackingEntry
import com.yellowtrack.platform.core.model.media.BackupHealth
import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.StorageKind
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.release.ReleaseKind
import com.yellowtrack.platform.core.model.release.ReleaseStatus
import com.yellowtrack.platform.core.model.release.TalentRelease
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.shot.Shot
import com.yellowtrack.platform.core.ui.removal.Removal
import com.yellowtrack.platform.feature.sessions.presentation.details.model.BackupSummary
import com.yellowtrack.platform.feature.sessions.presentation.details.model.CrewItem
import com.yellowtrack.platform.feature.sessions.presentation.details.model.LightRow
import com.yellowtrack.platform.feature.sessions.presentation.details.model.MediaCopyItem
import com.yellowtrack.platform.feature.sessions.presentation.details.model.PackableGear
import com.yellowtrack.platform.feature.sessions.presentation.details.model.PackingItem
import com.yellowtrack.platform.feature.sessions.presentation.details.model.PackingSummary
import com.yellowtrack.platform.feature.sessions.presentation.details.model.ReleaseItem
import com.yellowtrack.platform.feature.sessions.presentation.details.model.ReleaseSummary
import com.yellowtrack.platform.feature.sessions.presentation.details.model.SessionDetailsModel
import com.yellowtrack.platform.feature.sessions.presentation.details.model.SessionLight
import com.yellowtrack.platform.feature.sessions.presentation.details.model.ShotGroup
import com.yellowtrack.platform.feature.sessions.presentation.details.model.ShotItem
import com.yellowtrack.platform.feature.sessions.presentation.model.NewSession
import com.yellowtrack.platform.feature.sessions.presentation.model.label
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Duration

internal fun Session.toDetailsModel(
    project: Project?,
    client: Client?,
    shots: List<Shot>,
    crew: List<CrewMember>,
    releases: List<TalentRelease>,
    mediaCopies: List<MediaCopy>,
    gear: List<GearItem>,
    packing: List<PackingEntry>,
    volumes: List<StorageVolume>,
    deviceZone: TimeZone,
    removal: Removal,
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
        shotGroups = shots.toGroups(),
        shotsRemaining = shots.count { !it.isCaptured },
        releases = releases.toSummary(),
        backup = mediaCopies.toBackupSummary(volumes),
        packing = toPackingSummary(packing, gear),
        crew =
            crew.map { member ->
                CrewItem(
                    id = member.id,
                    name = member.name,
                    role = member.role.label,
                    phone = member.phone,
                    callTimeLabel = member.callTime?.let { DateFormats.timeOfDay(it, zone) },
                )
            },
        removal = removal,
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

/**
 * Shots gathered under their headings, in the order they were put in.
 *
 * Groups keep first-appearance order rather than being sorted alphabetically: the order a
 * photographer wrote them in is the order they intend to work, and "Bride's family" before
 * "Groom's family" is a decision about who is standing where, not a filing preference.
 */
private fun List<Shot>.toGroups(): List<ShotGroup> =
    groupBy { it.groupOrUngrouped }
        .map { (name, shots) ->
            ShotGroup(
                name = name,
                shots =
                    shots.map { shot ->
                        ShotItem(
                            id = shot.id,
                            description = shot.description,
                            people = shot.people?.takeIf(String::isNotBlank),
                            isCaptured = shot.isCaptured,
                        )
                    },
            )
        }

/**
 * The permissions, with what is wrong with each one said plainly.
 *
 * A release marked signed is not necessarily a release that would stand up: a minor's
 * needs the guardian named, and one with no date cannot say when permission was given —
 * which is precisely the question asked when it is challenged. `TalentRelease.isValid`
 * decides; this reports why.
 */
private fun List<TalentRelease>.toSummary(): ReleaseSummary =
    ReleaseSummary(
        releases =
            map { release ->
                ReleaseItem(
                    id = release.id,
                    personName = release.personName,
                    kind = release.kind.label,
                    statusLabel = release.status.name,
                    isSigned = release.isValid,
                    problem = release.problem(),
                )
            },
        outstanding = count { it.status.isOutstanding },
        refused = count { it.status == ReleaseStatus.Refused },
    )

private fun TalentRelease.problem(): String? =
    when {
        status != ReleaseStatus.Signed -> null
        signedAt == null -> "Signed, but with no date — it cannot say when permission was given"
        kind == ReleaseKind.Minor && guardianName.isNullOrBlank() ->
            "A child's release needs the parent or guardian who signed it"
        else -> null
    }

private val ReleaseKind.label: String
    get() =
        when (this) {
            ReleaseKind.Adult -> "Adult"
            ReleaseKind.Minor -> "Minor"
            ReleaseKind.Property -> "Property"
        }

/**
 * The 3-2-1 verdict, with what is missing spelled out.
 *
 * The rule itself lives in `core:model` — it is a fact about the studio's data, not a way
 * of drawing it — and this only renders the answer.
 */
private fun List<MediaCopy>.toBackupSummary(volumes: List<StorageVolume>): BackupSummary {
    val byId = volumes.associateBy { it.id }
    val health = BackupHealth.of(this, byId)

    return BackupSummary(
        copies =
            map { copy ->
                val volume = copy.volumeId?.let { byId[it] }

                MediaCopyItem(
                    id = copy.id,
                    // The register's name wins where there is one: a drive renamed there
                    // should read the same on every shoot it holds.
                    volumeName = volume?.label ?: copy.volumeName,
                    kind = copy.kind.label,
                    isOffsite = volume?.isAwayFromStudio ?: copy.isAwayFromStudio,
                    isVerified = copy.verifiedAt != null,
                    // A copy on a dead drive is still listed — it is the row that explains
                    // why the count above dropped.
                    isUnreachable = volume?.isDependable == false,
                    isCheckable = copy.isCheckable,
                    // Shown only for a copy the application read. A tick by hand carries a
                    // date and no count, and should not borrow the authority of a count.
                    readLabel =
                        copy.verifiedFileCount?.let { count ->
                            "${count.grouped()} ${if (count == 1) "file" else "files"} read"
                        },
                )
            },
        isSatisfied = health.isSatisfied,
        verdict =
            if (health.isSatisfied) {
                "Three copies, ${health.distinctKinds} kinds, ${health.offsiteCopies} away from the studio"
            } else {
                "${health.copies} of ${BackupHealth.REQUIRED_COPIES} copies"
            },
        shortfalls = health.shortfalls,
        unverified = health.unverifiedCopies,
    )
}

private val StorageKind.label: String
    get() =
        when (this) {
            StorageKind.CameraCard -> "Camera card"
            StorageKind.Computer -> "Computer"
            StorageKind.ExternalDrive -> "External drive"
            StorageKind.Nas -> "NAS"
            StorageKind.Cloud -> "Cloud"
            StorageKind.OffsiteDrive -> "Offsite drive"
        }

/**
 * The kit list for this day.
 *
 * An entry whose gear has since been deleted is dropped rather than shown as a blank row:
 * the list is only useful if every line names something that can be looked for.
 */
private fun toPackingSummary(
    packing: List<PackingEntry>,
    gear: List<GearItem>,
): PackingSummary {
    val byId = gear.associateBy { it.id }

    val items =
        packing.mapNotNull { entry ->
            val item = byId[entry.gearItemId] ?: return@mapNotNull null

            PackingItem(
                id = entry.id,
                gearItemId = entry.gearItemId,
                name = item.name,
                categoryLabel = item.category.name,
                isPacked = entry.isPacked,
                isReturned = entry.isReturned,
            )
        }

    val listed = packing.map { it.gearItemId }.toSet()

    return PackingSummary(
        items = items.sortedBy { it.name.lowercase() },
        // Only gear in service is offered. A body at the repair shop cannot be packed, and
        // offering it would put a line on the list that can never be ticked.
        available =
            gear
                .filter { it.status.isAvailable && it.id !in listed }
                .sortedBy { it.name.lowercase() }
                .map { PackableGear(id = it.id, label = it.name) },
        packed = items.count { it.isPacked },
        missing = items.count { it.isPacked && !it.isReturned },
    )
}

/**
 * Groups a count for reading — "2,481" rather than "2481".
 *
 * A file count is a figure a person scans, and four unbroken digits are read a beat slower
 * than three and a comma. Money is grouped for the same reason.
 */
private fun Int.grouped(): String =
    toString()
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()
