package com.yellowtrack.platform.core.model.release

import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@JvmInline
value class TalentReleaseId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): TalentReleaseId = TalentReleaseId(uuidV7().toString())
    }
}

/** What is being released, which decides who has to sign. */
@Serializable
enum class ReleaseKind {
    /** An adult signing for themselves. */
    Adult,

    /** A child. A parent or guardian signs, and the release is void without them. */
    Minor,

    /** A building or land, signed by whoever owns it rather than by anyone in the frame. */
    Property,
}

/**
 * Where a release stands.
 *
 * [Refused] is a state rather than an absence on purpose: someone who has said no is not
 * the same as someone who has not been asked, and the difference decides whether their
 * photograph may be used at all.
 */
@Serializable
enum class ReleaseStatus {
    Pending,
    Signed,
    Refused,
    ;

    val isOutstanding: Boolean get() = this == Pending
}

/**
 * Permission from the person in the photograph.
 *
 * This is what makes a usage licence deliverable. A studio can sign a contract granting a
 * client worldwide rights and still have no lawful way to hand the images over, because
 * the people in them never agreed to it — see [com.yellowtrack.platform.core.model.contract.UsageLicense].
 * The licence is the promise; the releases are whether it can be kept.
 *
 * @param guardianName who signed on behalf of a child. A [ReleaseKind.Minor] release
 *   without one is not a release, which is why [isValid] says so rather than trusting the
 *   status alone.
 */
@Serializable
data class TalentRelease(
    val id: TalentReleaseId,
    override val studioId: StudioId,
    val sessionId: SessionId,
    val personName: String,
    val kind: ReleaseKind = ReleaseKind.Adult,
    val status: ReleaseStatus = ReleaseStatus.Pending,
    val signedAt: Instant? = null,
    val guardianName: String? = null,
    val email: String? = null,
    /** Where the signed paper lives. Object-storage key once media hosting exists. */
    val documentReference: String? = null,
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    /**
     * Whether this permission would actually stand up.
     *
     * A minor's release needs the guardian named. A release marked signed with no date
     * cannot say when permission was given, which is the question asked when it is
     * challenged.
     */
    val isValid: Boolean
        get() =
            status == ReleaseStatus.Signed &&
                signedAt != null &&
                (kind != ReleaseKind.Minor || !guardianName.isNullOrBlank())

    /** True where the photograph must not be used: refused, or not yet agreed. */
    val blocksUse: Boolean get() = !isValid
}
