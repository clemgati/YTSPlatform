package com.yellowtrack.platform.core.model.expense

import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@JvmInline
value class ExpenseId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): ExpenseId = ExpenseId(uuidV7().toString())
    }
}

/**
 * Money the studio spent.
 *
 * The nullable [projectId] carries the whole design:
 *
 * - **Set** — a cost of that job. Subtracted from its revenue to give real margin. A
 *   wedding that billed 4,500 and paid a second shooter 400 did not earn 4,500.
 * - **Null** — overhead. Insurance, software, rent, marketing. Not attributable to any
 *   one job, so it feeds the cost of doing business instead, and every job has to carry
 *   a share of it.
 *
 * One table, two questions: *did this job make money* and *what does a year cost to run*.
 */
@Serializable
data class Expense(
    val id: ExpenseId,
    override val studioId: StudioId,
    val category: ExpenseCategory,
    val description: String,
    val amount: Money,
    val incurredOn: LocalDate,
    /** Null means overhead. See the class documentation. */
    val projectId: ProjectId? = null,
    val vendor: String? = null,
    /** Kept explicit: not everything a studio spends is deductible, and guessing is costly. */
    val isTaxDeductible: Boolean = true,
    /** Where the receipt lives. Object-storage key once media hosting exists. */
    val receiptReference: String? = null,
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    val isOverhead: Boolean get() = projectId == null

    val isJobCost: Boolean get() = projectId != null
}
