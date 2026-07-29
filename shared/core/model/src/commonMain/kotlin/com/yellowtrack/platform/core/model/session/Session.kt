package com.yellowtrack.platform.core.model.session

import com.yellowtrack.platform.core.common.solar.GeoCoordinates
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A scheduled block of work inside a [com.yellowtrack.platform.core.model.project.Project].
 *
 * @param timeZoneId the IANA zone the session happens in, stored alongside the instants
 *   rather than assumed from the device. A destination wedding booked from home, a client
 *   in another country, and a shoot that straddles a daylight-saving boundary all break
 *   when local time is treated as unambiguous.
 * @param callTime when crew are due on site — earlier than [startsAt], and the time that
 *   actually matters to anyone being paid to show up.
 */
@Serializable
data class Session(
    val id: SessionId,
    override val studioId: StudioId,
    val projectId: ProjectId,
    val title: String,
    val kind: SessionKind,
    val status: SessionStatus,
    val startsAt: Instant,
    val endsAt: Instant,
    val timeZoneId: String,
    val locationName: String? = null,
    val locationAddress: String? = null,
    /**
     * Where the shoot is, when it matters.
     *
     * Null for the great majority of sessions — a studio portrait has no use for the sun's
     * position. Present, it is what lets sunrise, sunset, and the golden hours be computed
     * for this day at this place, offline.
     */
    val coordinates: GeoCoordinates? = null,
    val callTime: Instant? = null,
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    val duration: Duration get() = endsAt - startsAt

    val timeZone: TimeZone get() = TimeZone.of(timeZoneId)

    /** Whether the session falls on [day], read in the zone the session happens in. */
    fun isOn(
        day: LocalDate,
        zone: TimeZone = timeZone,
    ): Boolean = startsAt.toLocalDateTime(zone).date == day
}
