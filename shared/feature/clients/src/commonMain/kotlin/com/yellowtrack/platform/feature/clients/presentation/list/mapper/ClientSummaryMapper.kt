package com.yellowtrack.platform.feature.clients.presentation.list.mapper

import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.feature.clients.presentation.list.model.ClientSummary

internal fun List<Client>.toClientSummaries(): List<ClientSummary> = map(Client::toClientSummary)

private fun Client.toClientSummary(): ClientSummary {
    val metadata = sampleMetadata()

    return ClientSummary(
        id = id,
        displayName = displayName,
        initials = initials,
        sessionCount = metadata.sessionCount,
        lastSession = metadata.lastSession,
        tags = metadata.tags,
    )
}

private val Client.initials: String
    get() =
        buildString {
            firstName.firstOrNull()?.let(::append)
            lastName.firstOrNull()?.let(::append)
        }.uppercase()

private fun Client.sampleMetadata(): SampleClientMetadata =
    when (id.value) {
        "client-1" ->
            SampleClientMetadata(
                sessionCount = 12,
                lastSession = "Jul 21",
                tags = listOf("Portrait", "Branding"),
            )

        "client-2" ->
            SampleClientMetadata(
                sessionCount = 4,
                lastSession = "Jun 11",
                tags = listOf("Headshots"),
            )

        "client-3" ->
            SampleClientMetadata(
                sessionCount = 8,
                lastSession = "Jul 4",
                tags = listOf("Family", "Portrait"),
            )

        "client-4" ->
            SampleClientMetadata(
                sessionCount = 1,
                lastSession = "May 18",
                tags = listOf("Graduation"),
            )

        else ->
            SampleClientMetadata(
                sessionCount = 0,
                lastSession = null,
                tags = emptyList(),
            )
    }

private data class SampleClientMetadata(
    val sessionCount: Int,
    val lastSession: String?,
    val tags: List<String>,
)
