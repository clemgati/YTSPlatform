package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientContact
import com.yellowtrack.platform.core.model.client.ClientContactRole
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.contact.ContactId
import kotlinx.serialization.json.Json
import com.yellowtrack.platform.core.database.Client as ClientRow
import com.yellowtrack.platform.core.database.SelectContactsForStudio as ClientContactRow

private val tagsJson = Json { ignoreUnknownKeys = true }

internal fun encodeTags(tags: List<String>): String = tagsJson.encodeToString(tags)

internal fun decodeTags(raw: String): List<String> =
    runCatching {
        tagsJson.decodeFromString<List<String>>(raw)
    }.getOrDefault(emptyList())

internal fun ClientRow.toDomain(contacts: List<ClientContact>): Client =
    Client(
        id = ClientId(id),
        studioId = StudioId(studio_id),
        accountName = account_name,
        accountType = enumOrDefault(account_type, ClientAccountType.Individual),
        contacts = contacts,
        notes = notes,
        tags = decodeTags(tags),
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )

internal fun ClientContactRow.toDomain(): ClientContact =
    ClientContact(
        contact =
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
            ),
        role = enumOrDefault(role, ClientContactRole.Primary),
    )
