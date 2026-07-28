package com.yellowtrack.platform.core.model.quote

import kotlinx.serialization.Serializable

@Serializable
enum class QuoteStatus {
    Draft,
    Sent,
    Accepted,
    Declined,

    /** Passed its validity date without a decision. Derived, not stored. */
    Expired,
    ;

    val isAwaitingDecision: Boolean get() = this == Sent
}
