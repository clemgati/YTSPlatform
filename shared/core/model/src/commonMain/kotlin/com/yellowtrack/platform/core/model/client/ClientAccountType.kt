package com.yellowtrack.platform.core.model.client

import kotlinx.serialization.Serializable

/** The shape of a client account, which determines how it is addressed and billed. */
@Serializable
enum class ClientAccountType {
    /** A single person — most portrait and headshot work. */
    Individual,

    /** Two people of equal standing — the default for weddings. */
    Couple,

    /** An organisation, billed to the organisation rather than to a person. */
    Company,

    /** An agent or agency booking repeatedly on behalf of others. */
    Agency,
}
