package com.yellowtrack.platform.feature.clients.presentation.project.mapper

import com.yellowtrack.platform.core.common.time.DateFormats
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.delivery.Deliverable
import com.yellowtrack.platform.core.model.delivery.DeliverableKind
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.feature.clients.presentation.project.model.DeliverableItem
import com.yellowtrack.platform.feature.clients.presentation.project.model.DeliverySummary
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * What the client is owed, measured against what the contract actually promised.
 *
 * `Contract.turnaroundDays` and `Contract.revisionRounds` have been stored since 0.4.0 and
 * compared against nothing. A studio that has agreed to both and tracks neither finds out
 * it is late when the client says so, and gives away a fourth revision on a two-revision
 * contract because nobody was counting. This is that comparison.
 */
internal fun buildDelivery(
    deliverables: List<Deliverable>,
    contract: Contract?,
    sessions: List<Session>,
    now: Instant,
): DeliverySummary {
    val zone = TimeZone.currentSystemDefault()
    val lastShoot = sessions.maxOfOrNull(Session::endsAt)
    val turnaroundDays = contract?.turnaroundDays
    val allowance = contract?.revisionRounds

    val items =
        deliverables.map { deliverable ->
            // Computed from the shoot and the promise wherever possible, so a studio does
            // not have to work out its own deadline and get it wrong.
            val due = deliverable.dueAt ?: promisedDate(lastShoot, turnaroundDays)
            val overdue = deliverable.isOverdue(now, due)

            DeliverableItem(
                id = deliverable.id,
                name = deliverable.name,
                kind = deliverable.kind.label,
                statusLabel = deliverable.status.name,
                dueLabel =
                    due?.let { date ->
                        val rendered = DateFormats.shortDate(date, zone)

                        when {
                            deliverable.dueAt != null -> "Due $rendered"
                            turnaroundDays != null -> "Due $rendered — $turnaroundDays days after the shoot"
                            else -> "Due $rendered"
                        }
                    },
                isOverdue = overdue,
                revisionsUsed = deliverable.revisionsUsed,
                revisionNote = revisionNote(deliverable.revisionsUsed, allowance),
                isBeyondAllowance = allowance != null && deliverable.revisionsUsed > allowance,
                isSettled = deliverable.isSettled,
            )
        }

    return DeliverySummary(
        deliverables = items,
        outstanding = items.count { !it.isSettled },
        overdue = items.count { it.isOverdue },
        // Stated even when nothing is late, because a studio should be able to see what it
        // agreed to without going back to the contract.
        promiseNote =
            when {
                contract == null -> "No contract on this booking, so nothing was promised in writing."
                turnaroundDays == null && allowance == null ->
                    "The contract sets no turnaround and no revision limit."
                turnaroundDays == null -> "The contract allows ${allowance.rounds}."
                allowance == null -> "The contract promises $turnaroundDays days' turnaround."
                else -> "The contract promises $turnaroundDays days' turnaround and ${allowance.rounds}."
            },
    )
}

/** The date a promise of [turnaroundDays] falls due, counted from the last day shot. */
private fun promisedDate(
    lastShoot: Instant?,
    turnaroundDays: Int?,
): Instant? = if (lastShoot != null && turnaroundDays != null) lastShoot + turnaroundDays.days else null

/**
 * What the revision count means against the contract.
 *
 * The one worth saying out loud is the round that is *about* to exceed the allowance,
 * because that is the one a studio can still charge for.
 */
private fun revisionNote(
    used: Int,
    allowance: Int?,
): String? =
    when {
        allowance == null -> if (used > 0) "$used ${roundWord(used)} so far" else null
        used > allowance -> "$used of ${allowance.rounds} used — beyond what was agreed"
        used == allowance -> "$used of ${allowance.rounds} used — the next one is chargeable"
        else -> "$used of ${allowance.rounds} used"
    }

private val Int?.rounds: String
    get() = if (this == 1) "1 revision round" else "$this revision rounds"

private fun roundWord(count: Int): String = if (count == 1) "round" else "rounds"

private val DeliverableKind.label: String
    get() =
        when (this) {
            DeliverableKind.Gallery -> "Gallery"
            DeliverableKind.Album -> "Album"
            DeliverableKind.Prints -> "Prints"
            DeliverableKind.Video -> "Video"
            DeliverableKind.RawFiles -> "Files"
            DeliverableKind.Other -> "Other"
        }
