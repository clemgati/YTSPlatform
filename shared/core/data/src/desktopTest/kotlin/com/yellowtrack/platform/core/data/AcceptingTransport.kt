package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.data.sync.SyncTransport
import com.yellowtrack.platform.core.model.sync.SyncPullResponse
import com.yellowtrack.platform.core.model.sync.SyncPushOutcome
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import com.yellowtrack.platform.core.model.sync.SyncPushResult

/**
 * A server that takes everything, for tests about something else.
 *
 * The ledger writes through the server under ADR 0012, so a repository test that is really
 * about totals or numbering still needs one to answer. Tests about the writing itself use
 * their own transport and assert on what it received.
 */
internal object AcceptingTransport : SyncTransport {
    override suspend fun pull(
        since: Long,
        limit: Int,
    ) = SyncPullResponse(cursor = since, hasMore = false)

    override suspend fun push(changes: SyncPushRequest): List<SyncPushResult> =
        (
            changes.invoices.map { "invoice" to it.id.value } +
                changes.payments.map { "payment" to it.id.value } +
                changes.quotes.map { "quote" to it.id.value } +
                changes.contracts.map { "contract" to it.id.value } +
                changes.expenses.map { "expense" to it.id.value } +
                changes.mileages.map { "mileage" to it.id.value } +
                changes.projects.map { "project" to it.id.value } +
                changes.sessions.map { "session" to it.id.value } +
                changes.leads.map { "lead" to it.id.value }
        ).map { SyncPushResult(it.first, it.second, SyncPushOutcome.Applied, 1) }
}
