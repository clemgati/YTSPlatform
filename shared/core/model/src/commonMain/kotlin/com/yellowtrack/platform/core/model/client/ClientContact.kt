package com.yellowtrack.platform.core.model.client

import com.yellowtrack.platform.core.model.contact.Contact
import kotlinx.serialization.Serializable

/** A person's involvement in a client account. */
@Serializable
data class ClientContact(
    val contact: Contact,
    val role: ClientContactRole,
)
