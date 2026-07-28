package com.yellowtrack.platform.core.model.contract

import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@JvmInline
value class ContractId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): ContractId = ContractId(uuidV7().toString())
    }
}

@Serializable
enum class ContractStatus {
    Draft,
    Sent,
    Signed,
    Declined,
    Cancelled,
}

/**
 * The agreement behind a booking.
 *
 * The terms modelled here are the ones that decide arguments. [turnaroundDays] is what a
 * client will hold the studio to; [revisionRounds] is what stops a video edit consuming
 * an unbounded amount of unpaid time; [rescheduleTerms] and [weatherClause] are what
 * settle an outdoor shoot called off the morning of.
 *
 * A date is not held until money has changed hands, which is why [retainerAmount] lives
 * on the contract rather than only on an invoice.
 */
@Serializable
data class Contract(
    val id: ContractId,
    override val studioId: StudioId,
    val projectId: ProjectId,
    val title: String,
    val status: ContractStatus,
    val sentAt: Instant? = null,
    val signedAt: Instant? = null,
    val signerName: String? = null,
    val signerEmail: String? = null,
    val retainerAmount: Money? = null,
    /** Retainers are normally non-refundable: they compensate for a date turned away. */
    val isRetainerRefundable: Boolean = false,
    val turnaroundDays: Int? = null,
    val revisionRounds: Int? = null,
    val cancellationTerms: String? = null,
    val rescheduleTerms: String? = null,
    val weatherClause: String? = null,
    val usageLicense: UsageLicense? = null,
    /** Where the signed document lives. Object-storage key once media hosting exists. */
    val documentReference: String? = null,
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    val isSigned: Boolean get() = status == ContractStatus.Signed && signedAt != null

    /** A date is only truly held once this is true. */
    fun isBindingWith(retainerPaid: Boolean): Boolean = isSigned && (retainerAmount == null || retainerPaid)
}
