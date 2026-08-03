package com.yellowtrack.platform.feature.clients.presentation.project.mapper

import com.yellowtrack.platform.core.common.money.formatted
import com.yellowtrack.platform.core.common.time.DateFormats
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.delivery.Deliverable
import com.yellowtrack.platform.core.model.post.PostProductionTask
import com.yellowtrack.platform.core.model.post.PostTaskKind
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.feature.clients.presentation.details.model.NewProject
import com.yellowtrack.platform.feature.clients.presentation.project.model.BookingSessionItem
import com.yellowtrack.platform.feature.clients.presentation.project.model.PostProductionSummary
import com.yellowtrack.platform.feature.clients.presentation.project.model.PostTaskItem
import com.yellowtrack.platform.feature.clients.presentation.project.model.ProjectDetailsModel
import com.yellowtrack.platform.feature.clients.presentation.project.model.ProjectRemoval
import kotlinx.datetime.TimeZone
import kotlin.math.abs
import kotlin.time.Instant

internal fun Project.toDetailsModel(
    client: Client?,
    sessions: List<Session>,
    tasks: List<PostProductionTask>,
    deliverables: List<Deliverable>,
    contract: Contract?,
    now: Instant,
    removal: ProjectRemoval,
): ProjectDetailsModel {
    val zone = TimeZone.currentSystemDefault()

    return ProjectDetailsModel(
        id = id,
        name = name,
        clientName = client?.displayName.orEmpty(),
        serviceLine = serviceLine.name,
        status = status,
        valueLabel = contractValue?.formatted(),
        enquiredLabel = enquiredAt?.let { "Enquired ${DateFormats.shortDate(it, zone)}" },
        bookedLabel = bookedAt?.let { "Booked ${DateFormats.shortDate(it, zone)}" },
        notes = notes?.lines().orEmpty().filter(String::isNotBlank),
        sessions =
            sessions
                .sortedBy(Session::startsAt)
                .map { session ->
                    val sessionZone = TimeZone.of(session.timeZoneId)

                    BookingSessionItem(
                        id = session.id,
                        title = session.title,
                        dayLabel = DateFormats.shortDate(session.startsAt, sessionZone),
                        timeRange = DateFormats.timeRange(session.startsAt, session.endsAt, sessionZone),
                        statusLabel = session.status.name,
                    )
                },
        postProduction = tasks.toSummary(),
        delivery = buildDelivery(deliverables, contract, sessions, now),
        removal = removal,
        editable =
            NewProject(
                name = name,
                serviceLine = serviceLine,
                status = status,
                contractValue = contractValue?.toPlainString().orEmpty(),
                notes = notes.orEmpty(),
            ),
    )
}

private fun List<PostProductionTask>.toSummary(): PostProductionSummary =
    PostProductionSummary(
        tasks =
            map { task ->
                val overrun = task.hoursOverEstimate

                PostTaskItem(
                    id = task.id,
                    name = task.name,
                    kind = task.kind.label,
                    status = task.status,
                    estimatedLabel = task.estimatedHours?.hours(),
                    actualLabel = task.actualHours?.hours(),
                    overrunLabel = overrun?.takeIf { abs(it) >= SIGNIFICANT_HOURS }?.overrunLabel(),
                    isOverrun = (overrun ?: 0.0) > 0.0,
                )
            },
        // Estimates from every task, so the figure answers "what did I say this job would
        // take" rather than only what has been finished so far.
        estimatedHours = sumOf { it.estimatedHours ?: 0.0 },
        actualHours = sumOf { it.actualHours ?: 0.0 },
        remaining = count { !it.isDone },
    )

/**
 * Under a quarter of an hour either way is not worth reporting as an overrun.
 *
 * Post-production is estimated in half hours; flagging six minutes would make every task
 * look mismanaged and teach the studio to stop reading the figure.
 */
private const val SIGNIFICANT_HOURS = 0.25

private fun Double.overrunLabel(): String = if (this > 0) "${hours()} over" else "${abs(this).hours()} under"

private fun Double.hours(): String {
    val rounded = (this * 10).toInt() / 10.0

    return if (rounded == rounded.toInt().toDouble()) "${rounded.toInt()}h" else "${rounded}h"
}

private val PostTaskKind.label: String
    get() =
        when (this) {
            PostTaskKind.Cull -> "Cull"
            PostTaskKind.Edit -> "Edit"
            PostTaskKind.Colour -> "Colour"
            PostTaskKind.Retouch -> "Retouch"
            PostTaskKind.AlbumDesign -> "Album design"
            PostTaskKind.Delivery -> "Delivery"
            PostTaskKind.Admin -> "Admin"
            PostTaskKind.Other -> "Other"
        }
