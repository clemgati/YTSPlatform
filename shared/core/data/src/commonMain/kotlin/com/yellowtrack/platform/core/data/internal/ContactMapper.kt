package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.contact.ContactId
import com.yellowtrack.platform.core.model.contact.ContactMethod
import kotlinx.serialization.json.Json
import com.yellowtrack.platform.core.database.Contact as ContactRow

/** Contact methods live in a JSON column; see the note in Contact.sq. */
internal val contactJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

internal fun encodeContactMethods(methods: List<ContactMethod>): String = contactJson.encodeToString(methods)

internal fun decodeContactMethods(raw: String): List<ContactMethod> =
    runCatching { contactJson.decodeFromString<List<ContactMethod>>(raw) }
        .getOrDefault(emptyList())

internal fun ContactRow.toDomain(): Contact =
    Contact(
        id = ContactId(id),
        studioId = StudioId(studio_id),
        firstName = first_name,
        lastName = last_name,
        company = company,
        jobTitle = job_title,
        emails = decodeContactMethods(emails),
        phones = decodeContactMethods(phones),
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )
