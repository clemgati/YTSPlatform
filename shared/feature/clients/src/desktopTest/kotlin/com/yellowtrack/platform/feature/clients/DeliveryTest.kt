package com.yellowtrack.platform.feature.clients

import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.contract.ContractStatus
import com.yellowtrack.platform.core.model.delivery.Deliverable
import com.yellowtrack.platform.core.model.delivery.DeliverableId
import com.yellowtrack.platform.core.model.delivery.DeliverableStatus
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.feature.clients.presentation.project.mapper.buildDelivery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * Checking what was delivered against what the contract promised.
 *
 * `Contract.turnaroundDays` and `Contract.revisionRounds` have been stored since 0.4.0 and
 * compared against nothing. These are the comparisons.
 */
class DeliveryTest {
    private val now = TestAppClock.DEFAULT_NOW
    private val projectId = ProjectId.new()
    private val studioId = LocalStudioContext.LOCAL_STUDIO_ID

    private fun contract(
        turnaroundDays: Int? = 45,
        revisionRounds: Int? = 2,
    ) = Contract(
        id = ContractId.new(),
        studioId = studioId,
        projectId = projectId,
        title = "Wedding Agreement",
        status = ContractStatus.Signed,
        signedAt = now,
        turnaroundDays = turnaroundDays,
        revisionRounds = revisionRounds,
        audit = AuditMetadata.createdAt(now),
    )

    private fun session(endsAt: kotlin.time.Instant) =
        Session(
            id = SessionId.new(),
            studioId = studioId,
            projectId = projectId,
            title = "Wedding day",
            kind = SessionKind.Shoot,
            status = SessionStatus.Completed,
            startsAt = endsAt - 8.days,
            endsAt = endsAt,
            timeZoneId = "Europe/London",
            audit = AuditMetadata.createdAt(now),
        )

    private fun deliverable(
        revisionsUsed: Int = 0,
        status: DeliverableStatus = DeliverableStatus.InProgress,
        dueAt: kotlin.time.Instant? = null,
    ) = Deliverable(
        id = DeliverableId.new(),
        studioId = studioId,
        projectId = projectId,
        name = "Full gallery",
        status = status,
        dueAt = dueAt,
        revisionsUsed = revisionsUsed,
        audit = AuditMetadata.createdAt(now),
    )

    // --- Turnaround --------------------------------------------------------------------

    @Test
    fun `the due date is worked out from the shoot and the promise`() {
        val shotOn = now - 10.days

        val summary =
            buildDelivery(
                deliverables = listOf(deliverable()),
                contract = contract(turnaroundDays = 45),
                sessions = listOf(session(endsAt = shotOn)),
                now = now,
            )

        val item = summary.deliverables.single()
        val due = assertNotNull(item.dueLabel)
        assertTrue(due.contains("45 days after the shoot"), "was: $due")
        assertFalse(item.isOverdue, "ten days into a forty-five day promise is not late")
    }

    @Test
    fun `a promise already passed is reported as overdue`() {
        val summary =
            buildDelivery(
                deliverables = listOf(deliverable()),
                contract = contract(turnaroundDays = 45),
                sessions = listOf(session(endsAt = now - 60.days)),
                now = now,
            )

        assertTrue(summary.deliverables.single().isOverdue)
        assertEquals(1, summary.overdue)
    }

    @Test
    fun `work already signed off is never late, however long it took`() {
        val summary =
            buildDelivery(
                deliverables = listOf(deliverable(status = DeliverableStatus.Approved)),
                contract = contract(turnaroundDays = 45),
                sessions = listOf(session(endsAt = now - 200.days)),
                now = now,
            )

        assertFalse(
            summary.deliverables.single().isOverdue,
            "the question is what is owed now, not what to feel bad about",
        )
        assertEquals(0, summary.outstanding)
    }

    @Test
    fun `a date set by hand overrides the one the contract implies`() {
        val chosen = now + 5.days

        val summary =
            buildDelivery(
                deliverables = listOf(deliverable(dueAt = chosen)),
                contract = contract(turnaroundDays = 45),
                sessions = listOf(session(endsAt = now - 60.days)),
                now = now,
            )

        val due = assertNotNull(summary.deliverables.single().dueLabel)
        assertFalse(due.contains("after the shoot"), "an overridden date is not the contract's date")
        assertFalse(summary.deliverables.single().isOverdue)
    }

    @Test
    fun `with no shoot day yet there is no date to promise against`() {
        val summary =
            buildDelivery(
                deliverables = listOf(deliverable()),
                contract = contract(turnaroundDays = 45),
                sessions = emptyList(),
                now = now,
            )

        assertNull(summary.deliverables.single().dueLabel)
        assertFalse(summary.deliverables.single().isOverdue, "nothing can be late against no date")
    }

    // --- Revision rounds ---------------------------------------------------------------

    @Test
    fun `the round that exhausts the allowance says the next one is chargeable`() {
        val summary =
            buildDelivery(
                deliverables = listOf(deliverable(revisionsUsed = 2)),
                contract = contract(revisionRounds = 2),
                sessions = listOf(session(endsAt = now)),
                now = now,
            )

        val item = summary.deliverables.single()
        assertEquals("2 of 2 revision rounds used — the next one is chargeable", item.revisionNote)
        assertFalse(item.isBeyondAllowance, "two of two is at the limit, not past it")
    }

    @Test
    fun `going past the allowance is stated plainly`() {
        val summary =
            buildDelivery(
                deliverables = listOf(deliverable(revisionsUsed = 3)),
                contract = contract(revisionRounds = 2),
                sessions = listOf(session(endsAt = now)),
                now = now,
            )

        val item = summary.deliverables.single()
        assertEquals("3 of 2 revision rounds used — beyond what was agreed", item.revisionNote)
        assertTrue(item.isBeyondAllowance)
    }

    @Test
    fun `rounds are still counted where the contract sets no limit`() {
        val summary =
            buildDelivery(
                deliverables = listOf(deliverable(revisionsUsed = 4)),
                contract = contract(revisionRounds = null),
                sessions = listOf(session(endsAt = now)),
                now = now,
            )

        val item = summary.deliverables.single()
        assertEquals("4 rounds so far", item.revisionNote)
        assertFalse(item.isBeyondAllowance, "nothing was agreed, so nothing has been exceeded")
    }

    // --- What was promised -------------------------------------------------------------

    @Test
    fun `the contract's promise is restated so it need not be looked up`() {
        val summary =
            buildDelivery(
                deliverables = emptyList(),
                contract = contract(turnaroundDays = 45, revisionRounds = 2),
                sessions = emptyList(),
                now = now,
            )

        assertEquals(
            "The contract promises 45 days' turnaround and 2 revision rounds.",
            summary.promiseNote,
        )
    }

    @Test
    fun `a booking with no contract says so rather than implying a promise`() {
        val summary =
            buildDelivery(
                deliverables = listOf(deliverable()),
                contract = null,
                sessions = listOf(session(endsAt = now - 200.days)),
                now = now,
            )

        assertEquals(
            "No contract on this booking, so nothing was promised in writing.",
            summary.promiseNote,
        )
        assertNull(summary.deliverables.single().dueLabel, "no promise means no deadline to miss")
        assertFalse(summary.deliverables.single().isOverdue)
    }
}
