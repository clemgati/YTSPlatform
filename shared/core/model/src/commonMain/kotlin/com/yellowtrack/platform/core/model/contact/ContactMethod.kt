package com.yellowtrack.platform.core.model.contact

import kotlinx.serialization.Serializable

/** A way of reaching a person: an email address or a phone number, with a label. */
@Serializable
data class ContactMethod(
    val value: String,
    val label: ContactMethodLabel = ContactMethodLabel.Primary,
)

@Serializable
enum class ContactMethodLabel {
    Primary,
    Mobile,
    Work,
    Home,
    Other,
}
