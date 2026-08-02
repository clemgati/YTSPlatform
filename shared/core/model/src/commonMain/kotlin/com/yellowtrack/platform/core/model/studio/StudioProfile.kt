package com.yellowtrack.platform.core.model.studio

import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@JvmInline
value class StudioProfileId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): StudioProfileId = StudioProfileId(uuidV7().toString())
    }
}

/**
 * Who the studio is, on paper.
 *
 * Every document the application sends carries this: a call sheet needs a name at the top,
 * and an invoice is not a valid invoice without one. Until now nothing held it, which is
 * why the Settings screen has said since 0.1.0 that studio details "arrive with the
 * account model" — they are needed sooner than that, because documents leave the building
 * before accounts arrive.
 *
 * One row per studio, like `CodbProfile`. It is not the `Studio` tenant record from
 * `docs/DOMAIN_MODEL.md`, which arrives with users and sync in 0.7.0 and will own
 * membership and billing. This is only what goes on the page.
 *
 * @param address free text across several lines. Address formats differ so completely
 *   between countries that a structured version would have to be reassembled into free
 *   text to be printed, and would refuse to hold half the world's addresses on the way.
 * @param taxNumber whatever the jurisdiction calls it — VAT registration, EIN, ABN, GST.
 *   Printed on invoices because in most of them an invoice without it is not deductible
 *   for the client.
 * @param paymentInstructions how to actually pay: bank details, a payment link, a note to
 *   ring the studio. Free text, because a studio taking bank transfer in one country and
 *   a card link in another has no common structure to fill in.
 * @param documentFooter the line at the bottom of everything — payment terms, a late
 *   payment notice, a company registration statement. One place rather than a field per
 *   jurisdiction's requirement.
 * @param currency what the studio charges in. `CurrencyCode` has said since it was written
 *   that this "is a per-studio setting rather than a global constant", and until now it was
 *   a global constant — every screen and every invoice was denominated in dollars whatever
 *   the studio actually charged. This is where that setting lives.
 */
@Serializable
data class StudioProfile(
    val id: StudioProfileId,
    override val studioId: StudioId,
    /** The trading name, as it should appear at the top of a document. */
    val name: String,
    val address: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val taxNumber: String? = null,
    val paymentInstructions: String? = null,
    val documentFooter: String? = null,
    val currency: CurrencyCode = CurrencyCode.USD,
    override val audit: AuditMetadata,
) : StudioScoped {
    /**
     * Whether a document may carry this studio's name.
     *
     * A nameless invoice is not an invoice, and sending one is worse than not sending it:
     * the client cannot tell who it is from, and the studio does not find out until the
     * payment does not arrive.
     */
    val canIssueDocuments: Boolean get() = name.isNotBlank()

    /**
     * What is missing before an invoice would stand up.
     *
     * The name is the only thing that stops a document going out. The rest is reported so
     * a studio can see what a client will notice is absent — an invoice with no way to pay
     * it is a common and expensive omission.
     */
    val documentGaps: List<String>
        get() =
            buildList {
                if (address.isNullOrBlank()) add("no address")
                if (email.isNullOrBlank() && phone.isNullOrBlank()) add("no way to reach you")
                if (taxNumber.isNullOrBlank()) add("no tax registration number")
                if (paymentInstructions.isNullOrBlank()) add("no payment instructions")
            }

    companion object {
        /** The empty profile a studio starts with, so the form has something to open on. */
        fun empty(
            studioId: StudioId,
            audit: AuditMetadata,
        ): StudioProfile =
            StudioProfile(
                // The studio's own id, not a fresh one. There is exactly one profile per
                // studio — both databases carry a unique index saying so — and a generated
                // id makes that one row two: the device that signed up creates one, the
                // device that signs in creates another, and the second push violates the
                // index rather than merging. Deriving it means both devices write the same
                // row and ordinary reconciliation settles it.
                id = StudioProfileId(studioId.value),
                studioId = studioId,
                name = "",
                audit = audit,
            )
    }
}
