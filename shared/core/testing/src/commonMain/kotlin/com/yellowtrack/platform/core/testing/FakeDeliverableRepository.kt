package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.data.DeliverableRepository
import com.yellowtrack.platform.core.model.delivery.Deliverable
import com.yellowtrack.platform.core.model.delivery.DeliverableId
import com.yellowtrack.platform.core.model.delivery.DeliverableStatus
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeDeliverableRepository(
    initial: List<Deliverable> = emptyList(),
) : DeliverableRepository {
    private val state = MutableStateFlow(initial)

    override fun observeDeliverablesForProject(projectId: ProjectId): Flow<List<Deliverable>> =
        state.map { deliverables ->
            deliverables
                .filter { it.projectId == projectId }
                // Matches the query: what is still owed comes first.
                .sortedWith(
                    compareBy({ it.status == DeliverableStatus.Approved }, { it.audit.createdAt }),
                )
        }

    override suspend fun getDeliverable(deliverableId: DeliverableId): Deliverable? =
        state.value.firstOrNull { it.id == deliverableId }

    override suspend fun saveDeliverable(deliverable: Deliverable) {
        state.value = state.value.filterNot { it.id == deliverable.id } + deliverable
    }

    override suspend fun deleteDeliverable(deliverableId: DeliverableId) {
        state.value = state.value.filterNot { it.id == deliverableId }
    }
}
