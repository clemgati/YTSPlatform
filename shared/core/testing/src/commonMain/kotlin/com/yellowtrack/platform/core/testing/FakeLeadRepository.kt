package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.data.LeadRepository
import com.yellowtrack.platform.core.model.lead.Lead
import com.yellowtrack.platform.core.model.lead.LeadId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLeadRepository(
    initial: List<Lead> = emptyList(),
) : LeadRepository {
    private val state = MutableStateFlow(initial)

    var failure: Throwable? = null

    override fun observeLeads(): Flow<List<Lead>> =
        state.map { leads -> failure?.let { throw it } ?: leads.sortedByDescending(Lead::receivedAt) }

    override fun observeLead(leadId: LeadId): Flow<Lead?> = state.map { leads -> leads.firstOrNull { it.id == leadId } }

    override fun observeOpenLeads(): Flow<List<Lead>> =
        state.map { leads -> leads.filter { it.status.isOpen }.sortedBy(Lead::receivedAt) }

    override fun observeAwaitingResponse(): Flow<List<Lead>> =
        state.map { leads ->
            failure?.let { throw it }
            leads.filter(Lead::isAwaitingFirstResponse).sortedBy(Lead::receivedAt)
        }

    override suspend fun getLead(leadId: LeadId): Lead? = state.value.firstOrNull { it.id == leadId }

    override suspend fun saveLead(lead: Lead) {
        state.value = state.value.filterNot { it.id == lead.id } + lead
    }

    override suspend fun deleteLead(leadId: LeadId) {
        state.value = state.value.filterNot { it.id == leadId }
    }
}
