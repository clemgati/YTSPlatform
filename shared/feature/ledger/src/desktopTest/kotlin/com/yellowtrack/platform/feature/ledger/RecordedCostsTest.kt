package com.yellowtrack.platform.feature.ledger

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.expense.DistanceUnit
import com.yellowtrack.platform.core.model.expense.Expense
import com.yellowtrack.platform.core.model.expense.ExpenseCategory
import com.yellowtrack.platform.core.model.expense.ExpenseId
import com.yellowtrack.platform.core.model.expense.Mileage
import com.yellowtrack.platform.core.model.expense.MileageId
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.feature.ledger.presentation.mapper.buildExpenseSummary
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The costs themselves, not just what they add up to.
 *
 * The Ledger showed three totals and nothing else, so a studio could record a cost and
 * never see it again: no way to check an amount, notice the same invoice entered twice, or
 * itemise a year at tax time. A number is not an account of where the money went.
 */
class RecordedCostsTest {
    @Test
    fun `costs and journeys appear as themselves`() {
        val summary =
            buildExpenseSummary(
                expenses = listOf(expense("e1", "Insurance renewal", 42_000, LocalDate.parse("2026-03-04"))),
                mileage = listOf(journey("m1", LocalDate.parse("2026-03-06"), purpose = "Venue recce")),
                year = 2026,
                currency = CurrencyCode.GBP,
            )

        assertEquals(
            listOf("Venue recce", "Insurance renewal"),
            summary.items.map { it.description },
            "both kinds of cost belong in one list — a studio checking what it spent does not " +
                "care which table the row came from",
        )
    }

    @Test
    fun `the newest is first, ordered by the date rather than by how it reads`() {
        val summary =
            buildExpenseSummary(
                expenses =
                    listOf(
                        // "Jul" sorts before "Mar" alphabetically, so a list sorted on the
                        // rendered date would put these the wrong way round and look
                        // entirely plausible doing it.
                        expense("e1", "March cost", 10_000, LocalDate.parse("2026-03-01")),
                        expense("e2", "July cost", 20_000, LocalDate.parse("2026-07-01")),
                    ),
                mileage = emptyList(),
                year = 2026,
                currency = CurrencyCode.GBP,
            )

        assertEquals(listOf("July cost", "March cost"), summary.items.map { it.description })
    }

    @Test
    fun `a cost names the booking it came out of`() {
        val project = ProjectId("project-1")

        val summary =
            buildExpenseSummary(
                expenses = listOf(expense("e1", "Second shooter", 30_000, DAY, projectId = project)),
                mileage = emptyList(),
                year = 2026,
                currency = CurrencyCode.GBP,
                projectNames = mapOf(project to "Okafor — Wedding"),
            )

        assertEquals(
            "Okafor — Wedding",
            summary.items.single().allocation,
            "an identifier on the screen tells a studio nothing about which job paid for this",
        )
    }

    @Test
    fun `a cost with no booking is overhead, which is the consequential case`() {
        val summary =
            buildExpenseSummary(
                expenses = listOf(expense("e1", "Insurance renewal", 42_000, DAY)),
                mileage = emptyList(),
                year = 2026,
                currency = CurrencyCode.GBP,
            )

        assertEquals(
            "Overhead",
            summary.items.single().allocation,
            "overhead raises the floor under every job, so which of the two it is has to be " +
                "visible on the row rather than only in the form that created it",
        )
    }

    @Test
    fun `a journey with no purpose describes itself by its distance`() {
        val summary =
            buildExpenseSummary(
                expenses = emptyList(),
                mileage = listOf(journey("m1", DAY, purpose = null)),
                year = 2026,
                currency = CurrencyCode.GBP,
            )

        assertTrue(
            summary.items
                .single()
                .description
                .contains("42"),
            "a blank row tells a studio nothing; the distance is the only honest description " +
                "of a journey nobody wrote a reason for: ${summary.items.single().description}",
        )
    }

    @Test
    fun `costs in another currency stay out of the list, as they do out of the totals`() {
        val summary =
            buildExpenseSummary(
                expenses =
                    listOf(
                        expense("e1", "Sterling cost", 10_000, DAY),
                        expense("e2", "Dollar cost", 10_000, DAY, currency = CurrencyCode.USD),
                    ),
                mileage = emptyList(),
                year = 2026,
                currency = CurrencyCode.GBP,
            )

        assertEquals(
            listOf("Sterling cost"),
            summary.items.map { it.description },
            "a list that disagrees with the totals above it is worse than no list",
        )
    }

    private fun expense(
        id: String,
        description: String,
        minorUnits: Long,
        on: LocalDate,
        projectId: ProjectId? = null,
        currency: CurrencyCode = CurrencyCode.GBP,
    ) = Expense(
        id = ExpenseId(id),
        studioId = STUDIO,
        category = ExpenseCategory.Other,
        description = description,
        amount = Money(minorUnits = minorUnits, currency = currency),
        incurredOn = on,
        projectId = projectId,
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun journey(
        id: String,
        on: LocalDate,
        purpose: String?,
    ) = Mileage(
        id = MileageId(id),
        studioId = STUDIO,
        travelledOn = on,
        distance = 42.0,
        unit = DistanceUnit.Miles,
        ratePerUnit = Money(minorUnits = 45, currency = CurrencyCode.GBP),
        purpose = purpose,
        audit = AuditMetadata.createdAt(NOW),
    )

    private companion object {
        val STUDIO = StudioId("studio-1")
        val NOW: Instant = Instant.fromEpochMilliseconds(1_781_000_000_000)
        val DAY: LocalDate = LocalDate.parse("2026-08-01")
    }
}
